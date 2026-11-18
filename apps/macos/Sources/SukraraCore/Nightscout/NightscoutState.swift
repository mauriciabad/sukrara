// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// This Mac's treatment log with a Nightscout source (`nightscout/state.json`): the site it belongs to, this
/// install's name there, every entry (tombstones included) and the ledger of what came from the site and what went
/// up. Another site starts a new log (two people's treatments never mix).
public struct NightscoutState: Codable, Equatable, Sendable {
    public static let formatVersion = 1

    public var v: Int
    /// The site root this log belongs to (`NightscoutSite.root`).
    public var site: String
    /// This install's name on the site (`Sukrara-<6 hex>`), made once.
    public var device: String
    /// Every entry by id, tombstones included.
    public var entries: [String: Treatment]
    public var ledger: NightscoutTreatments.Ledger
    /// The sensor the site's newest Sensor Start / Sensor Change started (nil = none known).
    public var sensor: NightscoutSensorLife.Life?

    public init(
        site: String, device: String = NightscoutTreatments.newDevice(), entries: [String: Treatment] = [:],
        ledger: NightscoutTreatments.Ledger = .init()
    ) {
        v = Self.formatVersion
        self.site = site
        self.device = device
        self.entries = entries
        self.ledger = ledger
    }

    /// The live entries, oldest first.
    public var live: [Treatment] { entries.values.filter { !$0.del }.sorted { ($0.t, $0.id) < ($1.t, $1.id) } }

    /// Entries written here that haven't reached the site as they are now (a person or site change loses them).
    public var unsent: Int {
        entries.values.filter { t in
            !ledger.pulled.contains(t.id) && (t.del ? ledger.pushed[t.id] != nil : (ledger.pushed[t.id] ?? -1) < t.u)
        }.count
    }
}
