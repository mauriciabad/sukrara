// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import Foundation
import ServiceManagement
import SukraraAlerts
import SukraraCore
import SukraraGraph
import SukraraShare
import SukraraTesting
import Testing

@testable import SukraraApp

/// A fresh UserDefaults suite per test: never the app's own domain.
func scratchStore() -> DefaultsSettingsStore {
    let name = "app.mauri.sukrara.tests.\(UUID().uuidString)"
    let d = UserDefaults(suiteName: name)!
    d.removePersistentDomain(forName: name)
    return DefaultsSettingsStore(defaults: d)
}

/// The settings window's model over a fresh store, following nobody.
func scratchSettings(wearer: WearerSettings? = nil) -> SettingsModel {
    SettingsModel(
        store: scratchStore(), follow: FollowModel(status: .notFollowing, actions: nil), wearer: wearer, version: "0")
}

/// `sukrara:` links (`AppLink`): the widget's two clicks never reach the follow step as a bad invite.
@Suite struct AppLinkTests {
    @Test func widgetLinksRouteToTheirPlace() {
        // the exact links the widget sets (`.widgetURL`)
        #expect(AppLink(URL(string: SukraraIDs.Link.newTreatment)!) == .newTreatment)
        #expect(AppLink(URL(string: SukraraIDs.Link.panel)!) == .panel)
        #expect(AppLink(URL(string: "sukrara://open/")!) == .panel)
    }

    @Test func everythingElseIsAnInviteForTheFollowStep() {
        let invite = "sukrara:join#AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        #expect(AppLink(URL(string: invite)!) == .invite(invite))
        #expect(AppLink(URL(string: "sukrara://treatment/old")!) == .invite("sukrara://treatment/old"))
    }

    /// #110: a link that launches the app arrives before the windows exist; it waits and opens once launch is done.
    @Test func aLinkThatArrivesDuringLaunchOpensAfterIt() {
        var inbox = LinkInbox()
        let invite = URL(string: "sukrara:join#AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")!
        let add = URL(string: SukraraIDs.Link.newTreatment)!
        #expect(inbox.receive([invite, URL(string: "https://example.com")!]).isEmpty)
        #expect(inbox.receive([add]).isEmpty)
        #expect(inbox.pending == [invite, add])  // only sukrara: links, in order
        #expect(inbox.launched() == [invite, add])
        #expect(inbox.pending.isEmpty && inbox.launched().isEmpty)  // each opens once
        #expect(inbox.receive([add]) == [add])  // after launch: at once
    }
}

/// Open at login over a fake `SMAppService` (never the real login items).
@Suite struct LoginItemTests {
    final class FakeLogin: LoginService {
        var status: SMAppService.Status = .notRegistered
        var next: SMAppService.Status = .enabled
        var fail = false
        var calls: [String] = []
        struct Refused: Error {}
        func register() throws {
            calls.append("register")
            if fail { throw Refused() }
            status = next
        }
        func unregister() throws {
            calls.append("unregister")
            status = .notRegistered
        }
    }

    @Test func onOffAndApprovalFollowTheService() {
        let fake = FakeLogin()
        let item = LoginItem(service: fake)
        #expect(item.status == .off)
        item.set(true)
        #expect(item.status == .on && fake.calls == ["register"])
        item.set(false)
        #expect(item.status == .off && fake.calls == ["register", "unregister"])
        fake.next = .requiresApproval
        item.set(true)
        #expect(item.status == .needsApproval)
    }

    @Test func aRefusedRegistrationSaysWhyAndTheSwitchShowsTheRealState() {
        let fake = FakeLogin()
        fake.fail = true
        let item = LoginItem(service: fake)
        item.set(true)
        #expect(item.status == .off && item.lastError != nil && item.offersLoginItems)
        item.refresh()  // becoming active: the reason stays on screen
        #expect(item.lastError != nil)
        fake.fail = false
        item.set(true)
        #expect(item.status == .on && item.lastError == nil && !item.offersLoginItems)
    }

