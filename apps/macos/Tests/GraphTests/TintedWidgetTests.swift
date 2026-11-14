// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import CoreGraphics
import Foundation
import SukraraCore
import Testing

@testable import SukraraGraph

/// A widget in a tinted rendering mode (desktop not focused: accented / vibrant, #189). The system draws it on a
/// material that is light or dark by the wallpaper, not by the system's appearance, and fills the card's mask with
/// its primary ink for that material. So the card must be one mask, the same in light and dark, with no colour of its
/// own; and drawn as the system draws it, on either material, the value must read strongly while the decoration stays
/// faint. Before #189 a dark-theme widget drew its light inks into the bitmap: on the light material they vanished.
@Suite struct TintedWidgetTests {
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

    /// A seeded day with a just-taken bolus (the last-treatment slot), and a low (the range word, a red value).
    private static func scenes() -> [(String, CardData)] {
        let day = RealisticSynthetic.readings(until: now - 60, days: 8)
        let bolus = GraphMark(t: now - 1500, k: "rapid", a: 4.5, id: "i1")
        let live = CardAlerts(
            last: LastTreatment.Info(tr: bolus, until: bolus.t + 3600), muteEnds: ["i1": bolus.t + 3600])
        let low = day.filter { $0.t <= now - 400 } + [GraphReading(t: now - 60, v: 61, rate: -1.2)]
        return [
            ("live", CardData(readings: day, marks: [bolus], alerts: live, now: now)),
            ("low", CardData(readings: low, now: now)),
        ]
    }

    private struct Pixels {
        var w: Int, h: Int, px: [UInt8]
        init(_ img: CGImage) {
            w = img.width; h = img.height
            px = [UInt8](repeating: 0, count: w * h * 4)
            px.withUnsafeMutableBytes { buf in
                let ctx = CGContext(
                    data: buf.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                    space: CGColorSpace(name: CGColorSpace.sRGB)!,
                    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
                ctx.draw(img, in: CGRect(x: 0, y: 0, width: w, height: h))
            }
        }
        /// Coverage (0…1) of the mask in `box` (card points, y-down), every pixel.
        func coverage(in box: CGRect) -> [Double] {
            let r = box.offsetBy(dx: TintedWidgetTests.overflow, dy: TintedWidgetTests.overflow)
            let s = TintedWidgetTests.scale
            let x0 = max(0, Int(r.minX * s)), x1 = min(w, Int(r.maxX * s))
            let y0 = max(0, Int(r.minY * s)), y1 = min(h, Int(r.maxY * s))
            guard x0 < x1, y0 < y1 else { return [] }
            return (y0..<y1).flatMap { y in (x0..<x1).map { x in Double(px[(y * w + x) * 4 + 3]) / 255 } }
        }
    }

    private static func mask(_ surface: CardSurface, _ size: CGSize, _ data: CardData, dark: Bool) -> CGImage? {
        GlucoseCard.image(
            surface: surface, size: size, config: GraphConfig(), data: data, unit: .mgdl, dark: dark, tinted: true,
            scale: scale, overflow: overflow, words: .en)
    }

    private static func layout(_ surface: CardSurface, _ size: CGSize, _ data: CardData) -> CardLayout {
        let ctx = CGContext(
            data: nil, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 0,
            space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        let style = surface.style(GraphConfig(), PlaceOverrides(), sensor: data.sensor, words: .en)
        return GlucoseCard.draw(
            ctx, size: size, readings: data.readings, marks: data.marks, now: data.now, style,
            GraphPalette.of(dark: true).tinted(), surface: surface, part: .all, metricsOf: data.metrics,
            alerts: data.alerts)
    }

    /// WCAG contrast of ink at `coverage` over the material (sRGB greys).
    private static func contrast(coverage a: Double, ink: Double, material: Double) -> Double {
        func lin(_ c: Double) -> Double { c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4) }
        let shown = material + (ink - material) * a
        let (l1, l2) = (lin(shown) + 0.05, lin(material) + 0.05)
        return max(l1, l2) / min(l1, l2)
    }

    /// The material behind a tinted widget and the system's primary ink on it: light and dark.
    private static let materials: [(name: String, material: Double, ink: Double)] = [
        ("light", 0.82, 0), ("dark", 0.2, 1),
    ]

    @Test func tintedCardIsOneMaskTheSameInLightAndDark() throws {
        for (scene, data) in Self.scenes() {
            for (surface, sizes) in Self.sizes {
                for size in sizes {
                    let tag = "\(surface.rawValue) \(Int(size.width))x\(Int(size.height)) \(scene)"
                    let dark = try #require(Self.mask(surface, size, data, dark: true), "\(tag)")
                    let light = try #require(Self.mask(surface, size, data, dark: false), "\(tag)")
                    let d = Pixels(dark), l = Pixels(light)
                    #expect(d.px == l.px, "\(tag): the tinted card differs between light and dark")
                    // no colour of its own: premultiplied white, so only the opacity reaches the system
                    var coloured = 0
                    for i in stride(from: 0, to: d.px.count, by: 4)
                    where d.px[i] != d.px[i + 3] || d.px[i + 1] != d.px[i + 3] || d.px[i + 2] != d.px[i + 3] {
                        coloured += 1
                    }
                    #expect(coloured == 0, "\(tag): \(coloured) pixels carry a colour")
                    #expect(d.px.contains { $0 > 0 }, "\(tag): empty")
                }
            }
        }
    }

    @Test func valueReadsAndDecorationStaysFaintOnEitherMaterial() throws {
        var values = 0, plots = 0
        for (scene, data) in Self.scenes() {
            for (surface, sizes) in Self.sizes {
                for size in sizes {
                    let tag = "\(surface.rawValue) \(Int(size.width))x\(Int(size.height)) \(scene)"
                    let img = try #require(Self.mask(surface, size, data, dark: true), "\(tag)")
                    let px = Pixels(img), lay = Self.layout(surface, size, data)
                    if let value = lay.first("value.number")?.box {
                        values += 1
                        let strongest = px.coverage(in: value).max() ?? 0
                        for m in Self.materials {
                            let c = Self.contrast(coverage: strongest, ink: m.ink, material: m.material)
                            #expect(c >= 4.5, "\(tag) on the \(m.name) material: the value at \(c):1")
                        }
                    }
                    if let plot = lay.first("chart.plot")?.box {
                        // the grid and the in-range band: the plot's typical pixel stays well below the ink
                        plots += 1
                        let cov = px.coverage(in: plot).sorted()
                        let median = cov.isEmpty ? 0 : cov[cov.count / 2]
                        #expect(median <= 0.2, "\(tag): the plot's decoration at \(median)")
                    }
                }
            }
        }
        #expect(values > 0 && plots > 0, "no value (\(values)) or plot (\(plots)) laid out")
    }
}
