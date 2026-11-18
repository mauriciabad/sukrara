// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import Foundation
import SukraraCore
import Testing

@testable import SukraraApp

/// Whoever creates a leftover removes it (CLAUDE.md): a snapshot or test run keeps its settings in memory
/// (`InMemorySettingsStore`), never in a UserDefaults suite of its own, whose plist would stay in
/// ~/Library/Preferences (995 `app.mauri.sukrara.snap.<UUID>` and 2,031 `app.mauri.sukrara.tests.<UUID>` piled up).
@MainActor @Suite(.serialized) struct LeftoverSuitesTests {
    static let preferences = FileManager.default.homeDirectoryForCurrentUser.appending(path: "Library/Preferences")

    /// Sukrara's settings files there now.
    static func files() -> Set<String> {
        let all = (try? FileManager.default.contentsOfDirectory(atPath: preferences.path)) ?? []
        return Set(all.filter { $0.localizedCaseInsensitiveContains("sukrara") })
    }

    /// The snapshot scenes and the tests' scratch settings, written as a run writes them, leave no settings file.
    @Test func aRunLeavesNoSettingsSuiteBehind() async throws {
        let before = Self.files()
        #expect(!WindowSnapshots.scenes().isEmpty)
        #expect(!ShellSnapshots.scenes().isEmpty)
        let scratch = scratchSettings()
        scratch.throughFocus = true
        scratchStore().set(true, for: MacSettings.throughFocus)
        // cfprefsd writes a suite's plist shortly after the first value set
        try await Task.sleep(for: .seconds(2))
        #expect(Self.files().subtracting(before).isEmpty, "\(Self.files().subtracting(before).sorted())")
    }

    /// No code but the app's own launch makes a UserDefaults suite: a snapshot or test one would be a leftover.
    @Test func onlyTheAppMakesASuite() throws {
        let macos = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent()
        var found: [String] = []
        for dir in ["Sources", "Tests"] {
            let e = FileManager.default.enumerator(at: macos.appending(path: dir), includingPropertiesForKeys: nil)
            while let url = e?.nextObject() as? URL {
                guard url.pathExtension == "swift", url.lastPathComponent != "LeftoverSuitesTests.swift" else {
                    continue
                }
                let text = try String(contentsOf: url, encoding: .utf8)
                if text.contains("UserDefaults(suiteName") || text.contains("UserDefaults.init(suiteName") {
                    found.append(url.lastPathComponent)
                }
            }
        }
        #expect(found.sorted() == ["AppServices.swift"])
    }
}
