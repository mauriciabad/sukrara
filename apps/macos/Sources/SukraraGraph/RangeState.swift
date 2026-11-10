// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import CoreGraphics
import CoreText
import Foundation

/// A value's range in words, so no place says it by colour alone (#26, WCAG 1.4.1: low red and very high orange look
/// alike to protan / deutan eyes). The raw value is the contracts/graph/range fixtures' key; `words` what the card draws.
/// Extremely high from its own bound up, like the value's colour (RangeState.kt on Android is the same rule).
public enum RangeState: String, CaseIterable, Sendable {
    case veryLow = "very_low"
    case low
    case high
    case veryHigh = "very_high"
    case extremelyHigh = "extremely_high"

    /// The card's words (English, like the rest of the drawn card).
    public var words: String {
        switch self {
        case .veryLow: "Very low"
        case .low: "Low"
        case .high: "High"
        case .veryHigh: "Very high"
        case .extremelyHigh: "Extremely high"
        }
    }

    /// The range of `sgv` in the person's `bands`; nil in range (very good included).
    public static func of(_ sgv: Int, _ bands: Bands) -> RangeState? {
        if sgv >= bands.extremelyHigh { return .extremelyHigh }
        return switch bands.of(Double(sgv)) {
        case .veryLow: .veryLow
        case .low: .low
        case .tight, .target: nil
        case .high: .high
        case .veryHigh: .veryHigh
        }
    }

    /// The range word the value's place draws for `face` (the reading `sgv`) with the status pill `pill`: an
    /// out-of-range value says its range unless it is struck (grey: no range colour to back up), is LO / HI (the text
    /// already says it) or the pill already says the side in words ("Eat carbs" / "Take insulin"). A tonal pill
    /// ("45m left", "Muted") says what was done, not the range: the word shows beside it. contracts/graph/range.
    static func shown(_ face: GlucoseLook, sgv: Int, bands: Bands, pill: StatusBadge.Look?) -> RangeState? {
        guard face.state == .value, !face.strike, (40...400).contains(sgv) else { return nil }
        if let pill, !pill.tonal, pill.kind == "carbs" || pill.kind == "rapid" { return nil }
        return of(sgv, bands.sane())
    }
}

/// The range word under (or beside) an out-of-range value: the word in the card's primary ink on a pill tinted with the
/// value's colour, so it reads at 4.5:1 on any band's tint (ContrastTests) and belongs to the number. Tinted widget
/// rendering (no hue) keeps the word solid on a faint pill: there the word is all that says the range.
enum RangeTag {
    private static func font(_ h: CGFloat) -> CTFont { Ink.font(h * 0.56, .semibold) }

    static func width(_ text: String, h: CGFloat) -> CGFloat { Ink.width(text, font(h)) + h * 0.9 }

    /// The tint behind the word and the word's ink.
    static func colors(tint: CGColor, _ pal: GraphPalette) -> (bg: CGColor, fg: CGColor) {
        if pal.knockout { return (CGColor(gray: 1, alpha: 0.22), CGColor(gray: 1, alpha: 1)) }
        return (tint.copy(alpha: (pal.dark ? 72 : 44) / 255.0) ?? tint, pal.ink)
    }

    /// Draws the tag `h` tall with its top-left at (`x`, `y`); returns its box.
    @discardableResult
    static func draw(
        _ ctx: CGContext, x: CGFloat, y: CGFloat, h: CGFloat, _ text: String, tint: CGColor, _ pal: GraphPalette
    ) -> CGRect {
        let box = CGRect(x: x, y: y, width: width(text, h: h), height: h)
        let (bg, fg) = colors(tint: tint, pal)
        ctx.setFillColor(bg)
        ctx.addPath(CGPath(roundedRect: box, cornerWidth: h / 2, cornerHeight: h / 2, transform: nil))
        ctx.fillPath()
        Ink.draw(ctx, text, x: x + h * 0.45, y: y + h / 2 + h * 0.56 * 0.36, font(h), fg)
        return box
    }
}
