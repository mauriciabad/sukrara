// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Network

/// One WebSocket connection carrying text frames (NIP-01 messages).
public protocol WebSocket: Sendable {
    func send(_ text: String) async throws
    /// The next text frame; throws when the connection ends.
    func receive() async throws -> String
    /// Returns when the relay answers; throws when it can't, or when the caller's task is cancelled.
    func ping() async throws
    func cancel()
}

/// Makes connections. `RelayPool` takes one so tests inject fakes and never open a socket to a real relay.
public protocol WebSocketFactory: Sendable {
    func connect(to url: URL) -> any WebSocket
}

/// Relay WebSockets over Network.framework: one `NWConnection` per socket (TLS for `wss://`; no cookies, no cache).
/// Not URLSession: every `URLSessionWebSocketTask` stays in memory after it ends, with its connection, its TLS
/// certificates and its HTTP upgrade (a reference cycle inside CFNetwork, whatever the session, its proxy settings or
/// how the task ends: #263, 62,134 of them after a day and a half). A cancelled `NWConnection` is freed.
public final class RelayWebSocketFactory: WebSocketFactory {
    public init() {}

    public func connect(to url: URL) -> any WebSocket { RelayWebSocket(url: url) }
}

final class RelayWebSocket: WebSocket {
    /// The longest relay message taken (URLSession's default; our events are a few KB): a longer one ends the socket.
    static let maximumMessageSize = 1 << 20

    let connection: NWConnection
    private let queue = DispatchQueue(label: "app.mauri.sukrara.relay-socket")

    /// The connection ended (a close frame, or nothing more to read).
    struct Closed: Error {}

    /// Starts connecting at once; sends wait until the socket is open. A relay that can't be reached (refused, no
    /// network: Network.framework's `waiting`) or fails ends the socket: `RelayPool` decides when to try again.
    init(url: URL) {
        let options = NWProtocolWebSocket.Options()
        options.autoReplyPing = true
        options.maximumMessageSize = Self.maximumMessageSize
        let parameters: NWParameters = url.scheme == "wss" ? .tls : .tcp
        parameters.defaultProtocolStack.applicationProtocols.insert(options, at: 0)
        let connection = NWConnection(to: .url(url), using: parameters)
        connection.stateUpdateHandler = { [weak connection] state in
            switch state {
            case .waiting, .failed: connection?.cancel()
            default: break
            }
        }
        self.connection = connection
        connection.start(queue: queue)
    }

    func send(_ text: String) async throws {
        try await Self.wait { done in
            self.connection.send(
                content: Data(text.utf8), contentContext: Self.context(.text), isComplete: true,
                completion: .contentProcessed { done($0) })
        }
    }

    func receive() async throws -> String {
        while true {
            let (data, opcode) = try await message()
            switch opcode {
            case .text: return String(decoding: data, as: UTF8.self)
            case .close: throw Closed()
            default: continue  // binary, ping, pong
            }
        }
    }

    private func message() async throws -> (Data, NWProtocolWebSocket.Opcode) {
        let once = ResumeOnce<(Data, NWProtocolWebSocket.Opcode), any Error>()
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                once.start(continuation)
                connection.receiveMessage { content, context, _, error in
                    if let error { return once.resume(with: .failure(error)) }
                    guard
                        let metadata = context?.protocolMetadata(definition: NWProtocolWebSocket.definition)
                            as? NWProtocolWebSocket.Metadata
                    else { return once.resume(with: .failure(Closed())) }
                    once.resume(with: .success((content ?? Data(), metadata.opcode)))
                }
            }
        } onCancel: {
            once.resume(with: .failure(CancellationError()))
        }
    }

    func ping() async throws {
        try await Self.pong { answered in
            let metadata = NWProtocolWebSocket.Metadata(opcode: .ping)
            metadata.setPongHandler(queue) { answered($0) }
            let context = NWConnection.ContentContext(identifier: "ping", metadata: [metadata])
            connection.send(
                content: nil, contentContext: context, isComplete: true,
                completion: .contentProcessed { error in if let error { answered(error) } })
        }
    }

    /// Waits for the answer to the ping `send` sends with the handler it's given. The handler can be called twice (the
    /// pong, then an error when the connection ends) or never (a half-open connection): the first call answers, later
    /// ones are dropped, and cancelling the caller ends the wait.
    static func pong(sending send: (@escaping @Sendable ((any Error)?) -> Void) -> Void) async throws {
        try await wait(send)
    }

    func cancel() { connection.cancel() }

    /// Runs `start` with a completion that answers once (nil: done), and stops waiting when the caller is cancelled.
    private static func wait(_ start: (@escaping @Sendable ((any Error)?) -> Void) -> Void) async throws {
        let once = ResumeOnce<Void, any Error>()
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                once.start(continuation)
                start { error in once.resume(with: error.map { .failure($0) } ?? .success(())) }
            }
        } onCancel: {
            once.resume(with: .failure(CancellationError()))
        }
    }

    private static func context(_ opcode: NWProtocolWebSocket.Opcode) -> NWConnection.ContentContext {
        NWConnection.ContentContext(identifier: "text", metadata: [NWProtocolWebSocket.Metadata(opcode: opcode)])
    }
}
