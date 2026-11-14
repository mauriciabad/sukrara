// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraAlerts
import SukraraCore
import SukraraGraph
import SukraraShare
import SukraraTesting
import Testing
import UserNotifications

@testable import SukraraApp

/// #195 (SPEC §1 "Showing an alert") on the Mac: an alert up shows the newest reading without sound once it moved
/// 5 mg/dL (never a wiggle, never within 10 s of the ring) or turned late / stale, waking for those steps; one the
/// person dismissed or clicked stays away until its next ring; a reading back out of range shows the episode's alert
/// again silently, a milder kind replaces a worse one silently; a follower shows the wearer's `show` silently unless
/// its own Mute quiets the side. Android: `AlertLiveValueTest`, `AlertLiveNotificationTest`.
@Suite struct AlertLiveValueTests {
    let clock = FakeClock()
    let store = MemoryAlertStore()
    let notifier = RecordingNotifier()
    let wake = RecordingWake()

    func controller(_ settings: AlertSettings = AlertSettings()) -> AlertController {
        AlertController(settings: settings, clock: clock, store: store, notifier: notifier, wake: wake)
    }

    /// A reading taken now (no rate: the ring for its trend).
    func reading(_ mgdl: Int) -> Reading { Reading(t: clock.now, mgdl: mgdl, rate: nil) }

    func look(_ mgdl: Double, rate: Double? = -1.5, age: TimeInterval = 0) -> AlertLiveValue.Look {
        AlertLiveValue.look(
            GlucoseSample(at: clock.now.addingTimeInterval(-age), mgdl: mgdl, rate: rate), now: clock.now)
    }

    // MARK: the rule

    @Test func theThresholdIs5MgdlAndTheGuardAfterARing10s() {
        #expect(AlertLiveValue.minMoveMgdl == 5)
        #expect(AlertLiveValue.afterRing == 10)
    }

    @Test func aMoveUnder5ChangesNothingAMoveOf5Does() {
        #expect(!AlertLiveValue.changed(look(70), look(74)))
        #expect(!AlertLiveValue.changed(look(70), look(66)))
        #expect(AlertLiveValue.changed(look(70), look(75)))
        #expect(AlertLiveValue.changed(look(70), look(65)))
    }

    @Test func aNewArrowOrFreshnessStepChangesIt() {
        #expect(AlertLiveValue.changed(look(70, rate: -1.5), look(70, rate: -2.5)))
        #expect(!AlertLiveValue.changed(look(70, rate: -1.2), look(71, rate: -1.8)))
        #expect(AlertLiveValue.changed(look(70, age: 30), look(70, age: 120)))
        #expect(AlertLiveValue.changed(look(70, age: 120), look(70, age: 400)))
        #expect(look(70, age: 400).stale)
    }

    @Test func neverWithin10sOfTheRing() {
        let now = clock.now
        #expect(!AlertLiveValue.due(look(70), look(80), rangAt: now.addingTimeInterval(-9), now: now))
        #expect(AlertLiveValue.due(look(70), look(80), rangAt: now.addingTimeInterval(-10), now: now))
        #expect(!AlertLiveValue.due(look(70), look(72), rangAt: nil, now: now))
    }

    /// #195 review: the sensor's LO / HI is said in words ("LO · below 40"): a number after it is news, however close.
    @Test func leavingOrReachingLoOrHiChangesItWithoutARateToo() {
        #expect(AlertLiveValue.changed(look(39, rate: nil), look(43, rate: nil)))
        #expect(AlertLiveValue.changed(look(43, rate: nil), look(39, rate: nil)))
        #expect(AlertLiveValue.changed(look(401, rate: nil), look(398, rate: nil)))
        #expect(!AlertLiveValue.changed(look(39, rate: nil), look(39, rate: nil)))
    }

    /// #195 review: the clock set back after a ring (the ring now looks in the future) never holds the updates back.
    @Test func aRingInTheFutureAfterTheClockWentBackDoesntHoldUpdates() {
        #expect(AlertLiveValue.due(look(70), look(80), rangAt: clock.now.addingTimeInterval(3600), now: clock.now))
    }

    /// The registered glyph surface `alert-notification`: the card's look, asked as itself.
    @Test func theLookIsTheCardsAskedAsTheAlertNotificationSurface() {
        let r = GlucoseSample(at: clock.now.addingTimeInterval(-400), mgdl: 70, rate: -2.5)
        let data = CardData(
            readings: [GraphReading(t: r.at.timeIntervalSince1970, v: 70, rate: -2.5)],
            now: clock.now.timeIntervalSince1970)
        #expect(AlertText.look(r, now: clock.now) == GlucoseCard.face(data, unit: .mgdl, surface: .alertNotification))
        #expect(AlertText.look(r, now: clock.now).strike)
    }

    // MARK: the controller

    @Test func theValueFollowsTheReadingsSilentlyOnceItMoved5() {
        let c = controller()
        c.reading(reading(65))
        #expect(notifier.values == [65])
        clock.advance(by: 60)
        c.reading(reading(62))
        #expect(notifier.values == [65], "a 3 mg/dL wiggle: left as it is")
        clock.advance(by: 60)
        c.reading(reading(60))
        #expect(notifier.values == [65, 60])
        #expect(notifier.posts.last == .post(.low, id: idOfFirst(), sound: false, category: lastCategory()))
    }

