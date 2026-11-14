// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import CoreGraphics
import Foundation
import SukraraCore
import SukraraTesting
import Testing

@testable import SukraraGraph

/// The desktop widget while the desktop is dimmed (an app in front), drawn the way macOS draws it (`DimmedDesktop`,
/// measured on the real desktop, #189): the value must read clearly and the plot's decoration stay faint, in light and
/// dark, at every widget surface and size.
@Suite struct DimmedWidgetTests {
    private static let now: TimeInterval = 1_800_000_000
    private static let sizes: [(CardSurface, [CGSize])] = [
        (.widgetValue, [CGSize(width: 138, height: 138), CGSize(width: 332, height: 138)]),
        (
            .widgetGraph,
            [
                CGSize(width: 138, height: 138), CGSize(width: 332, height: 138), CGSize(width: 332, height: 350),
                CGSize(width: 714, height: 350),
            ]
        ),
        (
            .widgetCombined,
            [
                CGSize(width: 138, height: 138), CGSize(width: 332, height: 138), CGSize(width: 332, height: 350),
                CGSize(width: 714, height: 350),
            ]
        ),
    ]
    private static let scale: CGFloat = 2, overflow: CGFloat = 12

    /// A seeded day ending in range with a just-taken bolus (the last-treatment slot), ending low, ending high.
    private static func scenes() -> [(String, CardData)] {
        let day = RealisticSynthetic.readings(until: now - 400, days: 8)
        let bolus = GraphMark(t: now - 1500, k: "rapid", a: 4.5, id: "i1")
        let live = CardAlerts(
            last: LastTreatment.Info(tr: bolus, until: bolus.t + 3600), muteEnds: ["i1": bolus.t + 3600])
        func ending(_ v: Double, _ rate: Double) -> [GraphReading] {
            day + [GraphReading(t: now - 60, v: v, rate: rate)]
        }
        return [
            ("in range", CardData(readings: ending(118, 0.2), marks: [bolus], alerts: live, now: now)),
            ("low", CardData(readings: ending(61, -1.2), now: now)),
            ("high", CardData(readings: ending(236, 1.1), now: now)),
        ]
    }

    private static func layout(_ surface: CardSurface, _ size: CGSize, _ data: CardData) -> CardLayout {
        let ctx = CGContext(
            data: nil, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 0,
            space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        let style = surface.style(GraphConfig(), PlaceOverrides(), sensor: data.sensor, words: .en)
        return GlucoseCard.draw(
            ctx, size: size, readings: data.readings, marks: data.marks, now: data.now, style,
            GraphPalette.of(dark: true), surface: surface, part: .all, metricsOf: data.metrics, alerts: data.alerts)
    }

    /// `box` (card points) in the picture's pixels.
    private static func pixels(_ box: CGRect) -> CGRect {
        CGRect(
            x: (box.minX + overflow) * scale, y: (box.minY + overflow) * scale, width: box.width * scale,
            height: box.height * scale)
    }

    private static func percentile(_ v: [Double], _ p: Double) -> Double {
        let s = v.sorted()
        return s.isEmpty ? 1 : s[min(s.count - 1, Int(Double(s.count - 1) * p))]
    }

    /// What the dimmed desktop shows of the widget as it ships: its picture on its container background.
    private static func dimmed(_ surface: CardSurface, _ size: CGSize, _ data: CardData, dark: Bool)
        -> DimmedDesktop.Window?
    {
        guard
            let img = GlucoseCard.widgetImage(
                surface: surface, size: size, config: GraphConfig(), data: data, unit: .mgdl, dark: dark,
                tinted: false, scale: scale, overflow: overflow, words: .en)
        else { return nil }
        return DimmedDesktop.render(img, on: .solid(GlucoseCard.widgetBackground(dark: dark)))
    }

    /// The value (large text: WCAG 3:1, 4.5:1 in range) against the card's background, and the plot's typical pixel
    /// (the grid and the in-range band) close to it.
    private static func check(_ w: DimmedDesktop.Window, _ lay: CardLayout, scene: String, tag: String)
        -> (value: Double?, plot: Double?)
    {
        var value: Double?, plot: Double?
        if let box = lay.first("value.number")?.box {
            let c = w.pixels(in: pixels(box)).map { DimmedDesktop.contrast($0, w.background) }
            value = percentile(c, 0.95)
        }
        if let box = lay.first("chart.plot")?.box {
            let c = w.pixels(in: pixels(box)).map { DimmedDesktop.contrast($0, w.background) }
            plot = percentile(c, 0.5)
        }
        return (value, plot)
    }

    @Test func valueReadsAndPlotStaysFaintWhenDimmed() throws {
        var values = 0, plots = 0
        for (scene, data) in Self.scenes() {
            for (surface, sizes) in Self.sizes {
                for size in sizes {
                    let lay = Self.layout(surface, size, data)
                    for dark in [true, false] {
                        let tag =
                            "\(surface.rawValue) \(Int(size.width))x\(Int(size.height)) \(scene) "
                            + (dark ? "dark" : "light")
                        let w = try #require(Self.dimmed(surface, size, data, dark: dark), "\(tag)")
                        let (value, plot) = Self.check(w, lay, scene: scene, tag: tag)
                        if let value {
                            values += 1
                            #expect(value >= (scene == "in range" ? 4.5 : 3), "\(tag): the value at \(value):1")
                        }
                        if let plot {
                            plots += 1
                            #expect(plot <= 1.5, "\(tag): the plot's decoration at \(plot):1")
                        }
                    }
                }
            }
        }
        #expect(values > 0 && plots > 0, "no value (\(values)) or plot (\(plots)) laid out")
    }

    /// The model reproduces the owner's screenshot from the widget before #189 was fixed: a dark-appearance card with
    /// a transparent background on `.fill.tertiary`, its value washed out, the plot's decoration as strong as the value.
    @Test func modelReproducesTheWashedOutWidget() throws {
        let data = Self.scenes()[0].1, size = CGSize(width: 714, height: 350), surface = CardSurface.widgetCombined
        let img = try #require(
            GlucoseCard.image(
                surface: surface, size: size, config: GraphConfig(), data: data, unit: .mgdl, dark: true,
                scale: Self.scale, overflow: Self.overflow, words: .en))
        let w = DimmedDesktop.render(img, on: .fillTertiary)
        let (value, plot) = Self.check(w, Self.layout(surface, size, data), scene: "in range", tag: "before")
        #expect(try #require(value) < 3)
        #expect(try #require(plot) > 1.5)
    }
}
