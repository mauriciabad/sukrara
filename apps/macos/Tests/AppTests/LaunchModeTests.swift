// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import Testing

@testable import SukraraApp

@Suite struct LaunchModeTests {
    let home = URL(fileURLWithPath: "/Users/u", isDirectory: true)
    /// A file system with one git checkout at /Users/u/repo.
    let exists: (String) -> Bool = { $0 == "/Users/u/repo/.git" }

    let installed = BuildInfo(isDebugBuild: false, bundlePath: "/Users/u/Applications/Sukrara.app", isBundle: true)
    let downloaded = BuildInfo(isDebugBuild: false, bundlePath: "/Users/u/Downloads/Sukrara.app", isBundle: true)
    let repoBundle = BuildInfo(
        isDebugBuild: false, bundlePath: "/Users/u/repo/apps/macos/build/Sukrara.app", isBundle: true)
    let bareRelease = BuildInfo(
        isDebugBuild: false, bundlePath: "/Users/u/repo/apps/macos/.build/release", isBundle: false)
    let debugBundle = BuildInfo(isDebugBuild: true, bundlePath: "/Users/u/Applications/Sukrara.app", isBundle: true)

    func plan(_ build: BuildInfo, _ args: [String] = [], env: [String: String] = [:]) throws(LaunchError) -> LaunchPlan
    {
        try LaunchPlan.parse(args, build: build, env: env, home: home, exists: exists)
    }

    @Test func installedCopyStartsEverythingWithTheUsersData() throws {
        for build in [installed, downloaded] {
            let p = try plan(build)
            #expect(p.mode == .normal)
            #expect(p.services == .all)
            #expect(p.data == .user(home: home))
            #expect(p.data.keychainService == "app.mauri.sukrara")
            #expect(p.data.defaultsSuite == nil)
            #expect(!p.isOffline)
        }
    }

    /// The App Store version (#186): everything the GitHub version starts, with the user's data, but never its own
    /// updater (App Review 2.4.5 vii: the store updates it).
    @Test func theAppStoreVersionHasNoUpdater() throws {
        var store = installed
        store.distribution = .appStore
        let p = try plan(store)
        #expect(p.distribution == .appStore)
        #expect(!p.services.updater)
        #expect(!p.services.enabled.contains(.updater))
        var withoutUpdates = ServicesPlan.all
        withoutUpdates.updater = false
        #expect(p.services == withoutUpdates)
        #expect(p.data == .user(home: home))
        #expect(try plan(installed).distribution == .github)
        // the limit holds whatever the user's settings ask for
        #expect(!p.services.limited(by: .all).updater)
        // a dev copy of either version stays offline
        var devStore = repoBundle
        devStore.distribution = .appStore
        let dev = try plan(devStore, env: ["SUKRARA_DEV": "1"])
        #expect(dev.services == .none)
        #expect(dev.distribution == .appStore)
    }

    @Test func theDistributionComesFromTheInfoPlist() {
        #expect(Distribution(infoValue: "appStore") == .appStore)
        #expect(Distribution(infoValue: "github") == .github)
        #expect(Distribution(infoValue: nil) == .github)
        #expect(Distribution(infoValue: "AppStore") == .github)
        #expect(Distribution(infoValue: 1) == .github)
        #expect(Distribution.github.hasOwnUpdater)
        #expect(!Distribution.appStore.hasOwnUpdater)
    }

    @Test func oldFinderProcessSerialNumberPasses() throws {
        #expect(try plan(installed, ["-psn_0_12345"]).services == .all)
    }

    @Test(arguments: [
        ["-bogus"], ["selftest"], ["-snapshot", "card", "/tmp/x"], ["-keychainService", "t"], ["-psn_1", "x"],
    ])
    func anyOtherArgumentIsRefusedWithExit2(_ args: [String]) {
        #expect(throws: LaunchError.self) { try plan(installed, args) }
        do {
            _ = try plan(installed, args)
        } catch {
            #expect(error.exitCode == 2)
        }
    }