    @Test func aRefusedUnregisterKeepsTheSwitchOn() {
        final class StuckOn: LoginService {
            var status: SMAppService.Status = .enabled
            struct Refused: Error {}
            func register() throws {}
            func unregister() throws { throw Refused() }
        }
        let item = LoginItem(service: StuckOn())
        item.set(false)
        #expect(item.status == .on && item.status.isOn && item.lastError != nil)
    }

    @Test func aThrowWhileMacOSStillAsksForApprovalIsNoFailure() {
        final class ThrowsButRegisters: LoginService {
            var status: SMAppService.Status = .notRegistered
            struct OperationNotPermitted: Error {}
            func register() throws {
                status = .requiresApproval
                throw OperationNotPermitted()
            }
            func unregister() throws {}
        }
        let item = LoginItem(service: ThrowsButRegisters())
        item.set(true)
        #expect(item.status == .needsApproval && item.lastError == nil && item.offersLoginItems)
    }

    @Test func notFoundIsOffAndARegistrationThatDoesntTakeSaysSo() {
        let fake = FakeLogin()
        fake.status = .notFound
        let item = LoginItem(service: fake)
        #expect(item.status == .off && item.lastError == nil)
        fake.next = .notFound  // register returned, but macOS still has no login item
        item.set(true)
        #expect(item.status == .off && item.lastError != nil)
    }

    /// #108: setup's Open at login step is done only once macOS has the login item on.
    @Test func theSetupStepCompletesOnlyWhenTheLoginItemIsOn() {
        let fake = FakeLogin()
        fake.fail = true
        let model = SetupModel(settings: scratchSettings())
        model.loginItem = LoginItem(service: fake)
        model.flow.open(.openAtLogin)
        model.openAtLogin()  // refused: the step stays, with the reason
        #expect(model.flow.current == .openAtLogin && model.flow.states[.openAtLogin] == nil)
        #expect(model.loginItem?.lastError != nil && model.loginItem?.offersLoginItems == true)
        fake.fail = false
        fake.next = .requiresApproval
        model.openAtLogin()  // waiting for approval: still not done
        #expect(model.flow.current == .openAtLogin && model.flow.states[.openAtLogin] == nil)
        #expect(model.loginItem?.status == .needsApproval)
        fake.status = .enabled  // allowed in System Settings › Login Items
        model.openAtLogin()
        #expect(model.flow.states[.openAtLogin] == .done && model.flow.current == nil)
        #expect(fake.calls == ["register", "register"])  // found on: not registered again
    }

    @Test func withoutALoginItemTheSetupStepDoesNothing() {
        let model = SetupModel(settings: scratchSettings())
        model.flow.open(.openAtLogin)
        model.openAtLogin()
        #expect(model.flow.current == .openAtLogin && model.flow.states[.openAtLogin] == nil)
    }

    @Test func everyStateBeyondOnAndOffHasALine() {
        #expect(OpenAtLoginRows.note(.on, error: nil) == nil && OpenAtLoginRows.note(.off, error: nil) == nil)
        #expect(OpenAtLoginRows.note(.needsApproval, error: nil) != nil)
        let refused = OpenAtLoginRows.note(.on, error: "Operation not permitted")
        #expect(refused?.contains("Operation not permitted") == true)
    }
}

/// The panel's Add Treatment… row: available only while following with the wearer's permission, else one of four
/// named reasons that say what would change it.
@Suite struct AddTreatmentAvailabilityTests {
    @Test func dimmedWithItsNamedReasonUnlessAllowed() {
        #expect(AddTreatmentAvailability(link: .following(wearer: "Ana", canWrite: true)) == .available)
        #expect(AddTreatmentAvailability(link: .following(wearer: "Ana", canWrite: true)).reason == nil)
        let notWriter = AddTreatmentAvailability(link: .following(wearer: "Ana", canWrite: false))
        #expect(notWriter == .notWriter(wearer: "Ana") && notWriter.reason?.hasPrefix("Ana hasn't") == true)
        let waiting = AddTreatmentAvailability(link: .requested(code: "1234", wearer: "Ana", pairing: nil))
        #expect(waiting.reason == "Waiting for Ana to approve this Mac")
        #expect(AddTreatmentAvailability(link: .revoked) == .revoked)
        #expect(AddTreatmentAvailability(link: .declined) == .ended)
        #expect(AddTreatmentAvailability(link: .inviteUsed) == .ended)
        #expect(AddTreatmentAvailability(link: .notFollowing) == .notFollowing)
        let reasons = [
            AddTreatmentAvailability.waiting(wearer: nil), .notWriter(wearer: nil), .revoked, .ended, .notFollowing,
        ].compactMap(\.reason)
        #expect(Set(reasons).count == 5, "each state names its own reason")
    }

