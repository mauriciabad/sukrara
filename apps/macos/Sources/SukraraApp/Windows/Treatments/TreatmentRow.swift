// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import SukraraCore
import SukraraGraph
import SwiftUI

/// One entry of the Treatments window (#207, docs/37-treatment-list.md): the chart's marker for its kind, what it
/// was and when, a pen for entries read from the pen, the delivery only when it says something, and at the end the
/// glucose then with its trend. Edit and Delete show on hover; the context menu, VoiceOver's actions and a trackpad
/// swipe reach them without it.
struct TreatmentRow: View {
    let row: TreatmentsModel.Row
    let now: Date
    let loggedBy: String
    /// Where a read-only entry lives (*From Nightscout · change it there*), shown under its time; nil = none.
    var elsewhere: String? = nil
    let canChange: Bool
    let shown: TreatmentsModel.Shown?
    let glucose: Reading?
    let unit: GlucoseUnit
    let edit: () -> Void
    let delete: () -> Void
    let retry: () -> Void
    @State private var hovering = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let t = row.treatment
        let at = Date(timeIntervalSince1970: TimeInterval(t.t))
        let title = TreatmentsView.title(t)
        HStack(spacing: 10) {
            KindBadge(kind: t.k)
            VStack(alignment: .leading, spacing: 2) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 5) {
                        Text(title).contentTransition(.numericText())
                        if let pen = TreatmentsView.penLine(t) {
                            PenGlyph()
                                .frame(width: 14, height: 14)
                                .foregroundStyle(.secondary)
                                .help(pen)
                                .accessibilityLabel(pen)
                        }
                    }
                    if t.k != .note, !t.n.isEmpty { Text(t.n).font(.callout).foregroundStyle(.secondary) }
                    Text(Clock.time(at) + " · " + TreatmentsView.since(at, now: now))
                        .font(.callout)
                        .foregroundStyle(.secondary)
                    if let elsewhere {
                        Label(elsewhere, systemImage: "globe").font(.callout).foregroundStyle(.secondary)
                    }
                }
                .accessibilityElement(children: .combine)
                .accessibilityHint(loggedBy)
                .accessibilityActions {
                    if canChange {
                        Button(L10n.t("Edit…"), action: edit)
                        Button(L10n.t("Delete"), action: delete)
                    }
                }
                // the separator under the row starts at the text, not at the badge
                .alignmentGuide(.listRowSeparatorLeading) { $0[.leading] }
                if let doses = t.pd, doses.count > 1 {
                    DisclosureGroup(L10n.t("Doses")) {
                        ForEach(Array(doses.enumerated()), id: \.offset) { _, d in
                            HStack {
                                Text(TreatmentsView.doseText(d)).foregroundStyle(d.p ? .secondary : .primary)
                                Spacer()
                                Text(Clock.time(Date(timeIntervalSince1970: TimeInterval(d.t))))
                                    .foregroundStyle(.secondary)
                            }
                            .font(.callout)
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel(TreatmentsView.doseSpoken(d))
                        }
                    }
                    .font(.callout)
                }
            }
            Spacer(minLength: 8)
            delivery
            if canChange && hovering {
                actions.transition(.opacity)
            }
            if let glucose { GlucoseThen(reading: glucose, unit: unit) }
        }
        .frame(minHeight: 30)
        .contentShape(Rectangle())
        .help(loggedBy)
        .onHover { h in
            withAnimation(reduceMotion ? nil : .spring(duration: 0.2)) { hovering = h }
        }
        .accessibilityElement(children: .contain)
        .contextMenu {
            if canChange {
                Button(L10n.t("Edit…"), action: edit)
                Button(L10n.t("Delete"), role: .destructive, action: delete)
            }
        }
        .swipeActions(edge: .trailing) {
            if canChange {
                Button(L10n.t("Delete"), role: .destructive, action: delete)
            }
        }
    }

    private var actions: some View {
        let title = TreatmentsView.title(row.treatment)
        return HStack(spacing: 6) {
            Button(action: edit) { Image(systemName: "pencil") }
                .help(L10n.t("Edit…"))
                .accessibilityLabel(L10n.t("Edit \(title)"))
            Button(action: delete) { Image(systemName: "trash") }
                .help(L10n.t("Delete"))
                .accessibilityLabel(L10n.t("Delete \(title)"))
        }
        .buttonStyle(.borderless)
    }

    @ViewBuilder private var delivery: some View {
        switch shown {
        case .sending:
            Label(L10n.t("Sending"), systemImage: "arrow.up.circle")
                .foregroundStyle(.secondary)
                .transition(.opacity)
        case .accepted:
            Label(L10n.t("On the phone"), systemImage: "checkmark.circle.fill")
                .foregroundStyle(.green)
                .transition(reduceMotion ? .opacity : .opacity.combined(with: .scale(scale: 0.8)))
        case .failed:
            HStack {
                Label(L10n.t("Not sent"), systemImage: "exclamationmark.circle").foregroundStyle(.red)
                Button(L10n.t("Retry"), action: retry)
            }
            .transition(.opacity)
        case nil: EmptyView()
        }
    }
}

