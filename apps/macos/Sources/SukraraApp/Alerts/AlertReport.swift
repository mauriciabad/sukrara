// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import Foundation

/// *Copy Report* of the panel's "An alert couldn't be shown": the alert posts macOS refused (step and error), the app's
/// and macOS's versions; nothing personal. The same lines as the Android alert checklist's report.
enum AlertReport {
    static func text(failedSteps: Set<String>, version: String, os: String) -> String {
        "Sukrara \(version) · macOS \(os)\nAlert steps failed: \(failedSteps.sorted().joined(separator: ", "))"
    }

    /// Puts the report on the clipboard; a haptic confirms the click where there's a trackpad.
    static func copy(_ failedSteps: Set<String>) {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "dev"
        let v = ProcessInfo.processInfo.operatingSystemVersion
        let report = text(
            failedSteps: failedSteps, version: version, os: "\(v.majorVersion).\(v.minorVersion).\(v.patchVersion)")
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(report, forType: .string)
        NSHapticFeedbackManager.defaultPerformer.perform(.generic, performanceTime: .now)
    }
}
