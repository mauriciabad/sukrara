// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Testing

@testable import SukraraApp

/// About › Sukrara on other devices (#179): each row opens that app's page on the website, never a
/// download.
@Suite struct OtherDevicesTests {
    @Test func eachAppOpensItsPageOnTheWebsite() {
        #expect(OtherDevice.all.map(\.url.absoluteString) == ["https://sukrara.mauri.app/android/"])
        #expect(OtherDevice.all.map(\.name) == [L10n.t("Sukrara for Android")])
    }

    @Test func noRowIsADownload() {
        for d in OtherDevice.all {
            #expect(d.url.scheme == "https" && d.url.host() == "sukrara.mauri.app")
            #expect(!["apk", "dmg", "zip"].contains(d.url.pathExtension))
        }
    }
}
