// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraAlerts
import SukraraCore
import os

/// What of the alerts survives a relaunch: the engine's open episodes and this Mac's mutes (`AlertEngine.Snapshot`),
/// the mirrored wearer's alerts and their mutes (`AlertMirror`). Not the settings: those are the `alerts` setting.
struct AlertState: Codable, Equatable {
    static let formatVersion = 1
    var v = AlertState.formatVersion
    var engine: AlertEngine.Snapshot
    var mirror: AlertMirror
    /// The wearer's newest firing per group (Unmute / Remind as usual shows it again after a relaunch).
    var mirrored: [AlertGroup: AlertFiring] = [:]
}

/// Loads and saves `AlertState` (tests use an in-memory one).
protocol AlertStateStoring {
    /// nil when there is none, it is unreadable or of another version (the caller starts fresh: alerts on).
    func load() -> AlertState?
    func save(_ state: AlertState)
}

/// `~/Library/Application Support/app.mauri.sukrara/alert-state.json`, written atomically.
struct AlertStateFile: AlertStateStoring {
    let url: URL
    private static let log = Logger(subsystem: SukraraIDs.appBundleID, category: "alerts")

    init(paths: SukraraPaths) {
        url = paths.appSupport.appending(path: "alert-state.json", directoryHint: .notDirectory)
    }

    /// An unreadable file is copied aside (`alert-state.json.corrupt-<unix s>`) before the next save replaces it, so
    /// what went wrong can still be looked at; one of another version is simply replaced.
    func load(now: Date) -> AlertState? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .secondsSince1970
        guard let state = try? decoder.decode(AlertState.self, from: data) else {
            let issue = StoreFiles.quarantine(url, now: now)
            Self.log.error(
                "alert state unreadable, kept as \(issue.quarantined?.lastPathComponent ?? "nothing", privacy: .public): starting with alerts on and nothing muted"
            )
            return nil
        }
        guard state.v == AlertState.formatVersion, state.engine.v == AlertEngine.Snapshot.formatVersion else {
            Self.log.error("alert state of another version: starting with alerts on and nothing muted")
            return nil
        }
        return state
    }

    func load() -> AlertState? { load(now: Date()) }

    func save(_ state: AlertState) {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        encoder.dateEncodingStrategy = .secondsSince1970
        do {
            try FileManager.default.createDirectory(
                at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
            try encoder.encode(state).write(to: url, options: .atomic)
        } catch {
            Self.log.error("alert state not saved: \(error.localizedDescription, privacy: .public)")
        }
    }
}
