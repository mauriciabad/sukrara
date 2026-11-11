// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import CryptoKit
import Foundation
import SukraraCore
import SukraraTesting
import Testing

@testable import SukraraApp

/// The updater's wiring (#91): who gets one, the feed it reads and the appcast the website serves. Sparkle itself is
/// never started here (no network): `StillUpdater` stands in for it.
@Suite struct UpdatesTests {
    static let macRoot = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
        .deletingLastPathComponent()
    static let repoRoot = macRoot.deletingLastPathComponent().deletingLastPathComponent()

    func plist(_ path: String) throws -> [String: Any] {
        let data = try Data(contentsOf: Self.macRoot.appending(path: path))
        return try #require(try PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any])
    }

    // MARK: - Who has an updater

    @Test func onlyTheInstalledGitHubVersionMayStartTheUpdater() throws {
        #expect(ServicesPlan.all(for: .github).updater)
        #expect(!ServicesPlan.all(for: .appStore).updater)
        #expect(!ServicesPlan.none.updater)
        let home = URL(fileURLWithPath: "/Users/u", isDirectory: true)
        let git: (String) -> Bool = { $0 == "/Users/u/repo/.git" }
        func plan(_ build: BuildInfo, env: [String: String] = [:]) throws -> LaunchPlan {
            try LaunchPlan.parse([], build: build, env: env, home: home, exists: git)
        }
        let installed = BuildInfo(isDebugBuild: false, bundlePath: "/Applications/Sukrara.app", isBundle: true)
        #expect(try plan(installed).services.updater)
        // dev runs (a bundle in the checkout, a bare executable, a debug build), the fixture and snapshots: never
        for dev in [
            BuildInfo(isDebugBuild: false, bundlePath: "/Users/u/repo/apps/macos/build/Sukrara.app", isBundle: true),
            BuildInfo(isDebugBuild: false, bundlePath: "/Users/u/repo/apps/macos/.build/release", isBundle: false),
            BuildInfo(isDebugBuild: true, bundlePath: "/Applications/Sukrara.app", isBundle: true),
        ] {
            #expect(try !plan(dev, env: ["SUKRARA_DEV": "1"]).services.updater, "\(dev.bundlePath)")
        }
        let debug = BuildInfo(isDebugBuild: true, bundlePath: "/Users/u/repo/apps/macos/.build/debug", isBundle: false)
        #expect(try !plan(debug, env: ["SUKRARA_DEV": "1", "SUKRARA_FIXTURE": "1"]).services.updater)
        let snapshot = try LaunchPlan.snapshot(
            SnapshotRequest(surface: "card", outputDirectory: URL(fileURLWithPath: "/tmp/s")), build: debug,
            scratch: URL(fileURLWithPath: "/tmp/s"))
        #expect(!snapshot.services.updater)
    }

    /// The App Store executable passes no updater: even a plan that allows one starts none. An offline plan never
    /// even creates it.
    @Test func theUpdaterStartsOnlyWhenThePlanAllowsItAndTheExecutableHasOne() {
        let scratch = URL(fileURLWithPath: NSTemporaryDirectory()).appending(path: "updates-\(UUID().uuidString)")
        var onlyUpdater = ServicesPlan.none
        onlyUpdater.updater = true
        let allowed = LaunchPlan(mode: .normal, services: onlyUpdater, data: .isolated(root: scratch))
        let storeExecutable = AppServices(plan: allowed, updater: nil)
        allowed.services.start(using: storeExecutable)
        #expect(storeExecutable.updates == nil)

        let engine = StillUpdater()
        let github = AppServices(plan: allowed, updater: { engine })
        allowed.services.start(using: github)
        #expect(github.updates != nil)
        #expect(engine.started)

        var made = 0
        let offline = LaunchPlan(mode: .normal, services: .none, data: .isolated(root: scratch))
        let dev = AppServices(
            plan: offline,
            updater: {
                made += 1
                return StillUpdater()
            })
        offline.services.start(using: dev)
        #expect(dev.updates == nil && made == 0)
    }

    // MARK: - The model

    @Test func aScheduledUpdateIsRemindedUntilLaterOrLookedAt() {
        let engine = StillUpdater()
        let updates = Updates(engine: engine)
        #expect(updates.reminder == .none)
        updates.found(version: "0.14.0", ready: false)
        #expect(updates.reminder == .available(version: "0.14.0", ready: false))
        updates.later()
        #expect(updates.reminder == .none)
        updates.found(version: "0.14.0", ready: true)
        updates.checkNow()  // the panel's Install…: the updater's window, with the update found
        #expect(engine.checks == 1)
        updates.attended()
        #expect(updates.reminder == .none)
    }

    @Test func theSwitchesAreTheUpdatersOwn() {
        let engine = StillUpdater()
        let updates = Updates(engine: engine)
        #expect(updates.automaticChecks && !updates.automaticDownloads)
        updates.automaticDownloads = true
        #expect(engine.automaticallyDownloads)
        updates.automaticChecks = false
        #expect(!engine.automaticallyChecks)
        // changed in the updater's own window (its "automatically download" checkbox)
        updates.settings(automaticChecks: true, automaticDownloads: false)
        #expect(updates.automaticChecks && !updates.automaticDownloads)
        let when = Date(timeIntervalSince1970: 1_800_000_000)
        updates.state(canCheck: true, lastCheck: when)
        #expect(updates.canCheck && updates.lastCheck == when)
        #expect(UpdateRows.lastCheck(nil) == L10n.t("Not checked yet"))
    }

