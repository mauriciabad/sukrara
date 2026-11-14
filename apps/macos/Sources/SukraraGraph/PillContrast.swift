// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import CoreGraphics
import Foundation

/// THE contrast rule of every pill the card draws (the status pill, the range word), on whatever it sits on
/// (`CardStyle.ground`: a surface that paints the card itself; else the palette's own surface): a filled pill's fill
/// reaches `boundary` against the ground (WCAG 1.4.11), and its words and icon reach `text` against what the fill shows
/// over the ground (WCAG 1.4.3). A tonal pill and the range word are faint tints whose words carry them: only the words
/// count. A pill that fails is drawn in the ground's ink (black or white, whichever stands out more) with the other ink
/// for its words. Android: `PillContrast.kt`, the same rule and numbers.
enum PillContrast {
    /// WCAG 1.4.11: a filled pill against the ground.
    static let boundary: CGFloat = 3
    /// WCAG 1.4.3: the words against the pill.
    static let text: CGFloat = 4.5

    static let black = CGColor(srgbRed: 0, green: 0, blue: 0, alpha: 1)
    static let white = CGColor(srgbRed: 1, green: 1, blue: 1, alpha: 1)

    /// A pill's colours after the rule: `fill` (may be translucent), `fg` the words and icon; `swapped` = in ink.
    struct Colors {
        var fill: CGColor
        var fg: CGColor
        var swapped: Bool
    }

    private static let srgb = CGColorSpace(name: CGColorSpace.sRGB)!

    /// `c` as sRGB red, green, blue, alpha.
    static func rgba(_ c: CGColor) -> [CGFloat] {
        let s = c.converted(to: srgb, intent: .defaultIntent, options: nil) ?? c
        let comps = s.components ?? [0, 0, 0, 1]
        if comps.count >= 4 { return Array(comps.prefix(4)) }
        let g = comps.first ?? 0
        return [g, g, g, comps.count > 1 ? comps[1] : 1]
    }

    private static func luminance(_ c: [CGFloat]) -> CGFloat {
        let lin = c.prefix(3).map { $0 <= 0.03928 ? $0 / 12.92 : pow(($0 + 0.055) / 1.055, 2.4) }
        return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]
    }

    /// The contrast of two colours, both taken as opaque.
    static func ratio(_ a: CGColor, _ b: CGColor) -> CGFloat {
        let (x, y) = (luminance(rgba(a)) + 0.05, luminance(rgba(b)) + 0.05)
        return max(x, y) / min(x, y)
    }

    /// `top` (with its alpha) over the opaque `ground`.
    static func over(_ top: CGColor, _ ground: CGColor) -> CGColor {
        let t = rgba(top), g = rgba(ground), a = t[3]
        let c = zip(t.prefix(3), g.prefix(3)).map { $0 * a + $1 * (1 - a) }
        return CGColor(srgbRed: c[0], green: c[1], blue: c[2], alpha: 1)
    }

    /// Black or white, whichever reads better on `ground`.
    static func ink(_ ground: CGColor) -> CGColor { ratio(black, ground) >= ratio(white, ground) ? black : white }

    /// `fill` (its alpha over `ground`) with `fg` on it, kept when the pill passes (`boundary`: a filled pill, its fill
    /// counts too), else the ground's ink and the other ink.
    static func of(fill: CGColor, fg: CGColor, ground: CGColor, boundary filled: Bool) -> Colors {
        let shown = over(fill, ground)
        if (!filled || ratio(shown, ground) >= boundary) && ratio(fg, shown) >= text {
            return Colors(fill: fill, fg: fg, swapped: false)
        }
        let i = ink(ground)
        return Colors(fill: i, fg: i === black ? white : black, swapped: true)
    }
}