    /// The card's pill: Eat carbs / Take insulin open Add Treatment on their field where this Mac may log; a mute
    /// pill opens its ways out.
    @Test func pillActions() {
        #expect(PillAction.of(.needed(.low), canAdd: true) == .treat(.carbs))
        #expect(PillAction.of(.needed(.high), canAdd: true) == .treat(.rapid))
        #expect(PillAction.of(.needed(.low), canAdd: false) == .none)
        #expect(PillAction.of(.paused(.high, until: 100), canAdd: false) == .quiet(.high))
        let mark = GraphMark(t: 0, k: "carbs", a: 20)
        #expect(PillAction.of(.taken(.low, treatment: mark, until: 100), canAdd: true) == .quiet(.low))
        #expect(PillAction.of(.muted(until: AlertStatus.never), canAdd: true) == .muteAll)
        #expect(PillAction.of(.none, canAdd: true) == .none)
        // Mute all on while out of range: the pill's way out is Mute all's
        #expect(PillAction.of(.paused(.low, until: 100), canAdd: true, muteAll: true) == .muteAll)
    }
}

/// Add Treatment's *When?* chip, quiet line and the Treatments window's days.
@Suite struct AddTreatmentFormTests {
    let now = Date(timeIntervalSince1970: 1_800_000_000)

    @Test func whenResolvesAtSave() {
        let n = Int64(now.timeIntervalSince1970)
        #expect(TreatmentForm.When.now.resolve(now: n) == n)
        #expect(TreatmentForm.When.ago(15).resolve(now: n) == n - 900)
        #expect(TreatmentForm.When.at(n - 3600).resolve(now: n) == n - 3600)
        #expect(TreatmentForm.When.at(n + 60).resolve(now: n) == n, "never ahead of now")
        #expect(TreatmentForm.When.fromPicker(minutesAgo: "30") == .ago(30))
        #expect(TreatmentForm.When.fromPicker(minutesAgo: "0") == .now)
        #expect(TreatmentForm.When.fromPicker(minutesAgo: "") == nil)
        #expect(TreatmentForm.When.fromPicker(minutesAgo: "1440") == nil, "a day or more")
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        let c = cal.dateComponents([.hour, .minute], from: now)
        // the current minute is now; a later clock time is yesterday's
        #expect(TreatmentForm.When.fromPicker(hour: c.hour!, minute: c.minute!, now: now, calendar: cal) == .now)
        let later = TreatmentForm.When.fromPicker(hour: (c.hour! + 1) % 24, minute: c.minute!, now: now, calendar: cal)
        #expect(later == .at(Int64(now.timeIntervalSince1970) - Int64(c.second(now, cal)) - 23 * 3600))
        #expect(AddTreatmentView.whenLabel(.ago(15), now: now) == "15 min ago")
        #expect(AddTreatmentView.whenLabel(.now, now: now) == "Now")
    }

