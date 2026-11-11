// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import CoreGraphics
import Foundation
import SukraraTesting
import Testing

@testable import SukraraApp
@testable import SukraraGraph

/// The dropdown's chart scrolled and zoomed (contracts/graph/layout/scenes.json `scroll`; Android's twin:
/// PanelScrollTest.kt): each case is drawn the way PanelView draws its card (the menubar card at its size, the
/// `ChartScroll` at the window's end and length, the `ChartEase` view once it has arrived), and the card shows only what
/// is in its window. Its chart equals the dropdown drawn with the visible data alone (every reading and treatment
/// outside the window removed, now = the window's end, the time shown = the window's length): the % column, the y
/// range, the range labels, the lines, the distribution and the plot; the y range is the visible readings'; the Now
/// pill shows only while scrolled or zoomed; treatment labels only up to rules.json `treatmentLabels`; and the
/// font-independent `expect` of each case, which Android checks too.
@Suite struct PanelScrollTests {
    private struct Scene {
        var id: String
        var readings: [GraphReading]
        var marks: [GraphMark]
    }

    private static func json(_ path: String) -> [String: Any] {
        guard let data = FileManager.default.contents(atPath: Contracts.url(path).path),
            let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else {
            Issue.record("contracts/\(path) unreadable")
            return [:]
        }
        return root
    }

    private static func num(_ a: Any?) -> Double { (a as? NSNumber)?.doubleValue ?? 0 }

    /// scenes.json's maths (the same integer maths as Android's CardLayoutTest and LayoutTests).
    private static func scene(_ o: [String: Any], now: TimeInterval) -> Scene {
        var rs: [GraphReading] = []
        let days = Int(num(o["days"]))
        if days > 0 {
            let lo = Int64(num(o["lo"])), hi = Int64(num(o["hi"])), period = Int64(num(o["period"])), half = period / 2
            let first = Int64(now) - Int64(days) * 86400, last = Int64(now) - Int64(num(o["age"]))
            var t = first
            while t < last {
                let k = ((t - first) % period + period) % period
                let tri = k <= half ? k : period - k
                rs.append(GraphReading(t: TimeInterval(t), v: Double(lo + (hi - lo) * tri / half)))
                t += 300
            }
            rs.append(
                GraphReading(t: TimeInterval(last), v: num(o["last"]), rate: (o["rate"] as? NSNumber)?.doubleValue))
        }
        let marks = (o["treatments"] as? [[String: Any]] ?? []).map {
            GraphMark(
                t: now + num($0["t"]), k: $0["k"] as? String ?? "", a: num($0["a"]), n: $0["n"] as? String ?? "",
                id: $0["id"] as? String ?? "")
        }
        return Scene(id: o["id"] as? String ?? "?", readings: rs, marks: marks)
    }

