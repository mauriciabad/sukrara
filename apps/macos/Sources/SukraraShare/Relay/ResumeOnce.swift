// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Synchronization

/// Resumes a continuation with the first result only, for callbacks that may come more than once or race each other
/// (URLSession's pong handler, a ping racing its deadline, a cancel): a checked continuation resumed twice traps and
/// quits the app. A result that comes before `start` (a cancel before the wait began) is kept and resumes it at once.
final class ResumeOnce<Value: Sendable, Failure: Error>: Sendable {
    private struct State {
        var continuation: CheckedContinuation<Value, Failure>?
        var result: Result<Value, Failure>?
    }

    private let state = Mutex(State())

    /// Call from the `with…Continuation` body.
    func start(_ continuation: CheckedContinuation<Value, Failure>) {
        let early = state.withLock { state -> Result<Value, Failure>? in
            if state.result == nil { state.continuation = continuation }
            return state.result
        }
        if let early { continuation.resume(with: early) }
    }

    /// The first call answers; later ones are dropped.
    func resume(with result: Result<Value, Failure>) {
        let waiting = state.withLock { state -> CheckedContinuation<Value, Failure>? in
            guard state.result == nil else { return nil }
            state.result = result
            defer { state.continuation = nil }
            return state.continuation
        }
        waiting?.resume(with: result)
    }
}
