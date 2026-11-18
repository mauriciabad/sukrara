// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// The histories this Mac holds of identities it doesn't read now (one person, one link; docs/44 Appendix G findings 5
/// and 16), under `SukraraPaths.kept`: one folder per identity (`id`: the followed wearer's key, a LibreLinkUp patient,
/// a Nightscout site), its readings and treatments as their source stored them and `kept.json` (who, from which source,
/// when, what continues it). A follow left with *Keep it* lands here; a LibreLinkUp person or a Nightscout site the
/// source moves away from is shelved here and taken back when it is read again (`shelve` / `unshelve`). The next
/// identity read after a leave with *Keep it* continues the kept follow: its readings show before that identity's
/// (`continuing`, merged by `LinkRules.continuing`). Settings › Source lists the others (*Also saved on this Mac*,
/// *Delete*).
public enum KeptHistory {
    /// What a kept history says of itself.
    public struct Info: Codable, Equatable, Sendable {
        /// The folder's name: the source and its identity (`id(_:_:)`).
        public var id: String
        /// The person's name as the source gave it (nil = none).
        public var name: String?
        /// When it was kept (epoch s).
        public var keptAt: Int
        /// Left with *Keep it*: the next identity read continues it (until one is read).
        public var awaitingNext: Bool
        /// The identity (a kept-history `id`) that continues it: its readings show before that identity's.
        public var continuedBy: String?

        public init(
            id: String, name: String?, keptAt: Int = Int(Date().timeIntervalSince1970), awaitingNext: Bool = false,
            continuedBy: String? = nil
        ) {
            self.id = id
            self.name = name
            self.keptAt = keptAt
            self.awaitingNext = awaitingNext
            self.continuedBy = continuedBy
        }

        /// The source it came from (`SourceKind`), from the id.
        public var kind: SourceKind? {
            id.split(separator: "-", maxSplits: 1).first.flatMap { SourceKind(rawValue: String($0)) }
        }
    }

    static let infoFile = "kept.json"
    /// The source's own state file (LibreLinkUp's, Nightscout's) and LibreLinkUp's graph folder, as kept.
    public static let stateFile = "state.json"
    public static let graphFolder = "graph"

    /// A folder name for `key` (the wearer's identity, a patient id, a site) read through `kind`.
    public static func id(_ kind: SourceKind, _ key: String) -> String {
        let safe = key.lowercased().map { $0.isLetter || $0.isNumber ? $0 : "_" }
        return "\(kind.rawValue)-\(String(safe.prefix(64)))"
    }

    /// Moves `files` from `directory` into `kept/<info.id>/` (replacing an earlier copy of the same identity) and
    /// writes its `kept.json`. A file that isn't there is skipped.
    public static func keep(from directory: URL, files: [String], as info: Info, in kept: URL) {
        let fm = FileManager.default
        let folder = folder(info.id, in: kept)
        try? fm.removeItem(at: folder)
        try? fm.createDirectory(at: folder, withIntermediateDirectories: true)
        for name in files {
            let from = directory.appending(path: name, directoryHint: .notDirectory)
            guard fm.fileExists(atPath: from.path) else { continue }
            try? fm.moveItem(at: from, to: folder.appending(path: name, directoryHint: .notDirectory))
        }
        write(info, in: kept)
    }

    /// The kept histories, newest first.
    public static func list(in kept: URL) -> [Info] {
        let folders =
            (try? FileManager.default.contentsOfDirectory(at: kept, includingPropertiesForKeys: nil)) ?? []
        return folders.compactMap { info($0.lastPathComponent, in: kept) }
            .sorted { ($0.keptAt, $0.id) > ($1.keptAt, $1.id) }
    }

    /// What the kept history `id` says of itself (nil = none kept).
    public static func info(_ id: String, in kept: URL) -> Info? {
        (try? Data(contentsOf: folder(id, in: kept).appending(path: infoFile, directoryHint: .notDirectory)))
            .flatMap { try? JSONDecoder().decode(Info.self, from: $0) }
    }

    /// Writes `info` into its folder (made when missing).
    public static func write(_ info: Info, in kept: URL) {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        guard let data = try? encoder.encode(info) else { return }
        try? StoreFiles.write(
            data, to: folder(info.id, in: kept).appending(path: infoFile, directoryHint: .notDirectory))
    }

    /// The folder of a kept history.
    public static func folder(_ id: String, in kept: URL) -> URL {
        kept.appending(path: id, directoryHint: .isDirectory)
    }

    /// Deletes a kept history (Settings › Source's *Delete*).
    public static func delete(_ id: String, in kept: URL) {
        try? FileManager.default.removeItem(at: folder(id, in: kept))
    }

    // MARK: - continuing

    /// A leave with *Keep it*: `id` waits for the next identity read to continue it (only the last leave waits).
    public static func awaitNext(_ id: String, in kept: URL) {
        for var other in list(in: kept) where other.awaitingNext || other.id == id {
            other.awaitingNext = other.id == id
            write(other, in: kept)
        }
    }