    /// A debug build takes the system's language overrides, so a dev run can be checked in Spanish (#77); a release
    /// build still refuses every argument.
    @Test func debugBuildsTakeTheLanguageArguments() throws {
        let args = ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES", "-NSDoubleLocalizedStrings", "YES"]
        let p = try plan(debugBundle, args, env: ["SUKRARA_DEV": "1"])
        #expect(p.isOffline && p.mode == .normal)
        #expect(throws: LaunchError.unknownArgument("-AppleLanguages")) { try plan(installed, args) }
        #expect(throws: LaunchError.unknownArgument("-AppleLanguages")) {
            try plan(debugBundle, ["-AppleLanguages"], env: ["SUKRARA_DEV": "1"])
        }
        #expect(throws: LaunchError.unknownArgument("-keychainService")) {
            try plan(debugBundle, ["-keychainService", "t"], env: ["SUKRARA_DEV": "1"])
        }
    }

    @Test func devBuildsAreRecognised() {
        #expect(!installed.isDevBuild(home: home, exists: exists))
        #expect(!downloaded.isDevBuild(home: home, exists: exists))
        #expect(repoBundle.isDevBuild(home: home, exists: exists))
        #expect(bareRelease.isDevBuild(home: home, exists: exists))
        #expect(debugBundle.isDevBuild(home: home, exists: exists))
    }

    @Test func aDotfilesRepoInHomeDoesNotMakeTheInstalledAppADevBuild() throws {
        let dotfiles: (String) -> Bool = { $0 == "/Users/u/.git" || $0 == "/Users/u/repo/.git" }
        #expect(!installed.isDevBuild(home: home, exists: dotfiles))
        #expect(repoBundle.isDevBuild(home: home, exists: dotfiles))
        let systemApp = BuildInfo(isDebugBuild: false, bundlePath: "/Applications/Sukrara.app", isBundle: true)
        #expect(!systemApp.isDevBuild(home: home, exists: dotfiles))
        #expect(try LaunchPlan.parse([], build: installed, env: [:], home: home, exists: dotfiles).services == .all)
    }

    @Test func devHomeMustNotBeTheRealHome() {
        for path in ["/Users/u", "/Users/u/", "/Users/u/./"] {
            #expect(throws: LaunchError.devHomeIsUserHome) {
                try plan(repoBundle, env: ["SUKRARA_DEV": "1", "SUKRARA_DEV_HOME": path])
            }
        }
        #expect(LaunchError.devHomeIsUserHome.exitCode == 2)
    }

    @Test(arguments: ["repo", "bare", "debug"])
    func devBuildWithoutOptInIsRefusedWithExit3(_ which: String) {
        let build = ["repo": repoBundle, "bare": bareRelease, "debug": debugBundle][which]!
        #expect(throws: LaunchError.devBuildNeedsOptIn(bundlePath: build.bundlePath)) { try plan(build) }
        #expect(LaunchError.devBuildNeedsOptIn(bundlePath: build.bundlePath).exitCode == 3)
        #expect(throws: LaunchError.self) { try plan(build, env: ["SUKRARA_DEV": "yes"]) }
    }

    @Test func devBuildWithOptInRunsOfflineWithSeparateData() throws {
        let p = try plan(repoBundle, env: ["SUKRARA_DEV": "1"])
        #expect(p.mode == .normal)
        #expect(p.services == .none)
        #expect(p.isOffline)
        #expect(p.data.isIsolated)
        #expect(p.data.keychainService == "app.mauri.sukrara.dev")
        #expect(p.data.defaultsSuite == "app.mauri.sukrara.dev")
        #expect(p.data.paths.appSupport.path.hasPrefix("/Users/u/Library/Application Support/app.mauri.sukrara.dev/"))
        #expect(p.data.paths.appSupport != SukraraPaths(home: home).appSupport)
    }

    @Test func devHomeCanBeChosen() throws {
        let p = try plan(bareRelease, env: ["SUKRARA_DEV": "1", "SUKRARA_DEV_HOME": "/tmp/devhome"])
        #expect(p.data.paths.home.path == "/tmp/devhome")
        #expect(p.services == .none)
    }

    @Test func snapshotIsOfflineIsolatedAndDebugOnly() throws {
        let request = SnapshotRequest(surface: "card", outputDirectory: URL(fileURLWithPath: "/tmp/out"))
        let scratch = URL(fileURLWithPath: "/tmp/scratch")
        let debug = BuildInfo(isDebugBuild: true, bundlePath: "/x", isBundle: false)
        let p = try LaunchPlan.snapshot(request, build: debug, scratch: scratch)
        #expect(p.mode == .snapshot(request))
        #expect(p.services == .none)
        #expect(p.data == .isolated(root: scratch))
        #expect(throws: LaunchError.debugOnly) { try LaunchPlan.snapshot(request, build: installed, scratch: scratch) }
        #expect(LaunchError.debugOnly.exitCode == 2)
    }

    /// The QR scanner's camera: only the installed app may open it; a dev, fixture or snapshot run never does (a debug
    /// run gets the made-up feed instead, a release dev copy none), and the app's services never make one there.
    @Test func onlyTheInstalledAppMayOpenTheCamera() throws {
        #expect(try plan(installed).services.allows(.camera))
        #expect(ServicesPlan.all.camera && !ServicesPlan.none.camera)
        #expect(!ServicesPlan.all.limited(by: .none).camera)
        let offline = [
            try plan(repoBundle, env: ["SUKRARA_DEV": "1"]),
            try plan(debugBundle, env: ["SUKRARA_DEV": "1", "SUKRARA_FIXTURE": "1"]),
            try LaunchPlan.snapshot(
                SnapshotRequest(surface: "windows", outputDirectory: URL(fileURLWithPath: "/tmp/out")),
                build: BuildInfo(isDebugBuild: true, bundlePath: "/x", isBundle: false),
                scratch: URL(fileURLWithPath: "/tmp/scratch")),
        ]
        for p in offline {
            #expect(!p.services.allows(.camera) && !p.services.enabled.contains(.camera))
            let services = AppServices(plan: p)
            p.services.start(using: services)
            #expect(services.makeCamera == nil)
            if case .snapshot = p.mode {
                #expect(services.scannerCamera == nil)
            } else {
                #if DEBUG
                    #expect(services.scannerCamera?() is SyntheticCamera)
                #endif
            }
        }
    }

    @Test func thePlanIsAnUpperLimitOnTheUsersChoices() {
        let wanted = ServicesPlan(ble: false, relays: true, alerts: true, widgetFeed: false, notifications: true)
        #expect(ServicesPlan.all.limited(by: wanted) == wanted)
        #expect(ServicesPlan.none.limited(by: .all) == .none)
        // Juggluco on the home network: possible in the installed app only (it connects only once switched on)
        #expect(ServicesPlan.all.allows(.lan) && !ServicesPlan.none.allows(.lan))
        #expect(!ServicesPlan.all.limited(by: wanted).lan)
    }
}

