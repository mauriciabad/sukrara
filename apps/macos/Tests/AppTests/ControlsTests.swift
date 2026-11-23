// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Testing

@testable import SukraraApp

/// One pattern per kind of input and notice across the Mac app (#181): every text field is a `TextInput` (no
/// placeholder), every number a `NumberField` (field, stepper, unit), every dialog's closing row `DialogButtons`
/// (regular size), every warning a `Notice`.
@Suite struct ControlsTests {
    /// The app's sources, but the shared controls themselves.
    private static func sources() throws -> [(String, String)] {
        let files =
            FileManager.default.enumerator(at: LocalizationTests.sources, includingPropertiesForKeys: nil)?
            .compactMap { $0 as? URL }.filter {
                $0.pathExtension == "swift" && $0.lastPathComponent != "Controls.swift"
            }
            ?? []
        return try files.map { ($0.lastPathComponent, try String(contentsOf: $0, encoding: .utf8)) }
    }

    /// Lines of `sources` matching `pattern`, as "File.swift:12".
    private static func uses(_ pattern: Regex<Substring>) throws -> [String] {
        try sources().flatMap { name, text in
            text.split(separator: "\n", omittingEmptySubsequences: false).enumerated()
                .filter {
                    $0.element.contains(pattern) && !$0.element.trimmingCharacters(in: .whitespaces).hasPrefix("//")
                }
                .map { "\(name):\($0.offset + 1)" }
        }
    }

    /// The owner, 2026-11-14: "on the Mac remove all placeholders". A raw field shows its title as a placeholder.
    @Test func noPlaceholders() throws {
        #expect(try Self.uses(/\bprompt:/) == [])
        #expect(try Self.uses(/\b(?:TextField|SecureField)\s*\(/) == [])
    }

    /// "the Add treatment › minutes ago input is not a number input": a number is typed in the one number field.
    @Test func numbersUseTheNumberField() throws {
        #expect(try Self.uses(/\bStepper\s*\(/) == [])
    }

    /// Cancel and the action at the regular size (the owner: the Cancel and Save buttons' height was wrong); a Cancel
    /// is the shared closing row's.
    @Test func dialogButtonsAreRegularSized() throws {
        #expect(try Self.uses(/\.controlSize\(\.large\)/) == [])
        #expect(try Self.uses(/keyboardShortcut\(\.cancelAction\)/) == [])
    }

    /// The owner, 2026-11-15: "it doesn't look like warnings that are similar are using a shared component". A warning
    /// or an error is a `Notice` (its symbol in its colour, the words at full contrast), never a hand-made label.
    @Test func warningsAreNotices() throws {
        #expect(try Self.uses(/(?:systemImage|systemName):\s*"exclamationmark\.(?:triangle|octagon)/) == [])
    }

    /// The owner, 2026-11-23: every "update Sukrara" message on the Mac looks like one card, the Android card's
    /// equivalent: an *Update…* button or the update symbol is the shared `UpdateNotice`'s, never drawn by hand.
    @Test func updateMessagesAreUpdateNotices() throws {
        #expect(try Self.uses(/Button\(\s*L10n\.t\("Update…"\)/) == [])
        #expect(try Self.uses(/"clock\.arrow\.circlepath"/) == [])
    }

    @Test func aStepStaysOnTheGridAndInRange() {
        func step(_ t: String, _ by: Double, _ r: ClosedRange<Double>, _ d: Int = 0) -> String {
            NumberField<Bool>.stepped(t, by: by, in: r, decimals: d)
        }
        #expect(step("4", 0.5, 0.5...100, 1) == "4.5")
        #expect(step("4,3", 0.5, 0.5...100, 1) == "4.5")
        #expect(step("4.3", -0.5, 0.5...100, 1) == "4")
        #expect(step("", 0.5, 0.5...100, 1) == "0.5")
        #expect(step("", -0.5, 0.5...100, 1) == "0.5")
        #expect(step("100", 0.5, 0.5...100, 1) == "100")
        #expect(step("12", 5, 0...1439) == "15")
        #expect(step("15", -5, 0...1439) == "10")
        #expect(step("", 5, 0...1439) == "5")
        #expect(step("3", -5, 0...1439) == "0")
        #expect(step("1438", 5, 0...1439) == "1439")
    }
}