    @Test func prereleasesOnlyWhileTheVersionIs0x() {
        #expect(Updates.channels(installed: "0.13.0") == ["prerelease"])
        #expect(Updates.channels(installed: "0.14.2") == [Updates.prereleaseChannel])
        #expect(Updates.channels(installed: "1.0.0").isEmpty)
        #expect(Updates.channels(installed: "2.3.1").isEmpty)
        #expect(Updates.channels(installed: "").isEmpty)
    }

    // MARK: - The feed

    /// Sparkle's keys are only in Bundle/sparkle.plist, which bundle.sh merges into the GitHub version alone: the feed
    /// is the website's appcast over https, the key a 32-byte EdDSA public key, automatic checks on and automatic
    /// install off as the schema says.
    @Test func theGitHubVersionReadsTheWebsitesAppcast() throws {
        let sparkle = try plist("Bundle/sparkle.plist")
        let feed = try #require(sparkle["SUFeedURL"] as? String)
        #expect(feed == Updates.feedURL.absoluteString)
        #expect(Updates.feedURL.scheme == "https")
        let cname = try String(contentsOf: Self.repoRoot.appending(path: "docs/CNAME"), encoding: .utf8)
        #expect(Updates.feedURL.host() == cname.trimmingCharacters(in: .whitespacesAndNewlines))
        #expect(Updates.feedURL.lastPathComponent == "appcast.xml")
        #expect(FileManager.default.fileExists(atPath: Self.repoRoot.appending(path: "docs/appcast.xml").path))
        let key = try #require((sparkle["SUPublicEDKey"] as? String).flatMap { Data(base64Encoded: $0) })
        _ = try Curve25519.Signing.PublicKey(rawRepresentation: key)
        #expect(sparkle["SUVerifyUpdateBeforeExtraction"] as? Bool == true)

        let rows =
            try JSONSerialization.jsonObject(
                with: Data(contentsOf: Contracts.url("settings/settings.json"))) as? [String: Any]
        let settings = try #require(rows?["settings"] as? [[String: Any]])
        func schemaDefault(_ id: String) -> Bool? { settings.first { $0["id"] as? String == id }?["default"] as? Bool }
        #expect(sparkle["SUEnableAutomaticChecks"] as? Bool == schemaDefault("update.automaticChecks"))
        #expect((sparkle["SUAutomaticallyUpdate"] as? Bool ?? false) == schemaDefault("update.automaticInstall"))

        // the shared Info.plist (both versions) has none of them
        let info = try String(contentsOf: Self.macRoot.appending(path: "Bundle/Info.plist.in"), encoding: .utf8)
        #expect(!info.contains("<key>SU"))
    }

    /// Every item of docs/appcast.xml: a GitHub release's dmg over https with an EdDSA signature and its length, a
    /// numeric build, the minimum macOS of Info.plist.in, the prerelease channel exactly for 0.x versions.
    @Test func theAppcastsItemsAreSignedGitHubReleases() throws {
        let data = try Data(contentsOf: Self.repoRoot.appending(path: "docs/appcast.xml"))
        let parsed = AppcastItems()
        let parser = XMLParser(data: data)
        parser.shouldProcessNamespaces = false
        parser.delegate = parsed
        #expect(parser.parse(), "docs/appcast.xml isn't well-formed: \(String(describing: parser.parserError))")
        #expect(parsed.channels == 1)
        let info = try plist("Bundle/Info.plist.in")
        let minimumOS = try #require(info["LSMinimumSystemVersion"] as? String)
        var builds: [Int] = []
        for item in parsed.items {
            let version = try #require(item["sparkle:shortVersionString"], "\(item)")
            #expect(version.wholeMatch(of: /\d+\.\d+\.\d+/) != nil, "\(version)")
            let build = try #require(item["sparkle:version"].flatMap { Int($0) }, "\(version)")
            let parts = version.split(separator: ".").compactMap { Int($0) }
            #expect(build == parts[0] * 1_000_000 + parts[1] * 1000 + parts[2], "\(version)")
            builds.append(build)
            #expect(item["sparkle:minimumSystemVersion"] == minimumOS, "\(version)")
            #expect(
                item["sparkle:channel"] == (parts[0] == 0 ? Updates.prereleaseChannel : nil),
                "\(version): prereleases are in the prerelease channel")
            #expect(
                item["enclosure.url"]
                    == "https://github.com/mauriciabad/sukrara/releases/download/v\(version)/Sukrara-\(version).dmg")
            #expect(Int(item["enclosure.length"] ?? "") ?? 0 > 0, "\(version)")
            let signature = item["enclosure.sparkle:edSignature"].flatMap { Data(base64Encoded: $0) }
            #expect(signature?.count == 64, "\(version): no EdDSA signature")
        }
        #expect(builds == builds.sorted(by: >), "newest first")
        #expect(Set(builds).count == builds.count, "one item per version")
    }
}

/// The items of an appcast as flat dictionaries: child element texts by name, the enclosure's attributes as
/// `enclosure.<attribute>`.
private final class AppcastItems: NSObject, XMLParserDelegate {
    var items: [[String: String]] = []
    var channels = 0
    private var item: [String: String]?
    private var text = ""

    func parser(
        _ parser: XMLParser, didStartElement name: String, namespaceURI: String?, qualifiedName: String?,
        attributes: [String: String] = [:]
    ) {
        text = ""
        if name == "channel" { channels += 1 }
        if name == "item" { item = [:] }
        if name == "enclosure" { for (k, v) in attributes { item?["enclosure.\(k)"] = v } }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) { text += string }

    func parser(_ parser: XMLParser, didEndElement name: String, namespaceURI: String?, qualifiedName: String?) {
        if name == "item", let item {
            items.append(item)
            self.item = nil
        } else if item != nil, name != "enclosure" {
            item?[name] = text.trimmingCharacters(in: .whitespacesAndNewlines)
        }
    }
}
