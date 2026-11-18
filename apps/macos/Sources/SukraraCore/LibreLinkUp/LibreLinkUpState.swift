// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// What the LibreLinkUp source keeps on this Mac (`librelinkup/state.json`): whose glucose its readings and treatment
/// log are (another person starts both again: two people's data never mix), their name, the people the account follows,
/// the sensor LibreLinkUp last said, and this Mac's own treatment log (LibreLinkUp brings none; nothing is uploaded).
public struct LibreLinkUpState: Codable, Equatable, Sendable {
    public static let formatVersion = 1

    public var v: Int
    /// The patient id the readings and the log belong to ("" = none yet).
    public var patient: String
    /// Their name as LibreLinkUp says it ("" = not known yet).
    public var name: String
    /// The people the account followed at the last connections answer (a pick that's gone lists them).
    public var people: [LluPerson]
    /// The sensor LibreLinkUp last said (nil = none known).
    public var sensor: LluSensor?
    /// This Mac's treatment log, by id, tombstones included.
    public var entries: [String: Treatment]

    public init(
        patient: String = "", name: String = "", people: [LluPerson] = [], sensor: LluSensor? = nil,
        entries: [String: Treatment] = [:]
    ) {
        v = Self.formatVersion
        self.patient = patient
        self.name = name
        self.people = people
        self.sensor = sensor
        self.entries = entries
    }

    /// The live entries, oldest first.
    public var live: [Treatment] { entries.values.filter { !$0.del }.sorted { ($0.t, $0.id) < ($1.t, $1.id) } }
}