    /// The dropdown's card as PanelView draws it: `GlucoseCard.image`'s style and drawing at `PanelView.cardSize`, with
    /// the view `ChartEase` gives once its glide has arrived at `scroll`'s window.
    private func card(
        _ readings: [GraphReading], _ marks: [GraphMark], at now: TimeInterval, scroll: ChartScroll,
        config: GraphConfig
    ) -> CardLayout? {
        let data = CardData(readings: readings, marks: marks, now: now)
        let def = GlucoseCard.chartSpan(surface: .menubarCard, config: config) ?? 12 * 3600
        var ease = ChartEase()
        let y = GlucoseCard.yRange(
            surface: .menubarCard, config: config, readings: readings, end: scroll.end ?? now, span: scroll.span)
        ease.retarget(y: y, away: scroll.away, at: 0, instant: true)
        let view = ease.view(scroll, default: def, now: now, at: 0)
        var style = CardSurface.menubarCard.style(config, PlaceOverrides(), sensor: nil, words: .en)
        style.unit = .mgdl
        let size = PanelView.cardSize
        guard
            let ctx = CGContext(
                data: nil, width: Int(size.width), height: Int(size.height), bitsPerComponent: 8, bytesPerRow: 0,
                space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        else { return nil }
        return GlucoseCard.draw(
            ctx, size: size, readings: readings, marks: marks, now: now, style,
            GlucoseCard.palette(.menubarCard, dark: false, contrast: false), surface: .menubarCard, part: .all,
            metricsOf: data.metrics, alerts: data.alerts, view: view)
    }

    @Test func scrolledAndZoomedWindowsShowOnlyTheirData() throws {
        let root = Self.json("graph/layout/scenes.json")
        let now = Self.num(root["now"])
        let scroll = try #require(root["scroll"] as? [String: Any])
        let all = ((scroll["scenes"] as? [[String: Any]] ?? []) + (root["scenes"] as? [[String: Any]] ?? []))
            .map { Self.scene($0, now: now) }
        let cases = try #require(scroll["cases"] as? [[String: Any]])
        let rules = Self.json("graph/layout/rules.json")
        let maxLabel = Self.num((rules["treatmentLabels"] as? [String: Any])?["maxHours"]) * 3600
        #expect(GlucoseGraph.labelsMaxS == maxLabel, "Chart.labelsMaxS vs rules.json treatmentLabels")
        let config = GraphConfig()
        let windowS = TimeInterval(config.hours) * 3600
        let b = config.bands
        func info(_ l: CardLayout, _ n: String) -> String { l.all(n).map(\.info).joined(separator: ",") }
        func box(_ l: CardLayout, _ n: String) -> String {
            guard let r = l.first(n)?.box else { return "-" }
            return [r.minX, r.minY, r.maxX, r.maxY].map { CardLayout.dp($0, l.d) }.joined(separator: ",")
        }
        for c in cases {
            let sc = try #require(all.first { $0.id == c["scene"] as? String }, "no scene \(c["scene"] ?? "?")")
            let back = Self.num(c["back"])
            let span = (c["span"] as? NSNumber).map { TimeInterval($0.doubleValue) }
            let w = span ?? windowS
            let end = now - back, from = end - w
            let name = "\(sc.id) back \(Int(back))s" + (span.map { " span \(Int($0))s" } ?? "")
            let visible = sc.readings.filter { $0.t >= from - 60 && $0.t <= end }
            var aloneConfig = config
            aloneConfig.hours = Int(w / 3600)
            let scrolled = try #require(
                card(
                    sc.readings, sc.marks, at: now, scroll: ChartScroll(end: back > 0 ? end : nil, span: span),
                    config: config))
            let alone = try #require(
                card(
                    visible, sc.marks.filter { $0.t >= from && $0.t <= end }, at: end, scroll: ChartScroll(),
                    config: aloneConfig))
            let markers = scrolled.first("chart.markers") != nil
            for n in ["chart.pct", "chart.y", "chart.values", "chart.markers", "chart.lines"]
                + (markers ? [] : ["time.text"])
            {
                #expect(info(scrolled, n) == info(alone, n), "\(name): \(n), the visible data alone")
            }
            for n in ["chart.distribution", "chart.distribution.dots", "chart.distribution.cut", "chart.plot"] {
                #expect(box(scrolled, n) == box(alone, n), "\(name): \(n), the visible data alone")
            }
            // the y range is the visible readings'
            let lo = Int(visible.map(\.v).min() ?? 0), hi = Int(visible.map(\.v).max() ?? 0)
            let want =
                "\(max(GlucoseStats.minMg, min(b.low - 22, b.veryLow - 6, lo - 6)))-"
                + "\(min(GlucoseStats.maxMg, max(b.high + 40, b.veryHigh + 8, hi + 8)))"
            #expect(info(scrolled, "chart.y") == want, "\(name): the visible readings' y range (\(lo)…\(hi))")
            let window = back > 0 ? "\(Int((from - now) / 60))m..\(Int((end - now) / 60))m" : ""
            #expect(info(scrolled, "chart.window") == window, "\(name): window")
            // treatment labels only up to rules.json treatmentLabels
            let labelled = !scrolled.all("chart.label").isEmpty
            if w > maxLabel { #expect(!labelled, "\(name): a \(Int(w / 3600)) h window draws treatment labels") }
            if w <= maxLabel,
                sc.marks.contains(where: { $0.k != "note" && $0.t >= from && $0.t <= end && config.marks.label($0.k) })
            {
                #expect(labelled, "\(name): a \(Int(w / 3600)) h window with treatments draws no labels")
            }
            let away = back > 0 || span != nil
            #expect((scrolled.first("chart.now") != nil) == away, "\(name): the Now pill only while scrolled or zoomed")
            // the shared, font-independent answer (Android checks the same)
            let expect = (c["expect"] as? [String: String]) ?? [:]
            var got: [String: String] = [:]
            for n in expect.keys {
                got[n] = scrolled.all(n).map {
                    $0.info.replacingOccurrences(of: "\\s*\\[[^]]*]", with: "", options: .regularExpression)
                }.joined(separator: ",")
            }
            #expect(got == expect, "\(name)")
        }
    }
}