    @Test func noUpdateWithin10sOfTheRingItComesWhenTheGuardEnds() {
        let c = controller()
        c.reading(reading(65))
        let rang = clock.now
        clock.advance(by: 5)
        c.reading(reading(58))
        #expect(notifier.values == [65])
        #expect(wake.at == rang.addingTimeInterval(10), "it wakes when the guard ends")
        clock.advance(by: 5)
        c.tick()
        #expect(notifier.values == [65, 58])
    }

    @Test func aLateReadingIsPostedAgainAtItsFreshnessStepsWakingForThem() {
        let c = controller()
        c.reading(reading(65))
        let t0 = clock.now
        #expect(wake.at == t0.addingTimeInterval(91), "it wakes at the value's next step")
        clock.advance(by: 91)
        c.tick()
        #expect(notifier.values == [65, 65])
        #expect(soundOfLastPost() == false)
        clock.advance(by: 210)
        c.tick()
        #expect(notifier.values == [65, 65, 65], "stale: the ring now")
    }

    @Test func dismissedOrClickedItStaysAwayUntilItsNextRing() {
        let c = controller()
        c.reading(reading(65))
        c.dismissed(.low, firing: idOfFirst())
        clock.advance(by: 60)
        c.reading(reading(58))
        #expect(notifier.values == [65], "an update never brings it back")
        clock.advance(by: 240)  // the low's 5-min repeat rings
        c.reading(reading(57))
        #expect(notifier.values == [65, 57])
        #expect(soundOfLastPost() == true)
    }

    @Test func aDismissalOfAnotherNotificationChangesNothing() {
        let c = controller()
        c.reading(reading(65))
        c.dismissed(.low, firing: "another")
        clock.advance(by: 60)
        c.reading(reading(58))
        #expect(notifier.values == [65, 58])
    }

    @Test func lowAgainAfterAMomentInRangeIsShownAtOnceWithoutSound() {
        let c = controller()
        c.reading(reading(65))
        clock.advance(by: 60)
        c.reading(reading(75))
        #expect(notifier.calls.last == .clear(.level))
        clock.advance(by: 60)
        c.reading(reading(66))
        #expect(notifier.values == [65, 66])
        #expect(soundOfLastPost() == false)
    }

    @Test func dismissedTheSilentShowAfterABounceWaitsForTheRing() {
        let c = controller()
        c.reading(reading(65))
        c.dismissed(.low, firing: idOfFirst())
        clock.advance(by: 60)
        c.reading(reading(75))
        clock.advance(by: 60)
        c.reading(reading(66))
        #expect(notifier.values == [65])
    }

    /// #193 + #195: a rising low's quiet takes the alert away; stalled while still low (repeat once: nothing would ring
    /// again), it is back at once without sound.
    @Test func aRisingLowThatStallsIsShownAgainSilentlyAlsoWithRepeatOnce() {
        var s = AlertSettings()
        s.rules[.low]?.repeatMin = 0
        let c = controller(s)
        for v in [58, 59, 60, 61] {
            c.reading(reading(v))
            clock.advance(by: 60)
        }
        #expect(notifier.calls.last == .clear(.level), "rising back into range: quiet")
        c.reading(reading(61))
        clock.advance(by: 60)
        c.reading(reading(61))
        #expect(notifier.values == [58, 61])
        #expect(soundOfLastPost() == false)
    }

    @Test func veryLowGettingBetterIsReplacedByTheLowSilently() {
        let c = controller()
        c.reading(reading(56))
        clock.advance(by: 60)
        c.reading(reading(55))
        clock.advance(by: 60)
        c.reading(reading(56))
        guard case .post(.low, _, false, _)? = notifier.posts.last else {
            Issue.record("no silent low: \(notifier.calls)")
            return
        }
        #expect(notifier.calls.contains(.clear(.level)))
    }

    @Test func aFollowerShowsTheWearersShowSilentlyUnlessItsMuteQuietsIt() throws {
        let c = controller()
        c.setMirroring(true)
        c.wearerAlert(try payload(state: "show", id: "s1"))
        #expect(notifier.posts == [.post(.low, id: "s1", sound: false, category: lastCategory())])
        c.perform(.mute(.low, minutes: 30))
        notifier.calls = []
        c.wearerAlert(try payload(state: "show", id: "s2"))
        #expect(notifier.posts.isEmpty)
    }

    @Test func theShowStateIsOnTheWire() throws {
        let p = try payload(state: "show", id: "s1")
        #expect(p.shows && !p.fires && !p.answered)
        #expect(try !payload(state: "fire", id: "f1").shows)
    }

    @Test func anAlertsDismissalReachesTheAppAndASilentPostIsNoBanner() {
        for cat in Notifier.categories(AlertText(language: .en)) {
            let muted = cat.identifier.hasPrefix(AlertNotificationIDs.mutedCategory)
            #expect(cat.options.contains(.customDismissAction) == !muted, "\(cat.identifier)")
        }
    }

    // MARK: helpers

    func idOfFirst() -> String {
        guard case .post(_, let id, _, _)? = notifier.posts.first else { return "" }
        return id
    }

    func lastCategory() -> String {
        guard case .post(_, _, _, let category)? = notifier.posts.last else { return "" }
        return category
    }

    func soundOfLastPost() -> Bool? {
        guard case .post(_, _, let sound, _)? = notifier.posts.last else { return nil }
        return sound
    }

    func payload(state: String, id: String) throws -> AlertPayload {
        let at = Int(clock.now.timeIntervalSince1970)
        let json =
            #"{"v":2,"type":"alert","k":"low","state":"\#(state)","id":"\#(id)","at":\#(at),"t":\#(at),"sgv":66,"th":70,"n":1}"#
        return try AlertPayload.decode(json)
    }
}
