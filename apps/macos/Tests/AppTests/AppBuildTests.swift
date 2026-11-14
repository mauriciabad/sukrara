// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Testing

@testable import SukraraApp

/// What the build is (docs/DEVELOPMENT.md › Development and release builds): only Info.plist `SukraraBuild` = "release"
/// is a release; a development build (tools/install.sh, any local build, no Info.plist at all) says so wherever the
/// version shows (About, Settings › General, Copy Report) and never with the release wording. Android: AppBuildTest,
/// VersionSummaryTest.
@Suite struct AppBuildTests {
    let dev = AppBuild.development(commits: 23, commit: "d744fd74", date: "2026-11-14")
    let gb = Locale(identifier: "en_GB")

    @Test func onlyTheExactReleaseValueIsARelease() {
        #expect(AppBuild(infoValue: "release") == .release)
        #expect(AppBuild(infoValue: " release\n") == .release)
        for value: Any? in [nil, "", "development", "releases", "release 1", "Release", "@SUKRARA_BUILD@", 1] {
            #expect(
                AppBuild(infoValue: value) == .development(commits: nil, commit: nil, date: nil),
                "\(String(describing: value))")
        }
        #expect(AppBuild(infoValue: "development 23 d744fd74 2026-11-14") == dev)
        #expect(
            AppBuild(infoValue: "development ? d744fd74 2026-11-14")
                == .development(commits: nil, commit: "d744fd74", date: "2026-11-14"))
        #expect(
            AppBuild(infoValue: "development 0 abc not-a-date") == .development(commits: 0, commit: "abc", date: nil))
    }

    @Test func reportsSayDevelopmentBuild() {
        #expect(AppBuild.release.report("0.14.5") == "0.14.5")
        #expect(dev.report("0.14.5") == "0.14.5 development build +23 d744fd74 2026-11-14")
        #expect(
            AlertReport.text(failedSteps: ["post"], version: dev.report("0.14.5"), os: "26.1").hasPrefix(
                "Sukrara 0.14.5 development build +23 d744fd74 2026-11-14 · macOS 26.1"))
    }

    @Test func aReleaseKeepsItsWording() {
        #expect(GeneralPane.versionText("0.14.5", .github, .release) == L10n.t("0.14.5 · GitHub version"))
        #expect(GeneralPane.versionText("0.14.5", .appStore, .release) == L10n.t("0.14.5 · App Store version"))
        #expect(AboutView.buildName(.github, .release, version: "0.14.5") == L10n.t("GitHub version"))
        #expect(AboutView.buildName(.appStore, .release, version: "0.14.5") == L10n.t("App Store version"))
    }

    @Test func aDevelopmentBuildSaysSoEverywhere() {
        let dev = L10n.t("Development build")
        let full = "\(dev) · \(L10n.t("\("0.14.5") + \(23) commits")) · d744fd74 · 14 Nov"
        for distribution in [Distribution.github, .appStore] {
            #expect(GeneralPane.versionText("0.14.5", distribution, self.dev, locale: gb) == full)
            #expect(AboutView.buildName(distribution, self.dev, version: "0.14.5", locale: gb) == full)
        }
        #expect(!full.contains("GitHub"))
        let one = AppBuild.development(commits: 1, commit: "d744fd74", date: "2026-11-14")
        #expect(
            one.developmentText("0.14.5", locale: gb)
                == "\(dev) · \(L10n.t("\("0.14.5") + 1 commit")) · d744fd74 · 14 Nov")
        // the release commit's own source built by install.sh, and a bare swift build: still not the release
        let zero = AppBuild.development(commits: 0, commit: "d744fd74", date: "2026-11-14")
        #expect(zero.developmentText("0.14.5", locale: gb) == "\(dev) · 0.14.5 · d744fd74 · 14 Nov")
        #expect(AppBuild(infoValue: nil).developmentText("0.14.5", locale: gb) == "\(dev) · 0.14.5")
        #expect(AppBuild.release.developmentText("0.14.5") == nil)
    }

    /// The test executable has no SukraraBuild: it runs as a development build, never as a release.
    @Test func withoutAnInfoPlistItIsADevelopmentBuild() {
        #expect(!AppBuild.current.isRelease)
    }
}
