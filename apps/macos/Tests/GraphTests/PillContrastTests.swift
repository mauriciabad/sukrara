// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import CoreGraphics
import Foundation
import Testing

@testable import SukraraGraph

/// WCAG 1.4.3: the words on the card's pills reach 4.5:1 in both themes (Android: PillContrastTest). The person reads
/// "Eat carbs" during a low.
struct PillContrastTests {
    private func rgb(_ c: CGColor) -> [CGFloat] { Array((c.components ?? [0, 0, 0]).prefix(3)) }

    private func luminance(_ c: [CGFloat]) -> CGFloat {
        let lin = c.map { $0 <= 0.03928 ? $0 / 12.92 : pow(($0 + 0.055) / 1.055, 2.4) }
        return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]
    }

    private func ratio(_ a: [CGFloat], _ b: [CGFloat]) -> CGFloat {
        let (x, y) = (luminance(a) + 0.05, luminance(b) + 0.05)
        return max(x, y) / min(x, y)
    }

    /// `top` (with its alpha) over the opaque `ground`.
    private func over(_ top: CGColor, _ ground: [CGFloat]) -> [CGFloat] {
        let a = top.alpha
        return zip(rgb(top), ground).map { $0 * a + $1 * (1 - a) }
    }

    @Test(arguments: [false, true])
    func pillWordsReachAA(dark: Bool) {
        let card = rgb(dark ? GraphPalette.darkPalette.bg : GraphPalette.lightPalette.bg)
        for k in ["rapid", "long", "carbs", "note"] {
            let c = TreatmentMarks.color(k, dark: dark)
            let filled = ratio(rgb(TreatmentMarks.onColor(dark: dark)), rgb(c))
            #expect(filled >= 4.5, "\(k) filled pill dark=\(dark): \(filled)")
            let tint = over(c.copy(alpha: (dark ? 0x3D : 0x2E) / 255.0)!, card)
            let tonal = ratio(rgb(TreatmentMarks.tonalInk(k, dark: dark)), tint)
            #expect(tonal >= 4.5, "\(k) tonal pill dark=\(dark): \(tonal)")
        }
    }

    /// Every pill on every ground it can sit on (`PillContrast`; Android PillContrastTest): the palette's surface (nil),
    /// each range colour of a colourised card, its stale grey, white and black. A filled pill's fill reaches 3:1 there,
    /// its words 4.5:1 on what the fill shows; a tonal pill's and the range word's words 4.5:1 on their tint.
    @Test(arguments: [false, true])
    func everyPillStaysLegibleOnItsGround(dark: Bool) {
        let light = GraphPalette.lightPalette
        let pal = GraphPalette.of(dark: dark)
        let grounds: [CGColor?] =
            [nil, GraphPalette.hex(0x8E9099), GraphPalette.hex(0xFFFFFF), GraphPalette.hex(0x000000)]
            + [light.veryLow, light.low, light.tight, light.target, light.high, light.veryHigh, light.extremelyHigh]
        let looks = ["carbs", "rapid", "muted", "sensor", "now"].flatMap { k in
            [false, true].map { StatusBadge.Look(kind: k, text: "x", short: "x", tonal: $0) }
        }
        for ground in grounds {
            let g = ground ?? pal.bg
            for look in looks {
                let (fill, fg) = StatusBadge.colors(look, dark: dark, knockout: false, ground: ground)
                let shown = PillContrast.over(fill, g)
                let swapped = fill === PillContrast.black || fill === PillContrast.white
                if !look.tonal || swapped {
                    #expect(PillContrast.ratio(shown, g) >= 3, "\(look.kind) tonal=\(look.tonal) fill dark=\(dark)")
                }
                #expect(PillContrast.ratio(fg, shown) >= 4.5, "\(look.kind) tonal=\(look.tonal) words dark=\(dark)")
            }
            for tint in [pal.veryLow, pal.low, pal.high, pal.veryHigh, pal.extremelyHigh] {
                let (fill, fg) = RangeTag.colors(tint: tint, pal, ground: ground)
                #expect(PillContrast.ratio(fg, PillContrast.over(fill, g)) >= 4.5, "range word dark=\(dark)")
            }
        }
    }

    @Test func aMutedPillOnTheRedCardTurnsToInk() {
        let look = StatusBadge.Look(kind: "muted", text: "Muted", short: "Muted", tonal: true)
        let (fill, fg) = StatusBadge.colors(look, dark: false, knockout: false, ground: GraphPalette.lightPalette.low)
        #expect(fill === PillContrast.black || fill === PillContrast.white)
        #expect(PillContrast.ratio(fg, fill) >= 4.5)
    }
}
