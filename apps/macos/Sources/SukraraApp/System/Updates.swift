// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Observation

/// The updater that installs new versions of the GitHub version (#91): Sparkle, in `SukraraUpdater`, which only the
/// GitHub executable links. The App Store executable passes none, so the App Store version has no updater at all.
/// Created only by `AppServices` when the launch plan allows `.updater` (never in a dev, test, fixture or snapshot run).
public protocol UpdaterEngine: AnyObject {
    /// Starts checking (at most once a day while automatic checks are on). The engine reports into `updates`: whether
    /// a check can start, the last check, the reminder of a scheduled update it found.
    func start(reporting updates: Updates)
    /// A check the person asked for. The updater's own windows show it through: checking, the update with its notes
    /// (*Install Update*, *Remind Me Later*, *Skip This Version*), the download, *Install and Relaunch*, *You're up to
    /// date* or the error. With an update already found, it brings that update to the front.
    func checkForUpdates()
    /// Look for updates by themselves (`update.automaticChecks`).
    var automaticallyChecks: Bool { get set }
    /// Download and install found updates by themselves (`update.automaticInstall`).
    var automaticallyDownloads: Bool { get set }
}

/// What the app shows of the updater: the panel's reminder of an update a scheduled check found, Settings › General ›
/// About (Check for Updates…, the two switches, the last check) and the right-click menu's *Check for Updates…*.
@Observable
public final class Updates {
    /// An update a scheduled check found, which the panel offers until the person looks at it.
    public enum Reminder: Equatable, Sendable {
        case none
        /// `version` is available; `ready`: already downloaded, *Install and Relaunch* is one click away.
        case available(version: String, ready: Bool)
    }

    /// The feed every GitHub version reads (Info.plist `SUFeedURL`, `Bundle/sparkle.plist`): docs/appcast.xml on the
    /// website, which `tools/release/mac-release.sh` writes.
    public static let feedURL = URL(string: "https://sukrara.mauri.app/appcast.xml")!
    public private(set) var reminder: Reminder = .none
    /// False while the updater is busy (a check or an install already running): *Check for Updates…* is dimmed.
    public private(set) var canCheck = false
    public private(set) var lastCheck: Date?
    public var automaticChecks: Bool {
        didSet { if engine.automaticallyChecks != automaticChecks { engine.automaticallyChecks = automaticChecks } }
    }
    public var automaticDownloads: Bool {
        didSet {
            if engine.automaticallyDownloads != automaticDownloads {
                engine.automaticallyDownloads = automaticDownloads
            }
        }
    }
    @ObservationIgnored private let engine: any UpdaterEngine

    public init(engine: any UpdaterEngine) {
        self.engine = engine
        automaticChecks = engine.automaticallyChecks
        automaticDownloads = engine.automaticallyDownloads
    }

    func start() { engine.start(reporting: self) }

    /// *Check for Updates…* (menu, About, the panel's *Install…*).
    public func checkNow() { engine.checkForUpdates() }

    /// The panel's *Later*: the reminder goes; the next scheduled check reminds again (the updater's own *Skip This
    /// Version* skips one for good).
    public func later() { reminder = .none }

    // MARK: - Reported by the engine

    public func found(version: String, ready: Bool) { reminder = .available(version: version, ready: ready) }
    /// The person looked at the update in the updater's window, or the update session ended.
    public func attended() { reminder = .none }
    public func state(canCheck: Bool, lastCheck: Date?) {
        if self.canCheck != canCheck { self.canCheck = canCheck }
        if self.lastCheck != lastCheck { self.lastCheck = lastCheck }
    }
    /// The switches as the updater stores them (they can change from its own windows).
    public func settings(automaticChecks: Bool, automaticDownloads: Bool) {
        if self.automaticChecks != automaticChecks { self.automaticChecks = automaticChecks }
        if self.automaticDownloads != automaticDownloads { self.automaticDownloads = automaticDownloads }
    }
}

#if DEBUG
    /// An updater that does nothing (window snapshots, tests): reports a found update or a state when asked.
    final class StillUpdater: UpdaterEngine {
        var automaticallyChecks = true
        var automaticallyDownloads = false
        private(set) var checks = 0
        private(set) var started = false
        func start(reporting updates: Updates) { started = true }
        func checkForUpdates() { checks += 1 }
    }
#endif
