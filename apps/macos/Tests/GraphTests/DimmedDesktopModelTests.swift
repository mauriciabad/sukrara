// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import CoreGraphics
import Foundation
import SukraraTesting
import Testing

/// `DimmedDesktop` against the real desktop: greys measured with `screencapture -l` on the installed widget's window
/// (macOS 27.0.1, dark appearance, an app in front; #189), each with the colour and opacity the renderer drew there.
@Suite struct DimmedDesktopModelTests {
    private static func hex(_ v: UInt32, _ a: CGFloat = 1) -> CGColor {
        CGColor(
            srgbRed: CGFloat((v >> 16) & 0xFF) / 255, green: CGFloat((v >> 8) & 0xFF) / 255,
            blue: CGFloat(v & 0xFF) / 255, alpha: a)
    }

    /// One pixel of `color` on `background`, as the model draws it.
    private static func shown(_ color: CGColor?, on background: DimmedDesktop.Background) -> Double {
        let ctx = CGContext(
            data: nil, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 0,
            space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        if let color {
            ctx.setFillColor(color)
            ctx.fill(CGRect(x: 0, y: 0, width: 1, height: 1))
        }
        return DimmedDesktop.render(ctx.makeImage()!, on: background)[0, 0]
    }

    /// (what was drawn, on what, the grey measured on screen)
    private static let measured: [(String, CGColor?, DimmedDesktop.Background, Double)] = [
        ("empty card (.fill.tertiary)", nil, .fillTertiary, 202),
        ("grid 3A3A3E", hex(0x3A3A3E), .fillTertiary, 19),
        ("value 141 (target 8CC63F)", hex(0x8CC63F), .fillTertiary, 138),
        ("axis label A3A3A8", hex(0xA3A3A8), .fillTertiary, 138),
        ("% label F2F2F2", hex(0xF2F2F2), .fillTertiary, 193),
        ("gap round the latest dot 1C1C1E", hex(0x1C1C1E), .fillTertiary, 0),
        ("tight band 3DD47A at 0x2A", hex(0x3DD47A, 0x2A / 255), .fillTertiary, 150),
        ("target band 8CC63F at 0x16", hex(0x8CC63F, 0x16 / 255), .fillTertiary, 160),
    ]

    @Test func matchesTheCapture() {
        for (what, color, bg, grey) in Self.measured {
            let g = Self.shown(color, on: bg)
            #expect(abs(g - grey) <= 6, "\(what): model \(g), screen \(grey)")
        }
    }
}
