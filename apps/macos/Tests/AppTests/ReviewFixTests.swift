// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraAlerts
import SukraraCore
import SukraraGraph
import Testing

@testable import SukraraApp

@Suite struct MirroringExitTests {
    func model() -> SettingsModel {
        let w = WearerSettings(
            json: #"{"alerts":{"alerts":{"low":{"repeatMin":30}},"muteAfterInsulin":{"on":false,"min":90}}}"#,
            wearer: "Ana")
        return SettingsModel(
            store: scratchStore(), follow: FollowModel(status: .notFollowing, actions: nil, macName: "Mac"), wearer: w,
            version: "0")
    }

    @Test func useOwnSwitchesTheAlertSourceToOwnAndBack() {
        let m = model()
        #expect(m.mirrors)
        m.useOwn()
        #expect(!m.mirrors && m.alerts.settings.source == .own)
        m.settingsFrom = .wearer
        #expect(m.alerts.settings.source == .wearer)
    }

    @Test func defaultsKeepsTheOwnSource() {
        let m = model()
        m.useDefaults()
        #expect(m.settingsFrom == .default && m.alerts.settings.source == .own)
    }

    @Test func mirroredAlertRowsReadTheWearersValues() {
        let m = model()
        #expect(m.shownAlerts[.low].repeatMin == 30 && m.alertsMirrored)
        #expect(m.shownAlerts.muteAfterInsulin == QuietRule(on: false, min: 90))
        m.useOwn()
        #expect(m.shownAlerts[.low].repeatMin == AlertSettings()[.low].repeatMin && !m.alertsMirrored)
    }

    /// A mirrored row says where its value comes from in words (#106), never only a dimmed switch.
    @Test func readOnlyRowsNameTheirSource() {
        let m = model()
        #expect(m.mirrorSource == "from Ana")
        m.useDefaults()
        #expect(m.mirrorSource == "Sukrara's default")
        m.useOwn()
        #expect(m.mirrorSource.isEmpty && !m.alertsMirrored)
        #expect(ReadOnlyValue.onOff(true) == "On" && ReadOnlyValue.onOff(false) == "Off")
    }

    @Test func defaultsNeverOverwriteThisMacsOwnValues() {
        let m = model()
        m.useOwn()
        m.alerts.update { $0[.low].threshold = 80 }
        m.setNumber(150, MacSettings.bandTight)
        m.useDefaults()
        #expect(m.shownAlerts[.low].threshold == 69 && m.graph.bands.tight == 140)
        m.useOwn()
        #expect(m.shownAlerts[.low].threshold == 80 && m.graph.bands.tight == 150)
    }

    @Test func copyTheWearersSettingsMakesThemThisMacsOwn() {
        let m = SettingsModel(
            store: scratchStore(), follow: FollowModel(status: .notFollowing, actions: nil, macName: "Mac"),
            wearer: WearerSettings(
                json: #"{"graph":{"tight":130,"ringOrder":"VERY_LOW,LOW,TIGHT,TARGET,HIGH,VERY_HIGH"},"#
                    + #""alerts":{"alerts":{"low":{"mgdl":75}}}}"#, wearer: "Ana"),
            version: "0")
        m.copyWearer()
        #expect(m.settingsFrom == .custom && m.editable)
        #expect(m.graph.bands.tight == 130 && m.graph.ringOrder.first == .veryLow)
        #expect(m.alerts.settings[.low].threshold == 75)
    }

    @Test func resetToDefaultsChangesOnlyTheMirroredSettings() {
        let m = model()
        m.useOwn()
        m.setNumber(150, MacSettings.bandTight)
        m.setNumber(3, MacSettings.ringCount)
        m.setPlaceHours(6, .menubar)
        m.alerts.update {
            $0[.low].threshold = 80
            $0[.high].repeatMin = 60
            $0.muteAfterCarbs = QuietRule(on: false, min: 30)
            $0[.noData].threshold = 45
            $0[.low].alarm = !AlertSettings()[.low].alarm
            $0.sounds.pack = "arcade"
            $0.notificationActions.muteMin = 60
        }
        let before = m.alerts.settings
        m.resetToDefaults()
        let d = AlertSettings()
        #expect(m.settingsFrom == .custom && m.graph == GraphConfig())
        #expect(
            m.alerts.settings[.low].threshold == d[.low].threshold
                && m.alerts.settings[.high].repeatMin == d[.high].repeatMin)
        #expect(m.alerts.settings.muteAfterCarbs == d.muteAfterCarbs)
        // what isn't mirrored stays this Mac's own
        #expect(m.alerts.settings[.noData] == before[.noData] && m.alerts.settings[.low].alarm == before[.low].alarm)
        #expect(m.alerts.settings.sounds == before.sounds && m.alerts.settings.notificationActions.muteMin == 60)
        #expect(m.placeHours(.menubar) == 6)
    }

    @Test func resetToDefaultsDoesNothingOutsideCustom() {
        let m = model()
        m.alerts.update { $0[.low].threshold = 80 }
        m.resetToDefaults()
        #expect(m.settingsFrom == .wearer && m.alerts.settings[.low].threshold == 80)
    }

    @Test func thePaneIsRemembered() {
        let store = scratchStore()
        let follow = FollowModel(status: .notFollowing, actions: nil, macName: "Mac")
        let m = SettingsModel(store: store, follow: follow, version: "0")
        m.pane = .rings
        #expect(SettingsModel(store: store, follow: follow, version: "0").pane == .rings)
    }
}

@Suite struct FailedDeleteTests {
    @Test func aTombstoneThatIsntSentKeepsTheEntry() async {
        let sender = RecordingSender()
        sender.accept = false
        let t = Treatment(id: "a", k: .rapid, a: 4, t: 1000, u: 1, o: .mac)
        let m = TreatmentsModel(rows: [.init(treatment: t, delivery: .accepted)], canWrite: true, actions: sender)
        await m.delete(m.rows[0])
        #expect(m.rows.map(\.id) == ["a"] && !m.undoAvailable)
    }
}