    @Test func quietLineSaysWhatTheEntriesQuiet() {
        let n = Int64(now.timeIntervalSince1970)
        let s = AlertSettings()
        #expect(
            TreatmentQuietNote.of(rapid: true, carbs: true, at: n, now: n, settings: s, mirroring: false)
                == "Highs quiet for 1 h · lows quiet for 15 min")
        #expect(
            TreatmentQuietNote.of(rapid: false, carbs: true, at: n - 600, now: n, settings: s, mirroring: false)
                == "Lows quiet for 5 min")
        #expect(TreatmentQuietNote.of(rapid: true, carbs: false, at: n, now: n, settings: s, mirroring: true) == nil)
        #expect(
            TreatmentQuietNote.of(rapid: false, carbs: true, at: n - 3600, now: n, settings: s, mirroring: false)
                == nil)
        var off = s
        off.on = false
        #expect(TreatmentQuietNote.of(rapid: true, carbs: true, at: n, now: n, settings: off, mirroring: false) == nil)
    }

    @Test func treatmentsWindowShowsSevenDaysByDay() {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        let n = Int64(now.timeIntervalSince1970)
        let agos: [Int64] = [0, 1, 3600, 86400 + 60, 6 * 86400, 8 * 86400]
        var rows: [TreatmentsModel.Row] = []
        for (i, ago) in agos.enumerated() {
            let t = Treatment(id: "\(i)", k: .carbs, a: 10, t: n - ago, u: 1, o: .phone)
            rows.append(TreatmentsModel.Row(treatment: t, delivery: nil))
        }
        let days = TreatmentsModel.byDay(rows.reversed(), now: now, calendar: cal)
        let ids: [String] = days.flatMap { $0.rows.map { $0.id } }
        #expect(ids == ["0", "1", "2", "3", "4"], "newest first, nothing older than 7 days")
        #expect(Set(days.map { $0.day }).count == days.count)
    }
}

private extension DateComponents {
    func second(_ d: Date, _ cal: Calendar) -> Int { cal.component(.second, from: d) }
}

@Suite struct SetupFlowTests {
    @Test func walksEveryStepInOrder() {
        var f = SetupFlow()
        #expect(f.isFirstRun && f.current == .welcome)
        for step in SetupFlow.Step.allCases {
            #expect(f.current == step)
            f.complete(step)
        }
        #expect(f.current == nil && f.isComplete && !f.needsFinishing)
    }

    @Test func skippingLeavesFinishSetup() {
        var f = SetupFlow()
        f.complete(.welcome)
        f.skip(.follow)
        #expect(f.current == .alerts)
        f.complete(.alerts)
        f.skip(.notifications)
        f.complete(.openAtLogin)
        #expect(f.current == nil && f.needsFinishing)
        #expect(f.unfinished == [.follow, .notifications])
        f.resume()
        #expect(f.current == .follow)
        f.complete(.follow)
        #expect(f.current == .notifications)
        f.complete(.notifications)
        #expect(f.isComplete && !f.needsFinishing)
    }

    @Test func skipNeverUndoesADoneStep() {
        var f = SetupFlow(states: [.welcome: .done, .follow: .done])
        f.open(.follow)
        f.skip(.follow)
        #expect(f.states[.follow] == .done)
    }

    @Test func backGoesToThePreviousStep() {
        var f = SetupFlow()
        f.complete(.welcome)
        f.back()
        #expect(f.current == .welcome)
    }

    @Test func persistsUnderTheSchemaKey() {
        let store = scratchStore()
        var f = SetupFlow(store: store)
        #expect(f.isFirstRun)
        f.complete(.welcome)
        f.skip(.follow)
        f.save(to: store)
        let again = SetupFlow(store: store)
        #expect(again.states == f.states && again.current == .alerts)
        #expect(SettingsSpec.Keys.setupSteps == MacSettings.setupSteps.key)
    }
}

