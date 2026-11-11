// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// *Sukrara on Other Devices* (Settings › General, #179): each other app's page on the website (what it does, how to
/// get it), never a download. The website is docs/ (.github/workflows/website.yml); the pages are docs/android/index.md and docs/mac/index.md.
struct OtherDevice: Identifiable, Equatable {
    var name: String
    var summary: String
    var url: URL
    var id: URL { url }

    /// The apps this Mac lists: Android (the wearer's phone, or a follower's).
    static var all: [OtherDevice] {
        [
            OtherDevice(
                name: L10n.t("Sukrara for Android"),
                summary: L10n.t("Reads the sensor, alerts and shares with this Mac"),
                url: URL(string: "https://sukrara.mauri.app/android/")!)
        ]
    }
}
