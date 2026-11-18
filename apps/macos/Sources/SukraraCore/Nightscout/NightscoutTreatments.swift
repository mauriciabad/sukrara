// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import CryptoKit
import Foundation

/// Treatments both ways with a Nightscout source (contracts/sources/nightscout/treatments.json; docs/44 "Data",
/// Treatments): one sync over a window just read in full → what to write here, delete here, push there, remove there.
/// The site's treatments arrive read-only under ids derived from theirs (UUID v5), so reading them again never copies
/// them; this Mac's own go up under their own id (`identifier`) and are recognised on the way back. Android
/// `NsTreatmentSync`.
public enum NightscoutTreatments {
    /// One entry a site document stands for.
    public struct Entry: Equatable, Sendable {
        public var id: String
        public var k: Treatment.Kind
        public var a: Double
        /// Epoch seconds.
        public var t: Int64
        public var n: String

        public init(id: String, k: Treatment.Kind, a: Double, t: Int64, n: String) {
            self.id = id
            self.k = k
            self.a = a
            self.t = t
            self.n = n
        }

        func same(as t: Treatment) -> Bool { k == t.k && abs(a - t.a) < 1e-6 && self.t == t.t && n == t.n }
    }

    /// What this Mac took from the site and sent there (persisted with the log).
    public struct Ledger: Codable, Equatable, Sendable {
        /// The ids this Mac took from the site (read-only here, never pushed).
        public var pulled: Set<String>
        /// Own id → the `u` that went up.
        public var pushed: [String: Int64]

        public init(pulled: Set<String> = [], pushed: [String: Int64] = [:]) {
            self.pulled = pulled
            self.pushed = pushed
        }
    }

    public struct Plan: Equatable, Sendable {
        /// Entries to write here (read-only when pulled), by time then id.
        public var upsert: [Entry] = []
        /// Ids to delete here.
        public var delete: [String] = []
        /// Own entries to create or replace on the site.
        public var push: [String] = []
        /// Own entries to delete there.
        public var remove: [String] = []

        public init(upsert: [Entry] = [], delete: [String] = [], push: [String] = [], remove: [String] = []) {
            self.upsert = upsert
            self.delete = delete
            self.push = push
            self.remove = remove
        }

        public var isEmpty: Bool { upsert.isEmpty && delete.isEmpty && push.isEmpty && remove.isEmpty }
    }