/// #107: setup's alert step has Android's rows (the five levels and no data), each changeable; nothing is stored
/// before Use these, and a changed level applies (this Mac's own rules) even while the wearer's are mirrored.
@Suite struct SetupAlertStepTests {
    static let marta = WearerSettings(
        json: #"{"alerts":{"alerts":{"low":{"on":true,"mgdl":72},"high":{"on":true,"mgdl":200}}}}"#, wearer: "Marta")

    @Test func theRowsAreTheFiveLevelsAndNoDataInUse() {
        let m = SetupModel(settings: scratchSettings(wearer: Self.marta))
        #expect(SetupModel.reviewedKinds == [.veryLow, .low, .high, .veryHigh, .extremelyHigh, .noData])
        #expect(SetupModel.reviewedKinds.map(m.threshold) == [55, 72, 200, 250, 300, 20])
        #expect(m.levelsMirrored && m.thresholdsInOrder)
    }

    @Test func noDataIsThisMacsOwnAndTheWearersLevelsStay() {
        let settings = scratchSettings(wearer: Self.marta)
        let m = SetupModel(settings: settings)
        m.flow.open(.alerts)
        m.setThreshold(30, .noData)
        #expect(m.threshold(.noData) == 30 && settings.alerts.settings[.noData].threshold == 20)  // not stored yet
        m.confirmThresholds()
        #expect(settings.alerts.settings[.noData].threshold == 30)
        #expect(settings.settingsFrom == .wearer && settings.shownAlerts[.low].threshold == 72)
        #expect(m.flow.states[.alerts] == .done && m.editedThresholds.isEmpty)
    }

    @Test func aChangedLevelBecomesThisMacsOwnStartingFromTheWearers() {
        let settings = scratchSettings(wearer: Self.marta)
        let m = SetupModel(settings: settings)
        m.flow.open(.alerts)
        m.setThreshold(80, .low)
        m.confirmThresholds()
        #expect(settings.settingsFrom == .custom && !m.levelsMirrored)
        #expect(settings.shownAlerts[.low].threshold == 80 && settings.shownAlerts[.high].threshold == 200)
        #expect(m.flow.states[.alerts] == .done)
    }

    @Test func levelsOutOfOrderWait() {
        let settings = scratchSettings()
        let m = SetupModel(settings: settings)
        m.flow.open(.alerts)
        m.setThreshold(65, .veryLow)
        m.setThreshold(60, .low)  // below very low: both are values each input offers
        #expect(!m.thresholdsInOrder)
        m.confirmThresholds()
        #expect(m.flow.states[.alerts] == nil && m.flow.current == .alerts && settings.settingsFrom == .wearer)
        m.setThreshold(70, .low)
        #expect(m.thresholdsInOrder)
    }

    @Test func useTheseWithoutAChangeStoresNothing() {
        let settings = scratchSettings()
        let m = SetupModel(settings: settings)
        m.flow.open(.alerts)
        m.setThreshold(70, .low)  // the value in use: no change
        m.confirmThresholds()
        #expect(m.flow.states[.alerts] == .done && settings.settingsFrom == .wearer)
        #expect(settings.store.value(MacSettings.alerts).isEmpty)
    }
}

final class RecordingSender: TreatmentActions {
    var sent: [Treatment] = []
    var accept = true
    func send(_ treatment: Treatment) async -> Bool {
        sent.append(treatment)
        return accept
    }
}

@Suite struct TreatmentsUndoTests {
    static let mine = Treatment(id: "a", k: .rapid, a: 4, t: 1000, u: 1, o: .mac)
    static let phones = Treatment(id: "b", k: .carbs, a: 30, t: 900, u: 1, o: .phone)

    @Test func deleteSendsATombstoneAndUndoBringsItBack() async {
        var clock = Date(timeIntervalSince1970: 2000)
        let sender = RecordingSender()
        let m = TreatmentsModel(
            rows: [.init(treatment: Self.mine, delivery: .accepted)], canWrite: true, actions: sender, now: { clock })
        await m.delete(m.rows[0])
        #expect(m.rows.isEmpty && m.undoAvailable)
        #expect(sender.sent.last?.del == true)
        clock += 5
        await m.undoLast()
        #expect(m.rows.map(\.id) == ["a"] && m.rows[0].delivery == .sending)
        #expect(sender.sent.last?.del == false)
        #expect(sender.sent.last!.u > sender.sent.first!.u)
    }

    @Test func undoEndsAfterTenSeconds() async {
        var clock = Date(timeIntervalSince1970: 2000)
        let sender = RecordingSender()  // the model holds its actions weakly
        let m = TreatmentsModel(
            rows: [.init(treatment: Self.mine, delivery: .accepted)], canWrite: true, actions: sender,
            now: { clock })
        await m.delete(m.rows[0])
        #expect(m.rows.isEmpty)
        clock += TreatmentsModel.undoSeconds + 0.1
        #expect(!m.undoAvailable)
        await m.undoLast()
        #expect(m.rows.isEmpty)
    }