    /// `current` is read: the kept history waiting for the next identity is continued by it (not by itself).
    public static func continueAwaiting(by current: String, in kept: URL) {
        for var info in list(in: kept) where info.awaitingNext {
            info.awaitingNext = false
            if info.id != current { info.continuedBy = current }
            write(info, in: kept)
        }
    }

    /// The kept histories `current` continues, directly or through another kept one, oldest continuation first (the
    /// order `LinkRules.continuing` merges them in).
    public static func continuing(_ current: String, in kept: URL) -> [Info] {
        let all = list(in: kept)
        var chain: [Info] = []
        var next = [current]
        var seen: Set<String> = [current]
        while let id = next.popLast() {
            for info in all where info.continuedBy == id && seen.insert(info.id).inserted {
                chain.insert(info, at: 0)
                next.append(info.id)
            }
        }
        return chain
    }

    /// The kept histories not shown with `current` (nil = nothing read now): Settings › Source lists them.
    public static func notShown(with current: String?, in kept: URL) -> [Info] {
        let shown = Set(current.map { continuing($0, in: kept).map(\.id) + [$0] } ?? [])
        return list(in: kept).filter { !shown.contains($0.id) }
    }

    /// The readings of the kept history `id`, oldest first: `readings.csv`, with LibreLinkUp's graph points
    /// (`graph/readings.csv`) in its gaps.
    public static func readings(_ id: String, in kept: URL) async -> [Reading] {
        let folder = folder(id, in: kept)
        let live = (try? await ReadingsFileStore(directory: folder).load(since: .distantPast)) ?? []
        let graph = folder.appending(path: graphFolder, directoryHint: .isDirectory)
        guard FileManager.default.fileExists(atPath: graph.path) else { return live }
        let points = (try? await ReadingsFileStore(directory: graph).load(since: .distantPast)) ?? []
        return LibreLinkUp.merge(live: live, graph: points)
    }

    /// The live treatments of a kept history, in the format its source keeps them (the follow's `treatments.json`,
    /// LibreLinkUp's or Nightscout's `state.json`), oldest first.
    public static func treatments(_ info: Info, in kept: URL) async -> [Treatment] {
        let folder = folder(info.id, in: kept)
        let state = try? Data(contentsOf: folder.appending(path: stateFile, directoryHint: .notDirectory))
        switch info.kind {
        case .sukrara: return await TreatmentFileStore(directory: folder).load().log.live
        case .libreLinkUp:
            return state.flatMap { try? JSONDecoder().decode(LibreLinkUpState.self, from: $0) }?.live ?? []
        case .nightscout: return state.flatMap { try? JSONDecoder().decode(NightscoutState.self, from: $0) }?.live ?? []
        case .lanJuggluco, nil: return []
        }
    }

    // MARK: - shelving

    /// A source's reading store and its folder inside the identity's kept folder ("" = the folder itself).
    public typealias Shelf = (folder: String, store: any ReadingsStoring)

    /// A source moves away from an identity it holds (another LibreLinkUp person, another Nightscout site): the
    /// stores' readings and the log (`log`: its state file's data) go into `kept/<info.id>/`, added to an earlier copy,
    /// and the stores are emptied. Nothing is kept when there is nothing to keep (no reading, no log).
    public static func shelve(_ info: Info, readings: [Shelf], log: Data?, in kept: URL) async {
        let target = folder(info.id, in: kept)
        var any = log != nil
        for (sub, store) in readings {
            let all = (try? await store.load(since: .distantPast)) ?? []
            guard !all.isEmpty else { continue }
            any = true
            let dir = sub.isEmpty ? target : target.appending(path: sub, directoryHint: .isDirectory)
            try? await ReadingsFileStore(directory: dir).merge(all)
        }
        for (_, store) in readings { try? await store.removeAll() }
        guard any else { return }
        if let log { try? StoreFiles.write(log, to: target.appending(path: stateFile, directoryHint: .notDirectory)) }
        var shelved = Self.info(info.id, in: kept) ?? info
        shelved.name = info.name ?? shelved.name
        shelved.keptAt = info.keptAt
        write(shelved, in: kept)
    }

    /// A source reads a kept identity again: its readings go back into the stores (as `shelve` laid them out), the
    /// folder goes, and its log's data is returned (nil = nothing kept, empty = no log).
    public static func unshelve(_ id: String, readings: [Shelf], in kept: URL) async -> Data? {
        let source = folder(id, in: kept)
        guard FileManager.default.fileExists(atPath: source.path) else { return nil }
        for (sub, store) in readings {
            let dir = sub.isEmpty ? source : source.appending(path: sub, directoryHint: .isDirectory)
            let all = (try? await ReadingsFileStore(directory: dir).load(since: .distantPast)) ?? []
            if !all.isEmpty { try? await store.merge(all) }
        }
        let log = try? Data(contentsOf: source.appending(path: stateFile, directoryHint: .notDirectory))
        delete(id, in: kept)
        return log ?? Data()
    }
}
