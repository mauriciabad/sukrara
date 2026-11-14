// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import CoreGraphics
import Foundation
import SukraraCore
import SukraraShare
import SukraraTesting
import Testing

@testable import SukraraApp
@testable import SukraraGraph

/// A follower with a month of one-minute readings (Libre 3 through Juggluco: 44 640), its last 24 hours live.
actor MonthFollower: FollowerService {
    let history: [Reading]
    let snapshot: FollowerSnapshot

    init(now: Date) {
        let t = now.timeIntervalSince1970
        history = (0..<(31 * 1440)).map { i in
            let at = t - Double(31 * 1440 - i) * 60
            return Reading(t: Date(timeIntervalSince1970: at), mgdl: 120 + Int(50 * sin(Double(i) / 90)))
        }
        var s = FollowerSnapshot.empty
        s.link = .following(wearer: "Ana", canWrite: false)
        s.readings = history.filter { $0.t.timeIntervalSince1970 >= t - 86400 }
        snapshot = s
    }

    func snapshots() async -> AsyncStream<FollowerSnapshot> { AsyncStream { _ in } }
    func alerts() async -> AsyncStream<AlertPayload> { AsyncStream { _ in } }
    func start() async {}
    func stop() async {}
    func reconnect() async {}
    func lookUp(link: String) async throws(FollowerJoinError) -> Invite { throw .alreadyFollowing }
    func join(invite: Invite) async throws(FollowerJoinError) { throw .alreadyFollowing }
    func cancelJoin() async {}
    func joinNearby(_ offer: NearbyOffer) async throws(FollowerJoinError) { throw .alreadyFollowing }
    func confirmNearby(same: Bool) async -> Bool { false }
    func searchNearby(_ on: Bool) async {}
    func setProfile(name: String, avatar: Avatar?) async {}
    func readings(since: Date) async -> [Reading] { history.filter { $0.t >= since } }
    func unfollow() async {}
    func send(_ treatment: Treatment) async -> TreatmentSendResult { .notAllowed }
}

/// The panel's chart while it scrolls (#181, 2026-11-14: "the scroll in the graph is choppy (low fps)"): a month of
/// one-minute readings and a scripted two-finger scroll and pinch, each step doing what `PanelCard` does for it (the
/// panel's data, the y range it glides to, one picture). Before: three full drawings per step (the picture, the
/// plot's rects, the pill's rect), every ring counted again over the month in each, the month converted to the card's
/// readings on every look, and the month's trends worked out on the main thread as the panel opened. The median a
/// step takes is printed (the numbers in the report).
@Suite(.serialized) struct PanelFrameTests {
    /// A model following a wearer with a month of history, loaded the way the panel's opening loads it.
    private func month() async throws -> (AppModel, MonthFollower) {
        let clock = FakeClock()
        let follower = MonthFollower(now: clock.now)
        let plan = LaunchPlan(mode: .normal, services: .none, data: .user(home: URL(fileURLWithPath: "/Users/u")))
        let model = AppModel(plan: plan, clock: clock, follower: follower)
        model.apply(follower.snapshot)
        for _ in 0..<500 where model.panelData.readings.count < 40_000 {
            try await Task.sleep(for: .milliseconds(20))
        }
        try #require(model.panelData.readings.count > 40_000)
        return (model, follower)
    }

    @Test func aScrollStepDrawsTheCardOnce() async throws {
        let (model, _) = try await month()
        var scroll = ChartScroll()
        var times: [Double] = []
        for i in 0..<60 {
            let start = Date()
            let data = model.panelData
            let def = GlucoseCard.chartSpan(surface: .menubarCard, config: model.graph) ?? 12 * 3600
            let first = data.readings.first?.t ?? data.now
            if i % 2 == 0 {
                scroll.by(-20 * scroll.span(def) / 260, minEnd: first + scroll.span(def), now: data.now)
            } else {
                scroll.zoom(1.05, anchor: 0.5, default: def, first: first, now: data.now)
            }
            let y = GlucoseCard.yRange(
                surface: .menubarCard, config: model.graph, readings: data.readings, end: scroll.end ?? data.now,
                span: scroll.span)
            var ease = ChartEase()
            ease.retarget(y: y, away: scroll.away, at: 0, instant: true)
            let picture = GlucoseCard.picture(
                surface: .menubarCard, size: PanelView.cardSize, config: model.graph, data: data, unit: .mgdl,
                dark: false, scale: 2, view: ease.view(scroll, default: def, now: data.now, at: 0))
            times.append(Date().timeIntervalSince(start) * 1000)
            #expect(picture.image != nil && picture.plot != nil && (picture.now != nil) == scroll.away)
        }
        let sorted = times.sorted()
        print(
            "panel scroll step: median \(String(format: "%.1f", sorted[sorted.count / 2])) ms, "
                + "p90 \(String(format: "%.1f", sorted[sorted.count * 9 / 10])) ms")
    }

    /// The rings the panel draws over the month are the slots counted once: the very numbers counting again gives.
    @Test func theRingsAreCountedOnceWithTheSameNumbers() async throws {
        let (model, _) = try await month()
        let data = model.panelData
        let bands = model.graph.bands.sane()
        for spec in model.graph.slots + (model.graph.lastElse.map { [$0] } ?? []) {
            let fresh = GlucoseMetrics.of(data.readings, from: data.now - spec.window, to: data.now, bands: bands)
            #expect(data.slots?.metrics(spec.window, bands) == fresh, "\(spec.window)")
            #expect(data.metrics(spec, bands) == fresh, "\(spec.window)")
        }
    }

    /// The month's trends, worked out off the main thread, are the ones THE function gives (contracts/trend), also
    /// after the *Trend arrow* setting changes.
    @Test func theHistorysTrendsAreTheSame() async throws {
        let (model, follower) = try await month()
        let want = TrendRate.annotate(await follower.history, setting: model.graph.trend)
        #expect(model.history.map(\.trend) == want.map(\.trend))
        var config = model.graph
        config.trend = config.trend == .quick ? .smooth : .quick
        model.graph = config
        let again = TrendRate.annotate(await follower.history, setting: config.trend)
        for _ in 0..<500 where model.history.map(\.trend) != again.map(\.trend) {
            try await Task.sleep(for: .milliseconds(20))
        }
        #expect(model.history.map(\.trend) == again.map(\.trend))
        #expect(model.history.map(\.trend) != want.map(\.trend))
    }
}