    @Test func editSendsANewerVersionAndUndoTakesItBack() async {
        var clock = Date(timeIntervalSince1970: 2000)
        let sender = RecordingSender()
        let m = TreatmentsModel(
            rows: [.init(treatment: Self.mine, delivery: .accepted)], canWrite: true, actions: sender, now: { clock })
        await m.edit(m.rows[0], amount: 5.5, note: "", at: 1100)
        #expect(m.rows.map(\.treatment.a) == [5.5] && m.rows[0].treatment.t == 1100 && m.rows[0].delivery == .sending)
        #expect(sender.sent.last?.id == "a" && sender.sent.last?.a == 5.5 && sender.sent.last!.u > Self.mine.u)
        #expect(m.undo?.kind == .changed && m.undo?.shown.a == 5.5)
        clock += 3
        await m.undoLast()
        #expect(m.rows[0].treatment.a == 4 && m.rows[0].treatment.t == 1000 && !m.undoAvailable)
        #expect(sender.sent.count == 2 && sender.sent[1].u > sender.sent[0].u && !sender.sent[1].del)
    }

    @Test func anEditWithoutAChangeSendsNothing() async {
        let sender = RecordingSender()
        let m = TreatmentsModel(rows: [.init(treatment: Self.mine, delivery: .sent)], canWrite: true, actions: sender)
        await m.edit(m.rows[0], amount: 4, note: "", at: 1000)
        #expect(sender.sent.isEmpty && m.undo == nil)
    }

    @Test func aChangedEntryStaysListedWhileItsUndoIsOffered() async {
        let sender = RecordingSender()
        let m = TreatmentsModel(rows: [.init(treatment: Self.mine, delivery: .sent)], canWrite: true, actions: sender)
        await m.edit(m.rows[0], amount: 6, note: "", at: 1000)
        var echoed = Self.mine
        echoed.a = 6
        echoed.o = .follower
        m.sync([echoed], deliveries: ["a": .accepted], canWrite: true)
        #expect(m.rows.map(\.treatment.a) == [6] && m.rows[0].delivery == .accepted)
    }

    /// The runtime keeps this Mac's entries as `o: follower` (how every writer sends them): they stay this Mac's to
    /// edit and delete by their delivery state; another follower's entry never is.
    @Test func ownEntriesStayChangeableInTheRuntimesCopy() {
        let m = TreatmentsModel(rows: [], canWrite: true, actions: nil)
        var own = Self.mine
        own.o = .follower
        let other = Treatment(id: "c", k: .carbs, a: 20, t: 950, u: 1, o: .follower)
        m.sync([own, other, Self.phones], deliveries: ["a": .accepted], canWrite: true)
        #expect(m.rows.map { m.canChange($0) } == [true, false, false])
        #expect(TreatmentsView.loggedBy(own, mine: true) == "Logged on this Mac")
        #expect(TreatmentsView.loggedBy(Self.phones, wearer: "Ana") == "Logged on Ana's phone")
        m.sync([own], deliveries: ["a": .accepted], canWrite: false)
        #expect(!m.canChange(m.rows[0]))
    }

    @Test func theEditSheetParsesLikeAddTreatment() {
        #expect(EditTreatmentView.text(of: Self.mine) == "4")
        #expect(EditTreatmentView.edited(Self.mine, text: "4,5")! == (4.5, ""))
        #expect(EditTreatmentView.edited(Self.mine, text: "150") == nil)
        #expect(EditTreatmentView.edited(Self.phones, text: "30.4")! == (30, ""))
        let note = Treatment(id: "n", k: .note, a: 0, t: 1000, n: "Walk", u: 1, o: .mac)
        #expect(EditTreatmentView.edited(note, text: "  Run ")! == (0, "Run"))
        #expect(EditTreatmentView.edited(note, text: " ") == nil)
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        // the picker's OK on the entry's own clock time (three days later) keeps the entry's day
        let old = Treatment(id: "o", k: .rapid, a: 2, t: 1_800_000_000, u: 1, o: .mac)
        #expect(EditTreatmentView.isOwnTime(.at(1_800_000_000 + 3 * 86400), of: old, calendar: utc))
        #expect(!EditTreatmentView.isOwnTime(.at(1_800_000_000 + 60), of: old, calendar: utc))
        #expect(!EditTreatmentView.isOwnTime(.now, of: old, calendar: utc))
    }

