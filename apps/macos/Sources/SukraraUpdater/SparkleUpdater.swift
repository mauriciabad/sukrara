// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import Sparkle
import SukraraApp
import os

/// Sparkle 2 as the GitHub version's `UpdaterEngine` (#91). Sparkle reads the feed (`SUFeedURL`, the website's
/// appcast) and the EdDSA public key (`SUPublicEDKey`) from the Info.plist (`Bundle/sparkle.plist`), checks once a day
/// while automatic checks are on, verifies every download against that key and the app's Developer ID signature, and
/// shows its own windows (the update with its notes, the download, *Install and Relaunch*, up to date, errors).
///
/// Gentle reminders (a menu bar app has no window of its own to come back to): a scheduled check's update comes up in
/// Sparkle's window by itself only when Sparkle would show it in immediate focus (just launched, or the Mac was idle);
/// otherwise the panel shows a row (`Updates.Reminder`) until the person opens it or the session ends, so an update
/// never pops up over what they are doing.
public final class SparkleUpdater: NSObject, UpdaterEngine, SPUUpdaterDelegate, SPUStandardUserDriverDelegate {
    private static let log = Logger(subsystem: "app.mauri.sukrara", category: "update")
    private var controller: SPUStandardUpdaterController?
    private weak var updates: Updates?
    private var observations: [NSKeyValueObservation] = []

    override public init() {
        super.init()
        controller = SPUStandardUpdaterController(
            startingUpdater: false, updaterDelegate: self, userDriverDelegate: self)
    }

    private var updater: SPUUpdater? { controller?.updater }

    public func start(reporting updates: Updates) {
        self.updates = updates
        guard let controller else { return }
        // Sparkle changes these on the main thread (its own windows' checkbox included)
        let changed: @Sendable (SPUUpdater, Any) -> Void = { [weak self] _, _ in
            MainActor.assumeIsolated { self?.report() }
        }
        let updater = controller.updater
        observations = [
            updater.observe(\.canCheckForUpdates) { changed($0, $1) },
            updater.observe(\.lastUpdateCheckDate) { changed($0, $1) },
            updater.observe(\.automaticallyChecksForUpdates) { changed($0, $1) },
            updater.observe(\.automaticallyDownloadsUpdates) { changed($0, $1) },
        ]
        controller.startUpdater()
        report()
    }

    public func checkForUpdates() { controller?.checkForUpdates(nil) }

    public var automaticallyChecks: Bool {
        get { updater?.automaticallyChecksForUpdates ?? false }
        set { updater?.automaticallyChecksForUpdates = newValue }
    }

    public var automaticallyDownloads: Bool {
        get { updater?.automaticallyDownloadsUpdates ?? false }
        set { updater?.automaticallyDownloadsUpdates = newValue }
    }

    private func report() {
        guard let updater, let updates else { return }
        updates.state(canCheck: updater.canCheckForUpdates, lastCheck: updater.lastUpdateCheckDate)
        updates.settings(
            automaticChecks: updater.automaticallyChecksForUpdates,
            automaticDownloads: updater.automaticallyDownloadsUpdates)
    }

    // MARK: - SPUUpdaterDelegate

    /// Prereleases while this version is 0.x (every 0.x release is a GitHub prerelease), none from 1.0.0 on.
    public func allowedChannels(for updater: SPUUpdater) -> Set<String> {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
        return Updates.channels(installed: version)
    }

    public func updater(_ updater: SPUUpdater, didAbortWithError error: any Error) {
        Self.log.error("update aborted: \(error.localizedDescription, privacy: .public)")
    }

    public func updater(
        _ updater: SPUUpdater, didFinishUpdateCycleFor updateCheck: SPUUpdateCheck, error: (any Error)?
    ) {
        if let error {
            Self.log.info("update cycle ended: \(error.localizedDescription, privacy: .public)")
        }
        report()
    }

    // MARK: - SPUStandardUserDriverDelegate (gentle reminders)

    public var supportsGentleScheduledUpdateReminders: Bool { true }

    public func standardUserDriverShouldHandleShowingScheduledUpdate(
        _ update: SUAppcastItem, andInImmediateFocus immediateFocus: Bool
    ) -> Bool {
        immediateFocus
    }

    public func standardUserDriverWillHandleShowingUpdate(
        _ handleShowingUpdate: Bool, forUpdate update: SUAppcastItem, state: SPUUserUpdateState
    ) {
        guard !handleShowingUpdate, !state.userInitiated else { return }
        updates?.found(version: update.displayVersionString, ready: state.stage != .notDownloaded)
    }

    public func standardUserDriverDidReceiveUserAttention(forUpdate update: SUAppcastItem) {
        updates?.attended()
    }

    public func standardUserDriverWillFinishUpdateSession() {
        updates?.attended()
        report()
    }
}
