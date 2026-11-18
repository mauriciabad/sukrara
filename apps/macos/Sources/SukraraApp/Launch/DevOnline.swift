// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
#if DEBUG
    import Foundation
    import Synchronization
    import SukraraCore
    import SukraraShare

    /// Debug builds only (docs/44 Appendix G finding 17): a dev run (`SUKRARA_DEV=1`, its own data) can read the online
    /// sources from servers on this Mac, so their journeys are walked on the app and not only in unit tests:
    /// `SUKRARA_DEV_LIBRELINKUP=http://127.0.0.1:8766` sends LibreLinkUp's calls to `tools/librelinkup-probe/fake.py`,
    /// `SUKRARA_DEV_NIGHTSCOUT=1` runs the Nightscout source for a site on this Mac (loopback http, which every run
    /// accepts). Off unless asked; never in a release build, a snapshot or a normal run. Secrets of such a run stay in
    /// memory (`DevSecrets`): it never writes the login Keychain.
    enum DevOnline {
        /// The fake LibreLinkUp's address, when this run asks for one (loopback http only).
        static func libreLinkUp(_ env: [String: String]) -> URL? {
            guard let url = env["SUKRARA_DEV_LIBRELINKUP"].flatMap(URL.init(string:)),
                url.scheme == "http", ["127.0.0.1", "localhost"].contains(url.host() ?? "")
            else { return nil }
            return url
        }

        /// The Nightscout source runs (for a site on this Mac).
        static func nightscout(_ env: [String: String]) -> Bool { env["SUKRARA_DEV_NIGHTSCOUT"] == "1" }

        /// Whether `plan` may run them: a debug dev run with its own data (not a snapshot), asked for.
        static func allowed(_ plan: LaunchPlan, _ env: [String: String]) -> Bool {
            guard plan.data.isIsolated, plan.mode == .normal || plan.mode == .fixture else { return false }
            return libreLinkUp(env) != nil || nightscout(env)
        }
    }

    /// LibreLinkUp's calls (https://api[-region].libreview.io) sent to the fake on this Mac's loopback instead.
    struct LoopbackLibreLinkUpHTTP: LibreLinkUpHTTP {
        let base: URL
        let inner = URLSessionLibreLinkUpHTTP()

        func send(_ request: URLRequest) async throws -> (status: Int, body: Data, headers: [String: String]) {
            var r = request
            if let url = r.url, var c = URLComponents(url: url, resolvingAgainstBaseURL: false) {
                c.scheme = base.scheme
                c.host = base.host()
                c.port = base.port
                r.url = c.url
            }
            return try await inner.send(r)
        }
    }

    /// The secrets of a dev run with online sources: in memory only (never the login Keychain).
    final class DevSecrets: SecretStoring {
        private let items = Mutex<[String: Data]>([:])

        func read(account: String) -> Data? { items.withLock { $0[account] } }
        func write(_ data: Data, account: String) { items.withLock { $0[account] = data } }
        func delete(account: String) { _ = items.withLock { $0.removeValue(forKey: account) } }
    }
#endif