    @Test func thePhonesEntriesCantBeChanged() async {
        let sender = RecordingSender()
        let m = TreatmentsModel(rows: [.init(treatment: Self.phones, delivery: nil)], canWrite: true, actions: sender)
        #expect(!m.canChange(m.rows[0]))
        await m.delete(m.rows[0])
        #expect(m.rows.count == 1 && sender.sent.isEmpty)
    }

    @Test func refusedSendShowsFailedAndRetrySendsAgain() async {
        let sender = RecordingSender()
        sender.accept = false
        let m = TreatmentsModel(rows: [], canWrite: true, actions: sender)
        await m.add([Self.mine])
        #expect(m.rows[0].delivery == .failed)
        sender.accept = true
        await m.retry(m.rows[0])
        #expect(m.rows[0].delivery == .sending && sender.sent.count == 2)
    }
}

@Suite struct QRImportTests {
    /// The short invite link of contracts/protocol/identity/vectors.json.
    static let link: String = {
        let root =
            try! JSONSerialization.jsonObject(
                with: Data(contentsOf: Contracts.url("protocol/identity/vectors.json"))) as! [String: Any]
        return (root["invite"] as! [String: String])["short_link"]!
    }()

    @Test func readsTheInviteFromAQRImage() {
        let img = QRImport.image(of: Self.link)!
        #expect(QRImport.inviteText(in: img) == Self.link)
    }

    @Test func aQRCodeThatIsNoInviteIsRefusedWithTheExactText() async {
        #expect(QRImport.inviteText(in: QRImport.image(of: "https://example.com")!) == nil)
        let m = FollowModel(status: .notFollowing, actions: nil, macName: "Mac")
        await m.follow(qrText: nil)
        #expect(m.failure?.message == "No Sukrara invite QR code found in that image.")
    }

    @Test func pastedTextThatIsNoLinkNeverReachesTheRelays() async {
        final class Never: FollowActions {
            var calls = 0
            var names: [String] = []
            func join(link: String) async throws(FollowFailure) { calls += 1 }
            func joinNearby(name: String) async throws(FollowFailure) { calls += 1 }
            func confirmNearby(same: Bool) async -> Bool { false }
            func setProfile(name: String, avatar: Avatar?) async { names.append(name) }
            func setName(_ name: String) async { names.append(name) }
            func cancelJoin() async {}
            func unfollow() async {}
        }
        let a = Never()
        let m = FollowModel(status: .notFollowing, actions: a, macName: "Mac")
        m.linkText = "hello"
        await m.follow()
        #expect(m.failure == .invalidLink && a.calls == 0 && m.status == .notFollowing)
        m.linkText = Self.link
        m.macName = "Ana's MacBook"
        await m.follow()
        #expect(a.calls == 1 && m.failure == nil)
        #expect(a.names == ["Ana's MacBook"])  // the typed name goes with the request
    }

    /// Nearby: asking shows "Asking Marta's phone…"; *Different Code* ends the join and says why; *Same Code* keeps it.
    @Test func nearbyCodesSameOrDifferent() async {
        final class Pairing: FollowActions {
            var confirmed: [Bool] = []
            func join(link: String) async throws(FollowFailure) {}
            func joinNearby(name: String) async throws(FollowFailure) {}
            func confirmNearby(same: Bool) async -> Bool {
                confirmed.append(same)
                return true
            }
            func setProfile(name: String, avatar: Avatar?) async {}
            func setName(_ name: String) async {}
            func cancelJoin() async {}
            func unfollow() async {}
        }
        let a = Pairing()
        let m = FollowModel(status: .notFollowing, actions: a, macName: "Mac")
        m.nearby = NearbyPhone(wearer: "Marta")
        await m.askNearby()
        #expect(m.status == .waiting(wearer: "Marta", pairing: .init(code: nil, confirmed: false)))
        m.status = .waiting(wearer: "Marta", pairing: .init(code: "021472", confirmed: false))
        await m.confirmNearby(same: true)
        #expect(m.failure == nil && a.confirmed == [true])
        await m.confirmNearby(same: false)
        #expect(m.failure == .codesDiffered && m.status == .notFollowing && a.confirmed == [true, false])
        #expect(m.failure?.message.hasPrefix("The codes didn't match") == true)
    }
}

@Suite struct SettingsPanesTests {
    @Test func numbersAreCoercedAndOnlyValidValuesStored() {
        let s = scratchStore()
        #expect(s.number(MacSettings.bandLow) == 70)
        #expect(!s.setNumber(500, for: MacSettings.bandLow))
        #expect(s.setNumber(72, for: MacSettings.bandLow))
        #expect(s.number(MacSettings.bandLow) == 72)
        s.set(9999, for: MacSettings.graphHours.spec)
        #expect(s.number(MacSettings.graphHours) == 48)
    }

