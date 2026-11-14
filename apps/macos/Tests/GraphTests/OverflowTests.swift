// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import CoreGraphics
import Foundation
import SukraraCore
import Testing

@testable import SukraraGraph

/// What the card draws past its edges (a ring's flowers, laurel and crown, the value's digits) stays inside
/// `GlucoseCard.overflowRoom`, and the panel's picture carries that room: nothing of the card is cut (#181, 2026-11-14:
/// "the large glucose number and the ring, when it has flowers, are cropped on the sides").
@Suite struct OverflowTests {
    private static let now: TimeInterval = 1_800_000_000

    /// Eight steady days, 97 % in range (both rings wear flowers), ending on `last`.
    private static func flowered(_ last: Double) -> CardData {
        var rs: [GraphReading] = []
        var t = now - 8 * 86_400, i = 0
        while t < now - 60 {
            rs.append(GraphReading(t: t, v: i % 100 < 3 ? 200 : 118, rate: 0))
            t += 300
            i += 1
        }
        return CardData(readings: rs + [GraphReading(t: now - 30, v: last, rate: 2.5)], now: now)
    }

    private static let places: [(CardSurface, CGSize)] = [
        (.menubarCard, CGSize(width: 340, height: 196)),
        (.widgetValue, CGSize(width: 138, height: 138)), (.widgetValue, CGSize(width: 332, height: 138)),
        (.widgetGraph, CGSize(width: 138, height: 138)), (.widgetGraph, CGSize(width: 332, height: 350)),
        (.widgetCombined, CGSize(width: 138, height: 138)), (.widgetCombined, CGSize(width: 332, height: 138)),
        (.widgetCombined, CGSize(width: 332, height: 350)), (.widgetCombined, CGSize(width: 714, height: 350)),
    ]

    /// The box (px) of every pixel with ink in `img`.
    private static func ink(_ img: CGImage) -> (minX: Int, minY: Int, maxX: Int, maxY: Int)? {
        let w = img.width, h = img.height
        let ctx = CGContext(
            data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
            space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        ctx.draw(img, in: CGRect(x: 0, y: 0, width: w, height: h))
        let px = ctx.data!.bindMemory(to: UInt8.self, capacity: w * h * 4)
        var box: (minX: Int, minY: Int, maxX: Int, maxY: Int)?
        for y in 0..<h {
            for x in 0..<w where px[(y * w + x) * 4 + 3] > 4 {
                box = (
                    min(box?.minX ?? x, x), min(box?.minY ?? y, y), max(box?.maxX ?? x, x), max(box?.maxY ?? y, y)
                )
            }
        }
        return box
    }

    @Test(arguments: [58.0, 118.0, 188.0])
    func theRendererDrawsWithinTheRoom(_ last: Double) throws {
        let data = Self.flowered(last), wide: CGFloat = 40, room = GlucoseCard.overflowRoom
        for (surface, size) in Self.places {
            let img = try #require(
                GlucoseCard.image(
                    surface: surface, size: size, config: GraphConfig(), data: data, unit: .mgdl, dark: true,
                    scale: 2, overflow: wide))
            let box = try #require(Self.ink(img))
            // points past each edge of the card
            let past = [
                wide - CGFloat(box.minX) / 2, wide - CGFloat(box.minY) / 2,
                CGFloat(box.maxX + 1) / 2 - wide - size.width, CGFloat(box.maxY + 1) / 2 - wide - size.height,
            ]
            #expect(past.allSatisfy { $0 < room }, "\(surface) \(size): \(past)")
        }
    }

    /// The panel's picture: the card's flowers and digits reach past the card, and nothing touches the picture's edge.
    @Test(arguments: [58.0, 188.0])
    func thePanelsPictureCutsNothing(_ last: Double) throws {
        let size = CGSize(width: 340, height: 196)
        let p = GlucoseCard.picture(
            surface: .menubarCard, size: size, config: GraphConfig(), data: Self.flowered(last), unit: .mgdl,
            dark: true, scale: 2)
        let img = try #require(p.image)
        #expect(CGFloat(img.width) == (size.width + 2 * p.overflow) * 2)
        let box = try #require(Self.ink(img))
        #expect(box.minX > 0 && box.minY > 0 && box.maxX < img.width - 1 && box.maxY < img.height - 1)
        // the flowers do reach past the card (else this test checks nothing)
        #expect(CGFloat(box.minY) / 2 < p.overflow)
    }
}
