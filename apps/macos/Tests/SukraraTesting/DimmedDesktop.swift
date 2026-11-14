// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import CoreGraphics
import Foundation

/// How the macOS desktop draws a widget while it is dimmed (an app window in front; Desktop & Dock › *Dim widgets on
/// desktop* › *Automatically*), measured on macOS 27.0.1 (26A434) from `screencapture -l` of the widget's own window
/// (no wallpaper behind it), with the installed Sukrara widget and Apple's Calendar and Clock next to it (#189).
///
/// The system does not ask the widget for another rendering mode (the widget still reads `.fullColor`, so a
/// tinted-mode branch never runs). It draws the full-colour widget greyscale, the way it draws an image in accented
/// rendering with desaturated images:
/// - every pixel's grey is a tone curve of its luma (Rec. 601): dark stays dark and light stays light, whatever the
///   system's appearance or the glass;
/// - its opacity is lifted (`opacity ^ 0.18`): a band at 9 % comes out at 64 %;
/// - the container background is drawn the same way: `.fill.tertiary` (dark appearance) comes out as a light grey card,
///   Apple's black one as black.
///
/// So a dark-appearance card on `.fill.tertiary` turns into light inks on a light card: the value, arrow and ring
/// numbers wash out and the dark grid comes out strongest (the owner's screenshot). The desktop then lays the window
/// over the wallpaper with a tint of its colour, the same for every widget; the model stops at the window.
public enum DimmedDesktop {
    /// What the widget's container background is.
    public enum Background: Sendable {
        /// `.containerBackground(.fill.tertiary, for: .widget)` in the dark appearance: measured 202.
        case fillTertiary
        /// An opaque colour.
        case solid(CGColor)
    }

    /// Measured (luma of the widget's colour → the grey the system drew, 0…255): `0x1C1C1E` → 0, `0x3A3A3E` → 19,
    /// `0x8CC63F` → 138, `0xF2F2F2` → 193; white extrapolated.
    static let tone: [(Double, Double)] = [(0, 0), (30, 0), (58, 19), (165, 138), (242, 193), (255, 199)]
    static let fillTertiaryGrey: Double = 202
    static let opacityExponent: Double = 0.18

    /// Rec. 601 luma of sRGB components 0…255.
    public static func luma(_ r: Double, _ g: Double, _ b: Double) -> Double { 0.299 * r + 0.587 * g + 0.114 * b }

    /// The grey (0…255) the system draws for a pixel of luma `y`.
    public static func grey(luma y: Double) -> Double {
        for i in 1..<tone.count where y <= tone[i].0 {
            let (x0, g0) = tone[i - 1], (x1, g1) = tone[i]
            return g0 + (g1 - g0) * (y - x0) / (x1 - x0)
        }
        return tone.last!.1
    }

    /// The opacity the system draws a pixel of opacity `a` (0…1) with.
    public static func coverage(_ a: Double) -> Double { a <= 0 ? 0 : pow(a, opacityExponent) }

    /// The grey (0…255) of `background` alone.
    public static func grey(_ background: Background) -> Double {
        switch background {
        case .fillTertiary: return fillTertiaryGrey
        case .solid(let c):
            let s = c.converted(to: CGColorSpace(name: CGColorSpace.sRGB)!, intent: .defaultIntent, options: nil) ?? c
            let k = s.components ?? [0, 0, 0, 1]
            let (r, g, b) = k.count >= 3 ? (k[0], k[1], k[2]) : (k[0], k[0], k[0])
            return grey(luma: luma(r * 255, g * 255, b * 255))
        }
    }

    /// The widget's window as the dimmed desktop draws it: `card` (sRGB, any alpha) over `background`, one grey per
    /// pixel (0…255), row-major from the top-left, `card.width` × `card.height`.
    public struct Window: Sendable {
        public let width: Int, height: Int
        public let grey: [Double]
        public let background: Double

        public subscript(x: Int, y: Int) -> Double { grey[y * width + x] }

        /// The pixels of `rect` (pixels, y-down from the top-left), clipped to the window.
        public func pixels(in rect: CGRect) -> [Double] {
            let x0 = max(0, Int(rect.minX)), x1 = min(width, Int(rect.maxX.rounded(.up)))
            let y0 = max(0, Int(rect.minY)), y1 = min(height, Int(rect.maxY.rounded(.up)))
            guard x0 < x1, y0 < y1 else { return [] }
            return (y0..<y1).flatMap { y in (x0..<x1).map { x in grey[y * width + x] } }
        }
    }

    public static func render(_ card: CGImage, on background: Background) -> Window {
        let w = card.width, h = card.height
        var px = [UInt8](repeating: 0, count: w * h * 4)
        px.withUnsafeMutableBytes { buf in
            let ctx = CGContext(
                data: buf.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                space: CGColorSpace(name: CGColorSpace.sRGB)!,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
            ctx.draw(card, in: CGRect(x: 0, y: 0, width: w, height: h))
        }
        let bg = grey(background)
        var out = [Double](repeating: bg, count: w * h)
        for i in 0..<(w * h) {
            let a = Double(px[i * 4 + 3])
            guard a > 0 else { continue }
            // unpremultiply: the system looks at the colour, then at the opacity
            let y = luma(Double(px[i * 4]) * 255 / a, Double(px[i * 4 + 1]) * 255 / a, Double(px[i * 4 + 2]) * 255 / a)
            let c = coverage(a / 255)
            out[i] = grey(luma: y) * c + bg * (1 - c)
        }
        // a bitmap context's memory starts at the image's top row: already y-down
        return Window(width: w, height: h, grey: out, background: bg)
    }

    /// WCAG contrast between two greys (0…255, sRGB).
    public static func contrast(_ a: Double, _ b: Double) -> Double {
        func lin(_ v: Double) -> Double {
            let c = v / 255
            return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        let (l1, l2) = (lin(a) + 0.05, lin(b) + 0.05)
        return max(l1, l2) / min(l1, l2)
    }

    /// `window` as a picture (greyscale, opaque): for docs/img and the snapshots.
    public static func image(_ window: Window) -> CGImage? {
        var bytes = window.grey.map { UInt8(max(0, min(255, $0.rounded()))) }
        return bytes.withUnsafeMutableBytes { buf in
            CGContext(
                data: buf.baseAddress, width: window.width, height: window.height, bitsPerComponent: 8,
                bytesPerRow: window.width, space: CGColorSpaceCreateDeviceGray(),
                bitmapInfo: CGImageAlphaInfo.none.rawValue)?.makeImage()
        }
    }
}
