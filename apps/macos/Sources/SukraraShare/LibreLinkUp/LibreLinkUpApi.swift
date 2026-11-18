// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore

/// LibreLinkUp's API as the source calls it (contracts/sources/librelinkup; Android `LibreLinkUp`): the login and the
/// authenticated GETs, with the headers every call sends. Pure: requests to build; `LibreLinkUpSource` does the I/O.
/// https only, to `api.libreview.io` or a region's `api-<region>.libreview.io` (a region is 2–3 lower-case letters).
public enum LibreLinkUpApi {
    /// POST `/llu/auth/login` at `region`'s host with the email and password.
    public static func login(email: String, password: String, region: String?, version: String) -> URLRequest {
        var r = request(LibreLinkUp.loginPath, region: region, version: version)
        r.httpMethod = "POST"
        r.httpBody = try? JSONSerialization.data(withJSONObject: [
            "email": email.trimmingCharacters(in: .whitespacesAndNewlines), "password": password,
        ])
        r.setValue("application/json", forHTTPHeaderField: "Content-Type")
        return r
    }

    /// GET `path` at `region`'s host with the token and the account's id (`Account-Id`: its SHA-256).
    public static func get(_ path: String, region: String?, token: String, accountId: String, version: String)
        -> URLRequest
    {
        var r = request(path, region: region, version: version)
        r.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        r.setValue(LibreLinkUp.sha256(accountId), forHTTPHeaderField: "Account-Id")
        return r
    }

    static func request(_ path: String, region: String?, version: String) -> URLRequest {
        var c = URLComponents()
        c.scheme = "https"
        c.host = LibreLinkUp.host(region: region)
        c.path = path
        var r = URLRequest(url: c.url!, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 30)
        r.setValue(LibreLinkUp.product, forHTTPHeaderField: "product")
        r.setValue(version, forHTTPHeaderField: "version")
        r.setValue("application/json", forHTTPHeaderField: "Accept")
        r.setValue("no-cache", forHTTPHeaderField: "cache-control")
        return r
    }
}

/// HTTPS calls to LibreLinkUp (`URLSessionLibreLinkUpHTTP`; tests use a fake that never touches the network).
public protocol LibreLinkUpHTTP: Sendable {
    /// The status code, body and headers. Redirects are never followed: the 3xx itself comes back.
    func send(_ request: URLRequest) async throws -> (status: Int, body: Data, headers: [String: String])
}

/// `LibreLinkUpHTTP` over an ephemeral URLSession (no cookies, cache or stored credentials) that never follows a
/// redirect: one could carry the password or token to another host.
public final class URLSessionLibreLinkUpHTTP: LibreLinkUpHTTP {
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
