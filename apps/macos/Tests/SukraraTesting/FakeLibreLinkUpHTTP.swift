// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraShare
import Synchronization

/// LibreLinkUp in memory (the global host and every region's): `answer` gives each request's status, body and
/// headers (nil = no answer: `unanswered` is thrown). Every request is logged. Never touches the network.
public final class FakeLibreLinkUpHTTP: LibreLinkUpHTTP {
    public struct Request: Sendable, Equatable {
        public var method: String
        public var url: URL
        public var headers: [String: String]
        public var body: Data?

        /// The path ("/llu/connections").
        public var path: String { url.path() }
    }

    public typealias Answer = (status: Int, body: Data, headers: [String: String])

    private let answer: @Sendable (Request) -> Answer?
    private let unanswered: any Error
    private let log = Mutex<[Request]>([])

    public init(
        unanswered: any Error = URLError(.cannotConnectToHost), answer: @escaping @Sendable (Request) -> Answer?
    ) {
        self.answer = answer
        self.unanswered = unanswered
    }

    /// Every request so far, in order.
    public var requests: [Request] { log.withLock { $0 } }

    public func send(_ request: URLRequest) async throws -> (status: Int, body: Data, headers: [String: String]) {
        let r = Request(
            method: request.httpMethod ?? "GET", url: request.url!, headers: request.allHTTPHeaderFields ?? [:],
            body: request.httpBody)
        log.withLock { $0.append(r) }
        guard let a = answer(r) else { throw unanswered }
        return a
    }

    /// A JSON answer.
    public static func json(_ text: String, status: Int = 200) -> Answer { (status, Data(text.utf8), [:]) }
}