@Suite struct ServiceStartTests {
    final class Recorder: ServiceStarting {
        var started: [Service] = []
        func start(_ service: Service) { started.append(service) }
    }

    @Test func offlinePlansStartNothing() throws {
        let recorder = Recorder()
        let dev = try LaunchPlan.parse(
            [], build: BuildInfo(isDebugBuild: true, bundlePath: "/x", isBundle: false), env: ["SUKRARA_DEV": "1"],
            home: URL(fileURLWithPath: "/Users/u"), exists: { _ in false })
        dev.services.start(using: recorder)
        #expect(recorder.started.isEmpty)
    }

    @Test func startsExactlyWhatThePlanAllows() {
        let recorder = Recorder()
        ServicesPlan(ble: true, relays: false, alerts: true, widgetFeed: false, notifications: true).start(
            using: recorder)
        #expect(recorder.started == [.notifications, .ble, .alerts])
        let all = Recorder()
        ServicesPlan.all.start(using: all)
        #expect(all.started == Service.allCases)
    }
}

@Suite struct AppModelTests {
    let home = URL(fileURLWithPath: "/Users/u")

    @Test func withoutAValueTheMenuSaysWhyAndNeverShowsANumber() {
        let model = AppModel(plan: LaunchPlan(mode: .normal, services: .all, data: .user(home: home)))
        #expect(model.statusTitle == "—")
        #expect(model.notMonitoringReason == "Not monitoring: not following anyone yet")
    }

    @Test func offlineRunsSayTheyAreOfflineEvenWithAValue() {
        let model = AppModel(plan: LaunchPlan(mode: .normal, services: .none, data: .isolated(root: home)))
        model.apply(readings: [Reading(t: Date(), mgdl: 120, rate: 0)])
        #expect(model.statusTitle == "120 →")
        #expect(model.notMonitoringReason?.hasPrefix("Not monitoring: offline") == true)
    }

    @Test func aValueNeedsNoReason() {
        let model = AppModel(plan: LaunchPlan(mode: .normal, services: .all, data: .user(home: home)))
        model.apply(readings: [Reading(t: Date(), mgdl: 98, rate: -1.5)])
        #expect(model.statusTitle == "98 ↘")
        #expect(model.notMonitoringReason == nil)
    }
}
