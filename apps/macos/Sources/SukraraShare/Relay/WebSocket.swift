// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

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

/// URLSession WebSockets (one ephemeral session per factory: no cookies, no cache).
public final class URLSessionWebSocketFactory: WebSocketFactory {
    private let session = URLSession(configuration: .ephemeral)

    public init() {}

    deinit { session.invalidateAndCancel() }

    public func connect(to url: URL) -> any WebSocket {
        let task = session.webSocketTask(with: url)
        task.resume()  // sends queue until the socket is open
        return URLSessionWebSocket(task: task)
    }
}

struct URLSessionWebSocket: WebSocket {
    let task: URLSessionWebSocketTask

    func send(_ text: String) async throws { try await task.send(.string(text)) }

    func receive() async throws -> String {
        while true {
            if case .string(let text) = try await task.receive() { return text }
        }
    }

    func ping() async throws { try await Self.pong { task.sendPing(pongReceiveHandler: $0) } }

    /// Waits for the answer to the ping `send` sends with the handler it's given. URLSession can call that handler
    /// twice (the pong, then an error when the connection closes or the task is cancelled) or never (a half-open
    /// connection): the first call answers, later ones are dropped, and cancelling the caller ends the wait.
    static func pong(sending send: (@escaping @Sendable ((any Error)?) -> Void) -> Void) async throws {
        let once = ResumeOnce<Void, any Error>()
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                once.start(continuation)
                send { error in once.resume(with: error.map { .failure($0) } ?? .success(())) }
            }
        } onCancel: {
            once.resume(with: .failure(CancellationError()))
        }
    }

    func cancel() { task.cancel(with: .goingAway, reason: nil) }
}