    /// `site`: the documents read (created_at in `window`, epoch s); `local`: this Mac's entries (tombstones included).
    public static func plan(
        site: [[String: Any]], local: [Treatment], ledger: Ledger, device: String, window: ClosedRange<Int64>
    ) -> Plan {
        let inWindow = local.filter { window.contains($0.t) }
        let byId = Dictionary(inWindow.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        let ownIds = Set(local.filter { !ledger.pulled.contains($0.id) }.map(\.id)).union(ledger.pushed.keys)
        var plan = Plan()
        var foreign: [String: Entry] = [:]
        var ownSeen: [String: Entry] = [:]
        for doc in site where (doc["isValid"] as? Bool) != false {
            let identifier = doc["identifier"] as? String
            if let identifier, ownIds.contains(identifier) {
                if var e = entries(of: doc).first {
                    e.id = identifier
                    ownSeen[identifier] = e
                }
                continue
            }
            // this install's, its identifier lost: never pulled
            if doc["app"] as? String == "Sukrara", doc["device"] as? String == device { continue }
            for e in entries(of: doc) where foreign[e.id] == nil { foreign[e.id] = e }
        }
        for e in foreign.values {
            if let have = byId[e.id] ?? local.first(where: { $0.id == e.id }), !have.del, e.same(as: have) { continue }
            plan.upsert.append(e)
        }
        for id in ledger.pulled where foreign[id] == nil {
            if let have = byId[id], !have.del { plan.delete.append(id) }
        }
        for t in inWindow where !ledger.pulled.contains(t.id) {
            let sent = ledger.pushed[t.id]
            if t.del {
                if sent != nil { plan.remove.append(t.id) }
                continue
            }
            guard let sent, t.u <= sent else {
                plan.push.append(t.id)
                continue
            }
            // unchanged here since it went up: the site's version holds
            guard let there = ownSeen[t.id] else {
                plan.delete.append(t.id)
                continue
            }
            if !there.same(as: t) { plan.upsert.append(there) }
        }
        plan.upsert.sort { ($0.t, $0.id) < ($1.t, $1.id) }
        plan.delete.sort()
        plan.push.sort()
        plan.remove.sort()
        return plan
    }

    /// The entries one site document stands for: its insulin (> 0; long-acting when `insulinType` says long), its
    /// carbs (> 0) and, with neither, a Note / Announcement's text; a Note with insulinType long-acting and "Long-acting
    /// insulin N U[ · note]" (how Sukrara writes one) is that dose. Ids from `identifier`, else `_id`. The loop's own
    /// insulin, other events and a document without a time stand for none.
    public static func entries(of doc: [String: Any]) -> [Entry] {
        guard let key = (doc["identifier"] as? String) ?? (doc["_id"] as? String), let t = time(doc) else { return [] }
        let event = doc["eventType"] as? String ?? ""
        if doc["isSMB"] as? Bool == true || doc["type"] as? String == "SMB" || skipped.contains(event) { return [] }
        let notes = (doc["notes"] as? String) ?? ""
        let insulinType = ((doc["insulinType"] as? String) ?? "").lowercased()
        var out: [Entry] = []
        if let u = NightscoutEntries.number(doc["insulin"]), u > 0 {
            let k: Treatment.Kind = insulinType.contains("long") ? .long : .rapid
            out.append(Entry(id: id(key, "insulin"), k: k, a: u, t: t, n: notes))
        }
        if let g = NightscoutEntries.number(doc["carbs"]), g > 0 {
            out.append(Entry(id: id(key, "carbs"), k: .carbs, a: g, t: t, n: notes))
        }
        guard out.isEmpty, event == "Note" || event == "Announcement", !notes.isEmpty else { return out }
        if insulinType.contains("long"), let (units, rest) = longActing(notes) {
            return [Entry(id: id(key, "insulin"), k: .long, a: units, t: t, n: rest)]
        }
        return [Entry(id: id(key, "note"), k: .note, a: 0, t: t, n: notes)]
    }

    /// Events that are not a treatment the person gave: the loop's own insulin and the site's other records (sensor
    /// life from the site is step 2's).
    static let skipped: Set<String> = [
        "SMB", "Temp Basal", "Temporary Basal", "Temporary Target", "Profile Switch", "Site Change", "Sensor Start",
        "Sensor Change", "Sensor Stop", "Insulin Change", "Pump Battery Change", "OpenAPS Offline", "Exercise",
    ]

    /// "Long-acting insulin 12 U · morning" → (12, "morning").
    static func longActing(_ notes: String) -> (Double, String)? {
        guard let m = notes.wholeMatch(of: /Long-acting insulin ([0-9]+(?:[.,][0-9]+)?) U(?: · (.*))?/),
            let units = Double(m.output.1.replacing(",", with: "."))
        else { return nil }
        return (units, m.output.2.map(String.init) ?? "")
    }

    /// A treatment's time (epoch s): `created_at`, else `date` / `mills` (ms).
    static func time(_ doc: [String: Any]) -> Int64? {
        if let s = doc["created_at"] as? String, let t = NightscoutTime.parse(s) { return Int64(t.rounded()) }
        if let ms = NightscoutEntries.number(doc["date"]) ?? NightscoutEntries.number(doc["mills"]) {
            return Int64((ms / 1000).rounded())
        }
        return nil
    }

    /// The Sukrara id of a site treatment's entry: UUID v5 (Nightscout's namespace "NightscoutRocks!") of
    /// `sukrara-ns_<key>_<field>`, field = insulin | carbs | note.
    public static func id(_ key: String, _ field: String) -> String {
        uuidV5(namespace: Data("NightscoutRocks!".utf8), name: "sukrara-ns_\(key)_\(field)")
    }

    /// RFC 4122 v5 UUID, lowercase (as node's `uuid.v5`).
    static func uuidV5(namespace: Data, name: String) -> String {
        var b = Array(Insecure.SHA1.hash(data: namespace + Data(name.utf8)).prefix(16))
        b[6] = (b[6] & 0x0F) | 0x50
        b[8] = (b[8] & 0x3F) | 0x80
        let hex = b.map { String(format: "%02x", $0) }.joined()
        let i = hex.startIndex
        func part(_ from: Int, _ to: Int) -> Substring {
            hex[hex.index(i, offsetBy: from)..<hex.index(i, offsetBy: to)]
        }
        return [part(0, 8), part(8, 12), part(12, 16), part(16, 20), part(20, 32)].joined(separator: "-")
    }

    /// The site document for one of this Mac's entries (as Android's upload writes one, `NsDocs.treatment`):
    /// identifier = its id, Correction Bolus / Carb Correction / Note, a long-acting dose as a Note with insulinType
    /// long-acting and "Long-acting insulin N U[ · note]".
    public static func document(_ t: Treatment, device: String, zone: TimeZone = .current) -> [String: Any] {
        let at = TimeInterval(t.t)
        var d: [String: Any] = [
            "identifier": t.id, "created_at": NightscoutTime.iso(at), "date": t.t * 1000,
            "utcOffset": zone.secondsFromGMT(for: Date(timeIntervalSince1970: at)) / 60, "enteredBy": app, "app": app,
            "device": device,
        ]
        switch t.k {
        case .rapid:
            d["eventType"] = "Correction Bolus"
            d["insulin"] = t.a
            d["insulinType"] = "rapid-acting"
        case .long:
            d["eventType"] = "Note"
            d["insulinType"] = "long-acting"
        case .carbs:
            d["eventType"] = "Carb Correction"
            d["carbs"] = t.a
        case .note:
            d["eventType"] = "Note"
        }
        let long = t.k == .long ? "Long-acting insulin \(units(t.a)) U" : nil
        let notes = [long, t.n.isEmpty ? nil : t.n].compactMap { $0 }
        if !notes.isEmpty { d["notes"] = notes.joined(separator: " · ") }
        return d
    }

    public static let app = "Sukrara"

    /// "12", "4.5".
    static func units(_ a: Double) -> String {
        a == a.rounded() ? String(Int64(a)) : String(format: "%.1f", locale: Locale(identifier: "en_US_POSIX"), a)
    }

    /// This install's name on a site (`Sukrara-<6 hex>`), made once and kept with the log.
    public static func newDevice() -> String {
        "Sukrara-" + (0..<3).map { _ in String(format: "%02x", UInt8.random(in: 0...255)) }.joined()
    }
}