/// The kind's badge: THE chart's marker in the kind's tonal ink on its tonal tint (`GlucoseCard.treatmentBadge`).
struct KindBadge: View {
    let kind: Treatment.Kind
    var size: CGFloat = 22
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        Group {
            if let cg = GlucoseCard.treatmentBadge(kind: kind.rawValue, size: size, scale: 3, dark: scheme == .dark) {
                Image(decorative: cg, scale: 3)
            } else {
                Color.clear
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/// A form field's kind over it (Add Treatment, Edit Treatment): its small badge and its name.
struct KindLabel: View {
    let kind: Treatment.Kind

    var body: some View {
        HStack(spacing: 5) {
            KindBadge(kind: kind, size: 15)
            Text(TreatmentKindLook.name(kind))
        }
        .font(.callout)
        .foregroundStyle(.secondary)
        .accessibilityHidden(true)
    }
}

/// The glucose then: the reading nearest the entry's time as the card writes it (LO / HI, the unit), with the card's
/// arrow for that reading's own trend (`GlucoseCard.trendImage`; the ring without one).
struct GlucoseThen: View {
    let reading: Reading
    let unit: GlucoseUnit

    var body: some View {
        let band = reading.trend?.band
        HStack(spacing: 3) {
            Text(Freshness.valueText(reading.mgdl, unit: unit)).monospacedDigit()
            if let cg = GlucoseCard.trendImage(
                rate: reading.trend?.rate, band: band, size: 13, scale: 3, color: CGColor(gray: 0, alpha: 1))
            {
                Image(nsImage: Self.template(cg, size: 13)).foregroundStyle(.secondary)
            }
        }
        .font(.callout)
        .foregroundStyle(.secondary)
        .frame(minWidth: 52, alignment: .trailing)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L10n.t("Glucose then \(spoken)"))
    }

    /// "142 milligrams per decilitre, rising": the card's words for this reading and its own trend.
    private var spoken: String {
        GlucoseCard.spokenReading(sgv: reading.mgdl, band: reading.trend?.band, unit: unit)
    }

    /// The arrow as a template image, so it takes the text's colour in either appearance.
    private static func template(_ cg: CGImage, size: CGFloat) -> NSImage {
        let image = NSImage(cgImage: cg, size: NSSize(width: size, height: size))
        image.isTemplate = true
        return image
    }
}

/// An insulin pen (Android `ds_ic_dia_pen`: a capsule body with the dose window, the dose button and the needle,
/// diagonal), on the Material Symbols grid; drawn in the foreground style.
struct PenGlyph: View {
    var body: some View {
        Canvas { ctx, size in
            let s = min(size.width, size.height) / 960
            var body = Path(
                roundedRect: CGRect(x: -320.4, y: -80, width: 640.8, height: 160), cornerRadius: 80)
            body = body.applying(CGAffineTransform(rotationAngle: -.pi / 4))
                .applying(CGAffineTransform(translationX: 510, y: 370))
            var lines = Path()
            for (a, b) in [((560, 300), (470, 390)), ((720, 160), (800, 80)), ((290, 590), (170, 710))] {
                lines.move(to: CGPoint(x: a.0, y: a.1))
                lines.addLine(to: CGPoint(x: b.0, y: b.1))
            }
            let scale = CGAffineTransform(scaleX: s, y: s)
            let style = StrokeStyle(lineWidth: 72 * s, lineCap: .round, lineJoin: .round)
            ctx.stroke(body.applying(scale), with: .foreground, style: style)
            ctx.stroke(lines.applying(scale), with: .foreground, style: style)
        }
        .accessibilityElement()
    }
}
