// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// The Nightscout address a person types or pastes (setup's Connect, Settings › Source) as the site Sukrara reads
/// (contracts/sources/nightscout/sites.json): its root (scheme + host [+ port] + path, lowercase scheme and host, no
/// default port, trailing "/" or API path), a `?token=` taken out of a pasted address, and what is wrong with it.
/// Plain http only to this Mac itself (CLAUDE.md "Cleartext"). Android `NsSite`.
public struct NightscoutSite: Equatable, Sendable {
    public enum Problem: String, Sendable {
        /// Not a web address (or one with a user name).
        case url
        /// Plain http to anything but this Mac: the token would travel unprotected.
        case https
    }

    public let root: String
    /// The access token a pasted `…?token=` carried, nil without one.
    public let token: String?
    public let problem: Problem?

    /// The root as a URL to call, nil when there's a problem.
    public var url: URL? { problem == nil ? URL(string: root) : nil }

    /// "ns.example.org" (or with a port and path) for the words.
    public var host: String {
        guard let c = URLComponents(string: root), let h = c.host else { return root }
        return h + (c.port.map { ":\($0)" } ?? "") + (c.path == "/" ? "" : c.path)
    }

    public static func parse(_ input: String) -> NightscoutSite {
        let typed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        func bad() -> NightscoutSite { NightscoutSite(root: typed, token: nil, problem: .url) }
        guard !typed.isEmpty else { return bad() }
        let withScheme = typed.contains("://") ? typed : "https://" + typed
        guard let c = URLComponents(string: withScheme), let scheme = c.scheme?.lowercased(),
            scheme == "https" || scheme == "http", let rawHost = c.host, !rawHost.isEmpty, c.user == nil,
            c.password == nil
        else { return bad() }
        let host = rawHost.lowercased()
        let port = c.port.flatMap { (scheme == "https" && $0 == 443) || (scheme == "http" && $0 == 80) ? nil : $0 }
        var path = c.path
        while path.hasSuffix("/") { path.removeLast() }
        for api in ["/api/v1", "/api/v3"] where path.lowercased().hasSuffix(api) {
            path.removeLast(api.count)
        }
        while path.hasSuffix("/") { path.removeLast() }
        let root = "\(scheme)://\(host)\(port.map { ":\($0)" } ?? "")\(path)"
        let token = c.queryItems?.first { $0.name == "token" }?.value.flatMap { $0.isEmpty ? nil : $0 }
        let problem: Problem? = scheme == "http" && !isLoopback(host) ? .https : nil
        return NightscoutSite(root: root, token: token, problem: problem)
    }

    /// Two addresses are the same site when their roots are equal (docs/44 "Data" rule 2).
    public static func same(_ a: String, _ b: String) -> Bool { parse(a).root == parse(b).root }

    /// This Mac itself: the only host plain http may reach.
    static func isLoopback(_ host: String) -> Bool {
        host == "localhost" || host == "::1" || host == "[::1]" || host.hasPrefix("127.")
    }
}
