// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
#if DEBUG
    import CoreGraphics
    import Foundation
    import ImageIO
    import SukraraCore
    import SukraraGraph
    import SukraraTesting
    import UniformTypeIdentifiers

    /// `sukrara-snap card <dir>`: every card surface at its registered sizes (contracts/graph/surfaces/surfaces.json),
    /// light and dark, with synthetic data only (a believable seeded day, a just-taken bolus, a sensor mid-life), plus
    /// the states a person meets: late, stale, no readings, warming up, sensor ended, a low with its badge.
    enum CardSnapshots {
        static let sizes: [(CardSurface, [CGSize])] = [
            (.menubarCard, [CGSize(width: 340, height: 196)]),
            (.widgetValue, [CGSize(width: 138, height: 138), CGSize(width: 332, height: 138)]),
            (
                .widgetGraph,
                [CGSize(width: 138, height: 138), CGSize(width: 332, height: 138), CGSize(width: 332, height: 350)]
            ),
            (
                .widgetCombined,
                [CGSize(width: 332, height: 138), CGSize(width: 332, height: 350), CGSize(width: 714, height: 350)]
            ),
        ]

        static func render(to dir: URL) throws -> Int {
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            let now: TimeInterval = 1_800_000_000
            var count = 0
            for (state, data) in states(now: now) {
                for (surface, sizes) in sizes {
                    for size in sizes {
                        // mmol/L for the states with a value worth comparing; mg/dL for all
                        let units: [GlucoseUnit] = ["live", "low", "stale"].contains(state) ? [.mgdl, .mmol] : [.mgdl]
                        for unit in units {
                            for (dark, dimmed) in looks(surface, state: state, unit: unit) {
                                let name =
                                    "\(surface.rawValue)-\(Int(size.width))x\(Int(size.height))-\(state)-\(unit.rawValue)-"
                                    + "\(dark ? "dark" : "light")\(dimmed ? "-dimmed" : "").png"
                                try write(
                                    surface, size: size, data: data, unit: unit, dark: dark, dimmed: dimmed,
                                    to: dir.appending(path: name))
                                count += 1
                            }
                        }
                    }
                }
            }
            return count
        }

        /// Light and dark; a widget also as the dimmed desktop draws it (an app in front: `DimmedDesktop`, measured on
        /// the real desktop, #189) for the states that matter most at a glance: live, struck through and hidden.
        private static func looks(_ surface: CardSurface, state: String, unit: GlucoseUnit)
            -> [(dark: Bool, dimmed: Bool)]
        {
            let widget = [CardSurface.widgetValue, .widgetGraph, .widgetCombined].contains(surface)
            let dimmed = widget && unit == .mgdl && ["live", "stale", "noreadings"].contains(state)
            return [(false, false), (true, false)] + (dimmed ? [(false, true), (true, true)] : [])
        }

        private static func states(now: TimeInterval) -> [(String, CardData)] {
            let day = RealisticSynthetic.readings(until: now - 60, days: 8)
            let bolus = GraphMark(t: now - 1500, k: "rapid", a: 4.5, id: "i1")
            let meal = GraphMark(t: now - 1320, k: "carbs", a: 45, id: "c1")
            let sensor = SensorInfo(start: now - 6 * 86400, end: now + 8 * 86400, warmupEnd: now - 6 * 86400 + 3600)
            let live = CardAlerts(
                last: LastTreatment.Info(tr: bolus, until: bolus.t + 3600),
                muteEnds: ["i1": bolus.t + 3600, "c1": meal.t + 900])
            func ending(_ age: TimeInterval, _ v: Double, _ rate: Double?) -> [GraphReading] {
                day.filter { $0.t <= now - age - 300 } + [GraphReading(t: now - age, v: v, rate: rate)]
            }
            return [
                ("live", CardData(readings: day, marks: [bolus, meal], sensor: sensor, alerts: live, now: now)),
                ("late", CardData(readings: ending(200, 132, -0.5), sensor: sensor, now: now)),
                ("stale", CardData(readings: ending(600, 141, 2), sensor: sensor, now: now)),
                ("noreadings", CardData(readings: ending(3600, 118, 0.2), sensor: sensor, now: now)),
                ("none", CardData(readings: [], now: now)),
                (
                    "warmup",
                    CardData(
                        readings: [],
                        sensor: SensorInfo(start: now - 1200, end: now + 14 * 86400, warmupEnd: now + 2400), now: now)
                ),
                (
                    "ended",
                    CardData(
                        readings: ending(4200, 120, 0),
                        sensor: SensorInfo(start: now - 15 * 86400, end: now - 3600, warmupEnd: 0), now: now)
                ),
                (
                    // LibreLinkUp's sensor: replaced in the Libre app (contracts/graph/sensor `renew`)
                    "ended-librelinkup",
                    CardData(
                        readings: ending(4200, 120, 0),
                        sensor: SensorInfo(start: now - 15 * 86400, end: now - 3600, warmupEnd: 0, renew: .libreApp),
                        now: now)
                ),
                (
                    "low",
                    CardData(
                        readings: ending(60, 64, -1.2), sensor: sensor, alerts: CardAlerts(status: .needed(.low)),
                        now: now)
                ),
                // out of range without an action pill: the range in words (#26)
                ("lowquiet", CardData(readings: ending(60, 64, -1.2), sensor: sensor, now: now)),
                (
                    "highmuted",
                    CardData(
                        readings: ending(60, 262, 1.4), sensor: sensor,
                        alerts: CardAlerts(status: .paused(.high, until: now + 2700)), now: now)
                ),
            ]
        }

        private static func write(
            _ surface: CardSurface, size: CGSize, data: CardData, unit: GlucoseUnit, dark: Bool, dimmed: Bool,
            to url: URL
        ) throws {
            let widget = [CardSurface.widgetValue, .widgetGraph, .widgetCombined].contains(surface)
            // a widget: its picture as the widget hands it over (the card on its own background, over the margins)
            guard
                let card =
                    widget
                    ? GlucoseCard.widgetImage(
                        surface: surface, size: size, config: GraphConfig(), data: data, unit: unit, dark: dark,
                        tinted: false, scale: 2, overflow: 12)
                    : GlucoseCard.image(
                        surface: surface, size: size, config: GraphConfig(), data: data, unit: unit, dark: dark,
                        overflow: 12),
                let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)
            else { throw SnapError.render(url.lastPathComponent) }
            if dimmed {
                // what the dimmed desktop shows of it (the window; the desktop then tints it with the wallpaper)
                guard
                    let out = DimmedDesktop.image(
                        DimmedDesktop.render(card, on: .solid(GlucoseCard.widgetBackground(dark: dark))))
                else { throw SnapError.render(url.lastPathComponent) }
                CGImageDestinationAddImage(dest, out, nil)
                guard CGImageDestinationFinalize(dest) else { throw SnapError.render(url.lastPathComponent) }
                return
            }
            let w = card.width, h = card.height
            guard
                let ctx = CGContext(
                    data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: 0,
                    space: CGColorSpace(name: CGColorSpace.sRGB)!,
                    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
            else { throw SnapError.render(url.lastPathComponent) }
            let all = CGRect(x: 0, y: 0, width: w, height: h)
            // the menu bar card's own surroundings (a widget's picture brings its background)
            ctx.setFillColor(dark ? CGColor(gray: 0.11, alpha: 1) : CGColor(gray: 0.97, alpha: 1))
            ctx.fill(all)
            ctx.draw(card, in: all)
            guard let out = ctx.makeImage() else { throw SnapError.render(url.lastPathComponent) }
            CGImageDestinationAddImage(dest, out, nil)
            guard CGImageDestinationFinalize(dest) else { throw SnapError.render(url.lastPathComponent) }
        }

        enum SnapError: Error { case render(String) }
    }
#endif
