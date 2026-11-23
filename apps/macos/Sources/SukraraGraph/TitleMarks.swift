// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import CoreGraphics
import Foundation
import SukraraCore

extension GlucoseCard {
    /// The range of `config` the newest reading of `data` is in, nil in range or without a reading: the menu bar
    /// colours its number with that range's colour on the card (`RangeState.color`, #257), like Android's status bar.
    public static func range(_ data: CardData, config: GraphConfig) -> RangeState? {
        // the value as written (whole mg/dL): 179.6 reads 180, so it is high like the card's 180
        guard let sgv = data.latest?.sgv else { return nil }
        return RangeState.of(sgv, config.bands.sane())
    }

    /// The SF Symbol a text place shows instead of a value for no readings for 20 min.
    public static let noReadingsSymbol = GlucoseLook.noReadingsSymbol

    /// The trend mark as a picture for text places that can show one (the menu bar): THE card's arrow at
    /// `TrendAngle.deg` of `rate`, doubled with `band` ±3, or the "no trend" ring for `rate` nil. `size` points square,
    /// `scale` pixels per point, drawn in `color`.
    public static func trendImage(rate: Double?, band: Int?, size: CGFloat, scale: CGFloat = 2, color: CGColor)
        -> CGImage?
    {
        let px = max(4, Int(size * scale))
        guard
            let ctx = CGContext(
                data: nil, width: px, height: px, bitsPerComponent: 8, bytesPerRow: 0,
                space: CGColorSpace(name: CGColorSpace.sRGB)!,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        else { return nil }
        // y-down points, like the card
        ctx.translateBy(x: 0, y: CGFloat(px))
        ctx.scaleBy(x: scale, y: -scale)
        ctx.translateBy(x: size / 2, y: size / 2)
        if let rate {
            ctx.rotate(by: CGFloat(TrendAngle.deg(rate)) * .pi / 180)
            drawArrow(ctx, scale: size, color: color, double: band.map { TrendAngle.isDouble(band: $0) } ?? false)
        } else {
            let r = TrendMark.ringRadius(size)
            ctx.setStrokeColor(color)
            ctx.setLineWidth(TrendMark.ringStroke(size))
            ctx.strokeEllipse(in: CGRect(x: -r, y: -r, width: 2 * r, height: 2 * r))
        }
        return ctx.makeImage()
    }
}