    @Test func everyP0MacNumberSettingHasItsSchemaFormat() {
        for n in MacSettings.bands + [MacSettings.graphHours, MacSettings.gridHours, MacSettings.countdownHours] {
            let info = SettingsSpec.settings.first { $0.id == n.id }
            #expect(info != nil, "\(n.id)")
            #expect(
                info.map { "defaults:" + n.spec.key == $0.mac || $0.mac.hasPrefix("defaults:" + n.spec.key) } == true)
            #expect(SettingsSpec.numbers[info?.number ?? ""] == n.format, "\(n.id)")
        }
    }

    @Test func alertSettingsKeepTheLastGoodValueWhenUnreadable() {
        let s = scratchStore()
        let a = AlertSettingsStore(store: s)
        a.update { $0.rules[.low]?.threshold = 75 }
        s.set(Data("not json".utf8), for: MacSettings.alerts)
        a.reload()
        #expect(a.readError && a.settings.on && a.settings[.low].threshold == 75)
        a.resetToDefaults()
        #expect(!a.readError && a.settings[.low].threshold == 70)
    }

    @Test func wearerSettingsMirrorByWirePath() {
        let json =
            #"{"v":2,"type":"settings","u":1,"graph":{"low":65,"defaultHours":999},"alerts":{"alerts":{"low":{"on":false,"mgdl":72}}}}"#
        let w = WearerSettings(json: json, wearer: "Ana")!
        #expect(w.number("graph.band.low") == 65)
        #expect(w.number("graph.hours") == 48)  // coerced to the schema's range
        #expect(w.number("alerts.threshold.low") == 72)
        #expect(w.bool("alerts.kind.on", kind: "low") == false)
        #expect(w.number("graph.band.high") == nil)
        #expect(w.number("alerts.threshold.no_data") == nil)  // not mirrored
    }

    @Test func graphConfigLoadsOwnOrMirroredValues() {
        let s = scratchStore()
        s.setNumber(75, for: MacSettings.bandLow)
        s.setNumber(6, for: MacSettings.graphHours)
        let w = WearerSettings(json: #"{"graph":{"low":65}}"#, wearer: "Ana")
        #expect(GraphConfig.load(from: s, wearer: w).bands.low == 65)  // default settingsFrom = wearer
        s.set(SettingsFrom.custom.rawValue, for: MacSettings.settingsFrom)
        let own = GraphConfig.load(from: s, wearer: w)
        #expect(own.bands.low == 75 && own.hours == 6)
    }

    /// Add Treatment opens centred under the status item, kept 12 pt inside the screen, 6 pt under the menu bar.
    @Test @MainActor func addTreatmentOpensUnderTheStatusItem() {
        let area = NSRect(x: 0, y: 0, width: 1440, height: 875)
        let size = NSSize(width: 400, height: 300)
        let mid = AppWindows.underMenuBar(size: size, anchor: NSRect(x: 900, y: 875, width: 60, height: 24), area: area)
        #expect(mid == NSPoint(x: 730, y: 869))
        let edge = AppWindows.underMenuBar(
            size: size, anchor: NSRect(x: 1400, y: 875, width: 40, height: 24), area: area)
        #expect(edge.x == 1028, "\(edge)")
        #expect(AppWindows.underMenuBar(size: size, anchor: nil, area: area).x == 1028)
    }

    @Test func aPillAsksTheOpenWindowToMoveTheFocus() {
        let f = AddTreatmentFocus()
        f.request(.carbs)
        f.request(.carbs)
        #expect(f.field == .carbs && f.count == 2)  // the same field again still moves the focus back
    }
}
