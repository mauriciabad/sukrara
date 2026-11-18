// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// The histories this Mac kept of people it no longer reads (one person, one link: leaving a link with *Keep it*),
/// under `SukraraPaths.kept`: one folder per identity (`id`), its readings and treatments as their source stored them
/// and `kept.json` (who, from which source, when). Settings › Source lists them (*Also on this Mac*, *Delete*).
public enum KeptHistory {
    /// What a kept history says of itself.
    public struct Info: Codable, Equatable, Sendable {
        /// The folder's name: the source and its identity (`id(_:_:)`).
        public var id: String
        /// The person's name as the source gave it (nil = none).
        public var name: String?
        /// When it was kept (epoch s).
        public var keptAt: Int

        public init(id: String, name: String?, keptAt: Int = Int(Date().timeIntervalSince1970)) {
            self.id = id
            self.name = name
            self.keptAt = keptAt
        }

        /// The source it came from (`SourceKind`), from the id.
        public var kind: SourceKind? {
            id.split(separator: "-", maxSplits: 1).first.flatMap { SourceKind(rawValue: String($0)) }
        }
    }

    static let infoFile = "kept.json"

    /// A folder name for `key` (the wearer's identity, a patient id, a site) read through `kind`.
    public static func id(_ kind: SourceKind, _ key: String) -> String {
        let safe = key.lowercased().map { $0.isLetter || $0.isNumber ? $0 : "_" }
        return "\(kind.rawValue)-\(String(safe.prefix(64)))"
    }

    /// Moves `files` from `directory` into `kept/<info.id>/` (replacing an earlier copy of the same identity) and
    /// writes its `kept.json`. A file that isn't there is skipped.
    public static func keep(from directory: URL, files: [String], as info: Info, in kept: URL) {
        let fm = FileManager.default
        let folder = kept.appending(path: info.id, directoryHint: .isDirectory)
        try? fm.removeItem(at: folder)
        try? fm.createDirectory(at: folder, withIntermediateDirectories: true)
        for name in files {
            let from = directory.appending(path: name, directoryHint: .notDirectory)
            guard fm.fileExists(atPath: from.path) else { continue }
            try? fm.moveItem(at: from, to: folder.appending(path: name, directoryHint: .notDirectory))
        }
        if let data = try? JSONEncoder().encode(info) {
            try? data.write(to: folder.appending(path: infoFile, directoryHint: .notDirectory), options: .atomic)
        }
    }

    /// The kept histories, newest first.
    public static func list(in kept: URL) -> [Info] {
        let folders =
            (try? FileManager.default.contentsOfDirectory(at: kept, includingPropertiesForKeys: nil)) ?? []
        return folders.compactMap { folder in
            (try? Data(contentsOf: folder.appending(path: infoFile, directoryHint: .notDirectory)))
                .flatMap { try? JSONDecoder().decode(Info.self, from: $0) }
        }
        .sorted { ($0.keptAt, $0.id) > ($1.keptAt, $1.id) }
    }

    /// The folder of a kept history.
    public static func folder(_ id: String, in kept: URL) -> URL {
        kept.appending(path: id, directoryHint: .isDirectory)
    }

    /// Deletes a kept history (Settings › Source's *Delete*).
    public static func delete(_ id: String, in kept: URL) {
        try? FileManager.default.removeItem(at: folder(id, in: kept))
    }
}
