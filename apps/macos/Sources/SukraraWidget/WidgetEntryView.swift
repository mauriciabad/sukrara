// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import SukraraCore
import SukraraGraph
import SwiftUI
import WidgetKit

/// The three widget kinds and the registered card surface each one draws (contracts/graph/surfaces/surfaces.json).
enum WidgetKind {
    case value, graph, combined

    var surface: CardSurface {
        switch self {
        case .value: .widgetValue
        case .graph: .widgetGraph
        case .combined: .widgetCombined
        }
    }
}

/// One widget's picture: the card of its kind's surface at the family's content size, drawn by the one renderer as a
/// single bitmap (the widget chooses only its size, its surface and the rendering mode). VoiceOver reads the card's
/// sentence and chart summary (`GlucoseCard.accessibilityLabel`). A tap opens quick Add treatment; without data, the app.
struct WidgetEntryView: View {
    let kind: WidgetKind
    let entry: SnapshotEntry
    @Environment(\.widgetRenderingMode) private var renderingMode
    @Environment(\.colorScheme) private var scheme
    @Environment(\.displayScale) private var displayScale

    /// Room around the card for what overflows it (a ring's laurel or crown): drawn into the widget's margin.
    private static let overflow: CGFloat = 12

    var body: some View {
        Group {
            if let snapshot = entry.snapshot, let data = entry.data, let reason = snapshot.reason,
                GlucoseCard.face(data, unit: snapshot.unit, surface: kind.surface).state == .none
            {
                // nothing to draw yet: why, and where to go, instead of a bare "No readings yet" (#72)
                why(reason)
                    .widgetURL(URL(string: SukraraIDs.Link.panel))
            } else if let snapshot = entry.snapshot, let data = entry.data {
                card(snapshot, data)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(
                        GlucoseCard.accessibilityLabel(
                            data, unit: snapshot.unit, surface: kind.surface, config: snapshot.config)
                    )
                    .widgetURL(URL(string: SukraraIDs.Link.newTreatment))
            } else {
                openApp
                    .widgetURL(URL(string: SukraraIDs.Link.panel))
            }
        }
        // the card is one image: redacted (placeholder, gallery) it would come out blank
        .unredacted()
        .containerBackground(.fill.tertiary, for: .widget)
    }

    private func card(_ snapshot: WidgetSnapshot, _ data: CardData) -> some View {
        GeometryReader { geo in
            let o = Self.overflow
            let tinted = renderingMode != .fullColor
            if let image = GlucoseCard.image(
                surface: kind.surface, size: geo.size, config: snapshot.config, data: data, unit: snapshot.unit,
                dark: scheme == .dark, tinted: tinted, scale: displayScale, overflow: o)
            {
                // tinted: the renderer's mask in the system's primary ink for the material it draws on, light or
                // dark by the wallpaper (#189), like the system's own text
                Image(decorative: image, scale: displayScale)
                    .renderingMode(tinted ? .template : .original)
                    .resizable()
                    .foregroundStyle(.primary)
                    .frame(width: geo.size.width + 2 * o, height: geo.size.height + 2 * o)
                    .frame(width: geo.size.width, height: geo.size.height)
            }
        }
        // one ink in the tinted modes: no accent split on top
        .widgetAccentable(false)
    }

    /// The app has no value and says why (`NoDataReason`): the "no readings" symbol, the reason and where to go; a click
    /// opens the panel, which has the reason's action. VoiceOver reads it as one sentence.
    private func why(_ reason: NoDataReason) -> some View {
        let words = WidgetWords.current
        let (title, next) = words.reason(reason)
        return VStack(alignment: .leading, spacing: 4) {
            Image(systemName: GlucoseCard.noReadingsSymbol)
                .font(.title3)
                .foregroundStyle(.secondary)
            Text(title)
                .font(.headline)
                .lineLimit(3)
                .minimumScaleFactor(0.8)
            Text(next)
                .font(.callout)
                .foregroundStyle(.secondary)
                .lineLimit(3)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: "\(words.noData) \(title). \(next)."))
    }

    /// No snapshot (the app isn't running or set up, or wrote another format): no number at all, and where to go.
    private var openApp: some View {
        let words = WidgetWords.current
        return VStack(alignment: .leading, spacing: 4) {
            Text(GlucoseCard.noValueText)
                .font(.system(.largeTitle, design: .rounded, weight: .semibold))
            Text(words.openApp)
                .font(.callout)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(words.openAppSpoken)
    }
}
