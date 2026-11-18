// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import Testing

/// Leaving a link with *Keep it*: the follow's readings and treatments move aside as the wearer's kept history, listed
/// newest first and deleted on request (Settings › Source's *Also on this Mac*).
@Suite struct KeptHistoryTests {
    @Test func keepingMovesTheFilesAsideAndListsThem() throws {
        let root = FileManager.default.temporaryDirectory.appending(path: "kept-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: root) }
        let kept = root.appending(path: "kept")
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        try Data("t_ms,mgdl,rate\n".utf8).write(to: root.appending(path: "readings.csv"))
        let id = KeptHistory.id(.sukrara, "AB:cd/12")
        #expect(id == "sukrara-ab_cd_12")
        KeptHistory.keep(
            from: root, files: ["readings.csv", "treatments.json"],
            as: KeptHistory.Info(id: id, name: "Ana", keptAt: 100), in: kept)
        #expect(!FileManager.default.fileExists(atPath: root.appending(path: "readings.csv").path))
        #expect(
            FileManager.default.fileExists(
                atPath: KeptHistory.folder(id, in: kept).appending(path: "readings.csv").path))
        KeptHistory.keep(
            from: root, files: [],
            as: KeptHistory.Info(id: KeptHistory.id(.libreLinkUp, "leo"), name: nil, keptAt: 200),
            in: kept)
        let list = KeptHistory.list(in: kept)
        #expect(list.map(\.id) == ["libreLinkUp-leo", id] && list.last?.name == "Ana" && list.last?.kind == .sukrara)
        KeptHistory.delete(id, in: kept)
        #expect(KeptHistory.list(in: kept).map(\.id) == ["libreLinkUp-leo"])
    }
}
