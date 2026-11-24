// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Network
import Synchronization
import Testing

@testable import SukraraShare

/// The production WebSocket factory against a relay on this Mac's loopback (127.0.0.1, plain ws://): never a real
/// relay. #263: a relay that answers and then closes, met every 2 s for a day and a half, left 62,134 sockets in
/// memory (4.7 GB): every `URLSessionWebSocketTask` stayed alive after it ended, with its connection and TLS chain.
@Suite(.serialized)
struct WebSocketLoopbackTests {
    let factory = RelayWebSocketFactory()

    @Test func noSocketOutlivesItsConnection() async throws {
        let relay = try await LoopbackRelay.start(.answerThenClose)
        defer { relay.stop() }
        var connections: [Weak] = []
        for _ in 0..<20 {
            let socket = factory.connect(to: relay.url)
            connections.append(Weak(try #require(Self.connection(of: socket))))
            #expect(try await socket.receive() == "[\"NOTICE\",\"bye\"]")
            await #expect(throws: (any Error).self) { try await socket.receive() }
            socket.cancel()
        }
        let ended = connections
        #expect(await eventually { ended.allSatisfy { $0.object == nil } }, "sockets still in memory after they ended")
    }

    @Test func aSendBeforeTheSocketOpensGoesOutOnceItDoes() async throws {
        let relay = try await LoopbackRelay.start(.echo)
        defer { relay.stop() }
        let socket = factory.connect(to: relay.url)
        defer { socket.cancel() }
        try await socket.send("[\"REQ\",\"live\",{}]")
        #expect(try await socket.receive() == "[\"REQ\",\"live\",{}]")
    }

    @Test func aPingIsAnswered() async throws {
        let relay = try await LoopbackRelay.start(.echo)
        defer { relay.stop() }
        let socket = factory.connect(to: relay.url)
        defer { socket.cancel() }
        try await socket.send("hi")
        _ = try await socket.receive()
        try await socket.ping()
    }

    @Test func cancelEndsAWaitingReceive() async throws {
        let relay = try await LoopbackRelay.start(.echo)
        defer { relay.stop() }
        let socket = factory.connect(to: relay.url)
        let receive = Task { try await socket.receive() }
        try await Task.sleep(for: .milliseconds(200))
        socket.cancel()
        await #expect(throws: (any Error).self) { try await receive.value }
    }

    @Test func aRelayThatRefusesTheConnectionFailsAtOnce() async throws {
        let relay = try await LoopbackRelay.start(.echo)
        let url = relay.url
        relay.stop()
        try await Task.sleep(for: .milliseconds(100))
        let socket = factory.connect(to: url)
        defer { socket.cancel() }
        let failed = Mutex(false)
        let receive = Task {
            _ = try? await socket.receive()
            failed.withLock { $0 = true }
        }
        #expect(await eventually { failed.withLock { $0 } }, "a refused connection must end, not wait")
        receive.cancel()
    }

    /// The object that holds the socket's connection.
    static func connection(of socket: any SukraraShare.WebSocket) -> AnyObject? {
        (socket as? RelayWebSocket)?.connection
    }
}

private final class Weak: @unchecked Sendable {
    weak var object: AnyObject?
    init(_ object: AnyObject) { self.object = object }
}

/// A WebSocket relay on 127.0.0.1 (Network.framework's server side).
final class LoopbackRelay: Sendable {
    enum Behaviour: Sendable {
        /// Sends one NOTICE and closes the connection (what nostr.mom did to the Mac every 2 s).
        case answerThenClose
        /// Sends back every text message.
        case echo
    }

    let url: URL
    private let listener: NWListener
    private let queue: DispatchQueue

    private init(url: URL, listener: NWListener, queue: DispatchQueue) {
        self.url = url
        self.listener = listener
        self.queue = queue
    }

    static func start(_ behaviour: Behaviour) async throws -> LoopbackRelay {
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: .any)
        let options = NWProtocolWebSocket.Options()
        options.autoReplyPing = true
        parameters.defaultProtocolStack.applicationProtocols.insert(options, at: 0)
        let listener = try NWListener(using: parameters)
        let queue = DispatchQueue(label: "loopback-relay")
        listener.newConnectionHandler = { connection in
            connection.start(queue: queue)
            switch behaviour {
            case .answerThenClose:
                send("[\"NOTICE\",\"bye\"]", on: connection) { connection.cancel() }
            case .echo:
                echo(connection)
            }
        }
        let port = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<UInt16, any Error>) in
            let once = ResumeOnce<UInt16, any Error>()
            once.start(continuation)
            listener.stateUpdateHandler = { state in
                switch state {
                case .ready: once.resume(with: .success(listener.port?.rawValue ?? 0))
                case .failed(let error): once.resume(with: .failure(error))
                default: break
                }
            }
            listener.start(queue: queue)
        }
        return LoopbackRelay(url: URL(string: "ws://127.0.0.1:\(port)")!, listener: listener, queue: queue)
    }

    func stop() { listener.cancel() }

    private static func send(_ text: String, on connection: NWConnection, then: @escaping @Sendable () -> Void) {
        let context = NWConnection.ContentContext(
            identifier: "text", metadata: [NWProtocolWebSocket.Metadata(opcode: .text)])
        connection.send(
            content: Data(text.utf8), contentContext: context, isComplete: true,
            completion: .contentProcessed { _ in then() })
    }

    private static func echo(_ connection: NWConnection) {
        connection.receiveMessage { content, context, _, error in
            guard error == nil, let content,
                let metadata = context?.protocolMetadata(definition: NWProtocolWebSocket.definition)
                    as? NWProtocolWebSocket.Metadata, metadata.opcode == .text
            else { return connection.cancel() }
            send(String(decoding: content, as: UTF8.self), on: connection) { echo(connection) }
        }
    }
}
