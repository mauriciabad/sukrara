// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import CryptoKit
import Foundation
import SukraraCore

/// How Sukrara signs in to a Nightscout site: an access token (Admin tools › Subjects; exchanged for a JWT sent as
/// `Authorization: Bearer`) or the API secret (`api-secret: sha1hex(secret)`).
public enum NightscoutAuth: Sendable, Equatable {
    case token(String)
    case secret(String)

    /// What the one field holds: a token looks like `<subject>-<16 hex>` (Nightscout makes them so); anything else is
    /// the API secret.
    public static func of(_ typed: String) -> NightscoutAuth {
        let s = typed.trimmingCharacters(in: .whitespacesAndNewlines)
        return s.wholeMatch(of: /[a-z0-9][a-z0-9_.-]*-[0-9a-f]{16}/) != nil ? .token(s) : .secret(s)
    }
}

/// Nightscout's REST API as the source reads and writes it (API v1, every server; the token exchange is v2). Pure:
/// requests to build and answers to classify; `NightscoutSource` does the I/O.
public enum NightscoutApi {
    /// GET `/api/v1/entries/sgv.json` with `find[date][$gte]` (ms) and `count`.
    public static func entries(_ root: URL, sinceMs: Int64, count: Int) -> URLRequest {
        request(
            root, "api/v1/entries/sgv.json",
            query: [
                .init(name: "find[date][$gte]", value: String(sinceMs)), .init(name: "count", value: String(count)),
            ])
    }

    /// GET `/api/v1/treatments.json` with `find[created_at][$gte]` (ISO, UTC) and at most 1000 documents.
    public static func treatments(_ root: URL, since: TimeInterval) -> URLRequest {
        request(
            root, "api/v1/treatments.json",
            query: [
                .init(name: "find[created_at][$gte]", value: NightscoutTime.iso(since)),
                .init(name: "count", value: "1000"),
            ])
    }

    /// GET the site's newest treatment of `eventType` ("Sensor Start", "Sensor Change"): the sensor's life.
    public static func newest(_ root: URL, eventType: String) -> URLRequest {
        request(
            root, "api/v1/treatments.json",
            query: [.init(name: "find[eventType]", value: eventType), .init(name: "count", value: "1")])
    }

    /// GET the site's documents with this `identifier` (an upsert deletes moved or retyped copies first).
    public static func treatments(_ root: URL, identifier: String) -> URLRequest {
        request(
            root, "api/v1/treatments.json",
            query: [.init(name: "find[identifier]", value: identifier), .init(name: "count", value: "10")])
    }

    public static func post(_ root: URL, treatment: Data) -> URLRequest {
        var r = request(root, "api/v1/treatments")
        r.httpMethod = "POST"
        r.httpBody = treatment
        r.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        return r
    }

    public static func delete(_ root: URL, documentId: String) -> URLRequest {
        var r = request(root, "api/v1/treatments/\(documentId)")
        r.httpMethod = "DELETE"
        return r
    }

    /// GET `/api/v2/authorization/request/<token>` → `{"token": <JWT>, "exp": <epoch s>}`.
    public static func jwt(_ root: URL, token: String) -> URLRequest {
        request(root, "api/v2/authorization/request/\(token)")
    }

    static func request(_ root: URL, _ path: String, query: [URLQueryItem] = []) -> URLRequest {
        var url = root.appending(path: path)
        if !query.isEmpty { url = url.appending(queryItems: query) }
        var r = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 20)
        r.setValue("application/json", forHTTPHeaderField: "Accept")
        return r
    }

    /// The headers an API secret signs with.
    public static func secretHeaders(_ secret: String) -> [String: String] {
        ["api-secret": Insecure.SHA1.hash(data: Data(secret.utf8)).map { String(format: "%02x", $0) }.joined()]
    }

    /// A status code's outcome (contracts/sources/health.json); nil = it answered.
    public static func outcome(status: Int) -> SourceHealth.Outcome? {
        switch status {
        case 200..<300: nil
        case 401, 403: .refused
        case 404: .notFound
        case 429: .rateLimited
        default: .server  // 5xx, a redirect never followed, anything else that isn't the site's answer
        }
    }

    /// A failed call: no network on this Mac (`offline`), or no answer from the site.
    public static func isOffline(_ error: any Error) -> Bool {
        guard let e = error as? URLError else { return false }
        return [.notConnectedToInternet, .networkConnectionLost, .dataNotAllowed, .internationalRoamingOff].contains(
            e.code)
    }

    /// `Retry-After` in seconds (a number; a date is read as from now).
    public static func retryAfter(_ headers: [String: String], now: Date) -> TimeInterval? {
        guard let v = headers.first(where: { $0.key.lowercased() == "retry-after" })?.value else { return nil }
        if let s = Double(v.trimmingCharacters(in: .whitespaces)) { return max(0, s) }
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "EEE, dd MMM yyyy HH:mm:ss zzz"
        return f.date(from: v).map { max(0, $0.timeIntervalSince(now)) }
    }
}

/// HTTPS calls to a Nightscout site (`URLSessionNightscoutHTTP`; tests use a fake that never touches the network).
public protocol NightscoutHTTP: Sendable {
    /// The status code, body and headers. Redirects are never followed: the 3xx itself comes back.
    func send(_ request: URLRequest) async throws -> (status: Int, body: Data, headers: [String: String])
}

/// `NightscoutHTTP` over an ephemeral URLSession (no cookies, cache or stored credentials) that never follows a
/// redirect: one could carry the token or secret to another host.
public final class URLSessionNightscoutHTTP: NightscoutHTTP {
    private let session: URLSession

    public init() {
        let cfg = URLSessionConfiguration.ephemeral
        cfg.waitsForConnectivity = false
        cfg.httpShouldSetCookies = false
        session = URLSession(configuration: cfg, delegate: NoRedirects(), delegateQueue: nil)
    }

    deinit { session.invalidateAndCancel() }

    public func send(_ request: URLRequest) async throws -> (status: Int, body: Data, headers: [String: String]) {
        let (data, response) = try await session.data(for: request)
        let http = response as? HTTPURLResponse
        var headers: [String: String] = [:]
        for (k, v) in http?.allHeaderFields ?? [:] { headers[String(describing: k)] = String(describing: v) }
        return (http?.statusCode ?? 0, data, headers)
    }
}

/// Refuses every redirect (the 3xx is the answer).
final class NoRedirects: NSObject, URLSessionTaskDelegate, Sendable {
    func urlSession(
        _ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest
    ) async -> URLRequest? { nil }
}
