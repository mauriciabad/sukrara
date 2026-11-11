// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Synchronization
import Testing

@testable import SukraraShare

/// `URLSessionWebSocket.ping()`'s wait for the pong, over a scripted `sendPing` (no socket is opened). URLSession can
/// call the pong handler twice (the pong, then an error when the connection closes or the task is cancelled) or never
/// (a half-open connection): the first call answers, later ones are dropped, and the caller can always stop waiting.
/// 2026-11-12: a checked continuation resumed by the second call trapped and quit the Mac follower overnight.
struct WebSocketPingTests {
    @Test func aPongThenTheCloseAnswersOnce() async throws {
        try await URLSessionWebSocket.pong { handler in
            handler(nil)
            handler(URLError(.cancelled))
        }
    }

    @Test func aFailureThenAPongThrowsTheFailure() async {
        await #expect(throws: URLError(.networkConnectionLost)) {
            try await URLSessionWebSocket.pong { handler in
                handler(URLError(.networkConnectionLost))
                handler(nil)
            }
        }
    }

    /// As URLSession does it: later, on its own queues, the close racing the pong.
    @Test func callsRacingOnOtherThreadsAnswerOnce() async {
        for _ in 0..<200 {
            _ = try? await URLSessionWebSocket.pong { handler in
                DispatchQueue.global().async {
                    DispatchQueue.concurrentPerform(iterations: 3) { handler($0 == 0 ? nil : URLError(.cancelled)) }
                }
            }
        }
    }

    /// A pong that never comes: cancelling the caller ends the wait, and the handler URLSession calls when the socket
    /// is cancelled afterwards (twice, even) changes nothing.
    @Test func aPongThatNeverComesEndsWhenTheCallerIsCancelled() async {
        let handler = Box<(@Sendable ((any Error)?) -> Void)?>(nil)
        let outcome = Box<String?>(nil)
        let wait = Task {
            outcome.set(await Self.outcome { try await URLSessionWebSocket.pong { handler.set($0) } })
        }
        #expect(await eventually { handler.get() != nil })
        wait.cancel()
        #expect(await eventually { outcome.get() == "cancelled" })
        handler.get()?(URLError(.cancelled))
        handler.get()?(nil)
        #expect(outcome.get() == "cancelled")
    }

    @Test func aCallerCancelledBeforeThePingDoesNotWait() async {
        let outcome = Box<String?>(nil)
        let wait = Task {
            while !Task.isCancelled { await Task.yield() }
            outcome.set(await Self.outcome { try await URLSessionWebSocket.pong { _ in } })
        }
        wait.cancel()
        #expect(await eventually { outcome.get() == "cancelled" })
    }

    private static func outcome(_ ping: () async throws -> Void) async -> String {
        do {
            try await ping()
            return "pong"
        } catch is CancellationError {
            return "cancelled"
        } catch {
            return "\(error)"
        }
    }
}

/// A value the test and the ping's tasks share.
private final class Box<Value: Sendable>: Sendable {
    private let value: Mutex<Value>

    init(_ value: Value) { self.value = Mutex(value) }

    func get() -> Value { value.withLock { $0 } }
    func set(_ new: Value) { value.withLock { $0 = new } }
}
