// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import Observation
import SukraraCore
import SwiftUI

/// Whose glucose and where it comes from (schema `source.person`, `source.kind`; docs/44 "Person × Source"), as setup's
/// two steps and Settings › Source edit them. The words are Android's (`ds_person_*`, `ds_source_*`).
@Observable
public final class SourceChoiceModel {
    @ObservationIgnored let store: any SettingsStoring
    /// Whether this Mac follows someone now (the answer an unanswered Mac runs on).
    @ObservationIgnored var following: () -> Bool = { false }
    /// The answer changed: the app model reads it again.
    @ObservationIgnored var changed: () -> Void = {}
    private var revision = 0

    init(store: any SettingsStoring) {
        self.store = store
    }

    /// The stored answer (nil = not answered yet).
    var stored: SourceSpec? {
        _ = revision
        return SourceSpec.stored(in: store)
    }

    /// What the Mac runs on: the answer, else following → their Sukrara.
    var running: SourceSpec? { stored ?? SourceSpec.running(in: store, following: following()) }

    func choose(_ spec: SourceSpec) {
        spec.store(in: store)
        revision += 1
        NSHapticFeedbackManager.defaultPerformer.perform(.levelChange, performanceTime: .now)
        changed()
    }

    static func personName(_ p: Person) -> String {
        p == .yours ? L10n.t("Yours") : L10n.t("Someone else's")
    }

    static func personMeans(_ p: Person) -> String {
        p == .yours ? L10n.t("You wear the sensor.") : L10n.t("A child, a partner or a friend.")
    }

    static func title(_ s: SourceSpec) -> String {
        switch s.kind {
        case .sukrara: s.person == .yours ? L10n.t("Sukrara on your phone") : L10n.t("Their Sukrara")
        case .lanJuggluco: L10n.t("Juggluco on this network")
        case .nightscout: "Nightscout"
        case .libreLinkUp: "LibreLinkUp"
        }
    }

    /// How the picker groups the sources: by what the person feels (docs/44 "Setup").
    static func groupName(_ g: SourceGroup) -> String {
        switch g {
        case .offline: L10n.t("On this Mac · works offline")
        case .network: L10n.t("On this network · works offline")
        case .online: L10n.t("Online · needs internet")
        case .sukrara: L10n.t("From another Sukrara · internet or Bluetooth nearby")
        }
    }

    static func means(_ s: SourceSpec) -> String {
        switch s.kind {
        case .sukrara:
            s.person == .yours
                ? L10n.t("Your phone reads your sensor and shares it with this Mac")
                : L10n.t("Their phone shares it with this Mac after an invite")
        case .lanJuggluco:
            L10n.t("Juggluco on a phone on this Wi-Fi reads the sensor; Sukrara reads Juggluco")
        case .nightscout:
            s.person == .yours
                ? L10n.t("Glucose and treatments from your site · about 2 min late")
                : L10n.t("Glucose and treatments from their site · about 2 min late")
        case .libreLinkUp:
            s.person == .yours
                ? L10n.t("The Libre app shares your glucose here · about 1 min late · may stop working any day")
                : L10n.t(
                    "Their Libre app shares with your LibreLinkUp account · about 1 min late · may stop working any day"
                )
        }
    }
}

/// The two big choices of *Whose glucose will Sukrara show?*: an illustration each, the answer and one line, side by
/// side. Picking one lifts it (tonal fill, a stronger outline, a spring) and its art plays once (`PersonArt`) with a
/// trackpad tap; Reduce Motion: it changes at once. VoiceOver: two buttons with the selected trait.
struct PersonChoice: View {
    @Binding var selected: Person?

    var body: some View {
        HStack(spacing: 12) {
            ForEach(Person.allCases, id: \.self) { p in
                let on = selected == p
                Button {
                    if !on { NSHapticFeedbackManager.defaultPerformer.perform(.alignment, performanceTime: .now) }
                    selected = p
                } label: {
                    VStack(alignment: .leading, spacing: 8) {
                        PersonArt(
                            person: p, selected: on,
                            background: on
                                ? Color(nsColor: .windowBackgroundColor).mix(with: .accentColor, by: 0.14)
                                : Color(nsColor: .controlBackgroundColor)
                        )
                        .frame(height: 120)
                        HStack(alignment: .firstTextBaseline) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(SourceChoiceModel.personName(p)).font(.title3.bold())
                                Text(SourceChoiceModel.personMeans(p)).font(.callout).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: on ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(on ? Color.accentColor : .secondary)
                                .font(.title3)
                        }
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: 16)
                            .fill(on ? Color.accentColor.opacity(0.14) : Color(nsColor: .controlBackgroundColor))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .strokeBorder(on ? Color.accentColor : Color.secondary.opacity(0.3), lineWidth: on ? 2 : 1)
                    )
                    .shadow(color: .black.opacity(on ? 0.12 : 0), radius: on ? 6 : 0, y: on ? 2 : 0)
                    .modifier(ChoiceCardMotion(on: on))
                    .contentShape(RoundedRectangle(cornerRadius: 16))
                }
                .buttonStyle(.plain)
                .accessibilityElement(children: .combine)
                .accessibilityAddTraits(on ? [.isSelected, .isButton] : .isButton)
            }
        }
    }
}

/// A big choice card's pick (Whose glucose, *Do you have Sukrara on your phone?*; Android's `PersonChoiceCard`): only
/// the card being picked lifts on a spring; the one left goes back to plain at once-ish, without the pick's spring or
/// lift (it moved both: one `.animation` over the pair sprang the card being left too). Reduce Motion: no lift, no
/// animation.
struct ChoiceCardMotion: ViewModifier {
    let on: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .scaleEffect(on && !reduceMotion ? 1.01 : 1)
            .animation(Self.animation(picked: on, reduceMotion: reduceMotion), value: on)
    }

    /// The card being picked: the spring; the card being left: a short ease without bounce; Reduce Motion: none.
    static func animation(picked: Bool, reduceMotion: Bool) -> Animation? {
        if reduceMotion { return nil }
        return picked ? .spring(duration: 0.35, bounce: 0.25) : .easeOut(duration: 0.15)
    }
}

/// The art of `person`, drawn on a 320 × 120-ish board scaled to fit, in the system's colours (light and dark), never a
/// glucose number. Yours: you, the sensor on your arm sending its signal to the phone in your hand. Someone else's: a
/// small person with a sensor and their own phone (their Sukrara), reaching your phone in two arches: high through the
/// cloud, low over Bluetooth. A tinted circle sits behind the phone, stronger when picked. On a pick (none with Reduce
/// Motion) the sensor pulses, the waves go out or a dot travels each arch, and the phone's line scrolls on as if time
/// passed, landing on the same shape. `background` is the card's fill (the cut-out around the hand). Android:
/// `PersonArt` in PersonChoice.kt, the same drawing.
struct PersonArt: View {
    let person: Person
    let selected: Bool
    var background: Color = Color(nsColor: .controlBackgroundColor)
    @State private var pickedAt: Date?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(paused: pickedAt == nil)) { timeline in
            let t =
                pickedAt.map { min(timeline.date.timeIntervalSince($0), PersonArtDrawing.pickS) }
                ?? PersonArtDrawing.pickS
            Canvas { ctx, size in
                PersonArtDrawing(person: person, selected: selected, card: background, t: t).draw(&ctx, size)
            }
        }
        .accessibilityHidden(true)
        .onChange(of: selected) { _, on in
            guard on, !reduceMotion else { return }
            let start = Date()
            pickedAt = start
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(PersonArtDrawing.pickS + 0.1))
                if pickedAt == start { pickedAt = nil }
            }
        }
    }
}

/// `PersonArt`'s drawing at `t` seconds since the pick (`pickS` = at rest), in board units.
struct PersonArtDrawing {
    static let pickS: Double = 1.6
    static let board = CGSize(width: 320, height: 132)
    let person: Person
    let selected: Bool
    let card: Color
    let t: Double

    // someone else's in purple: never a red or pink, the colours of a low
    private var tone: Color { person == .yours ? .accentColor : .purple }
    private var bodyFill: Color {
        Color(
            nsColor: NSColor(name: nil) { a in
                a.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
                    ? NSColor(srgbRed: 0.227, green: 0.227, blue: 0.235, alpha: 1)
                    : NSColor(srgbRed: 0.898, green: 0.898, blue: 0.918, alpha: 1)
            })
    }
    private var line: Color { .secondary }
    private var screen: Color { Color(nsColor: .textBackgroundColor) }

    func draw(_ ctx: inout GraphicsContext, _ size: CGSize) {
        let s = min(size.width / Self.board.width, size.height / Self.board.height)
        ctx.translateBy(x: (size.width - Self.board.width * s) / 2, y: (size.height - Self.board.height * s) / 2)
        ctx.scaleBy(x: s, y: s)
        if person == .yours { yours(ctx) } else { theirs(ctx) }
    }

    private func yours(_ ctx: GraphicsContext) {
        ctx.fill(circle(216, 64, 58), with: .color(tone.opacity(selected ? 0.26 : 0.14)))
        var arm = Path()
        arm.move(to: CGPoint(x: 144, y: 104))
        arm.addQuadCurve(to: CGPoint(x: 198, y: 86), control: CGPoint(x: 174, y: 114))
        // the arm starts inside the shoulder, so it grows out of it
        figure(ctx, 124, 96, 60)
        ctx.stroke(arm, with: .color(tone), style: StrokeStyle(lineWidth: 15, lineCap: .round))
        phone(ctx, 216, 60, 80, 10, delay: 0.12)
        ctx.fill(circle(199, 85, 9), with: .color(tone))
        ctx.stroke(circle(199, 85, 9), with: .color(card), lineWidth: 3)
        pulses(ctx, 165, 106, 8)
        sensor(ctx, 165, 106, 8)
        for (i, r) in [17.0, 24, 31].enumerated() {
            let base = 0.85 - 0.2 * Double(i)
            let delay = 0.1 + 0.15 * Double(i)
            let alpha = t >= Self.pickS ? base : min(max((t - delay) / 0.36, 0), 1) * base
            var p = Path()
            p.addArc(
                center: CGPoint(x: 165, y: 106), radius: r, startAngle: .degrees(-108), endAngle: .degrees(-62),
                clockwise: false)
            ctx.stroke(p, with: .color(tone.opacity(alpha)), style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
        }
    }

    private func theirs(_ ctx: GraphicsContext) {
        ctx.fill(circle(240, 66, 62), with: .color(tone.opacity(selected ? 0.26 : 0.14)))
        figure(ctx, 52, 106, 30)
        pulses(ctx, 62, 110, 4.5)
        sensor(ctx, 62, 110, 4.5)
        phone(ctx, 82, 100, 30, 10, plain: true)
        ctx.fill(circle(74, 110, 4.5), with: .color(tone))
        ctx.stroke(circle(74, 110, 4.5), with: .color(card), lineWidth: 2)
        var high = Path()
        high.move(to: CGPoint(x: 86, y: 80))
        high.addCurve(
            to: CGPoint(x: 211, y: 44), control1: CGPoint(x: 90, y: 38), control2: CGPoint(x: 160, y: 24))
        var low = Path()
        low.move(to: CGPoint(x: 98, y: 104))
        low.addCurve(
            to: CGPoint(x: 211, y: 97), control1: CGPoint(x: 108, y: 92), control2: CGPoint(x: 168, y: 88))
        dotted(ctx, high)
        traveller(ctx, high, 4.5)
        cloud(ctx, 138.9, 40)  // the high arch's middle
        dotted(ctx, low)
        traveller(ctx, low, 3.5)
        bluetooth(ctx, 153.5, 92.2)  // the low arch's middle
        phone(ctx, 240, 66, 112, -6, chip: true, delay: 0.05)
    }

    private func circle(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }

    /// A person: a round head over rounded shoulders.
    private func figure(_ ctx: GraphicsContext, _ cx: CGFloat, _ cy: CGFloat, _ s: CGFloat) {
        ctx.fill(circle(cx, cy - s * 0.55, s * 0.3), with: .color(tone))
        let top = cy - s * 0.08
        var p = Path()
        p.move(to: CGPoint(x: cx - s * 0.62, y: cy + s * 0.62))
        p.addCurve(
            to: CGPoint(x: cx, y: top), control1: CGPoint(x: cx - s * 0.62, y: top + s * 0.12),
            control2: CGPoint(x: cx - s * 0.32, y: top))
        p.addCurve(
            to: CGPoint(x: cx + s * 0.62, y: cy + s * 0.62), control1: CGPoint(x: cx + s * 0.32, y: top),
            control2: CGPoint(x: cx + s * 0.62, y: top + s * 0.12))
        p.closeSubpath()
        ctx.fill(p, with: .color(tone))
    }

    /// A Libre seen from above: a white disc with the filament's dot, ringed in the tone.
    private func sensor(_ ctx: GraphicsContext, _ x: CGFloat, _ y: CGFloat, _ r: CGFloat) {
        ctx.fill(circle(x, y, r), with: .color(.white))
        ctx.fill(circle(x, y, r * 0.32), with: .color(Color(red: 0.725, green: 0.745, blue: 0.769)))
        ctx.stroke(circle(x, y, r), with: .color(tone), lineWidth: 2)
    }

    /// Three rings leaving the sensor on a pick, a quarter second apart.
    private func pulses(_ ctx: GraphicsContext, _ x: CGFloat, _ y: CGFloat, _ r: CGFloat) {
        for i in 0..<3 {
            let local = t - 0.25 * Double(i)
            guard local > 0, local < 1 else { continue }
            let e = 1 - (1 - local) * (1 - local)
            ctx.stroke(
                circle(x, y, r * (1 + 1.8 * e)), with: .color(tone.opacity(0.7 * (1 - e))), lineWidth: 1.5)
        }
    }

    private func dotted(_ ctx: GraphicsContext, _ path: Path) {
        ctx.stroke(
            path, with: .color(tone.opacity(0.75)), style: StrokeStyle(lineWidth: 2.5, lineCap: .round, dash: [1, 7]))
    }

    /// A dot travelling `path` in the first 1.1 s of a pick (close to linear, a soft start and end), fading at its ends.
    private func traveller(_ ctx: GraphicsContext, _ path: Path, _ r: CGFloat) {
        let travel = 1.1
        guard t > 0, t < travel else { return }
        let p = t / travel
        let at = path.trimmedPath(from: 0, to: max(Self.ease(p, 0.33, 0.12, 0.67, 0.88), 0.0001)).currentPoint
        guard let at else { return }
        let alpha = p < 0.12 ? p / 0.12 : p < 0.85 ? 1 : (1 - p) / 0.15
        ctx.fill(circle(at.x, at.y, r), with: .color(tone.opacity(alpha)))
    }

    /// A cloud (the internet): three soft shapes, outlined as one (each outline first, every fill over them).
    private func cloud(_ ctx: GraphicsContext, _ cx: CGFloat, _ cy: CGFloat) {
        let parts = [
            Path(roundedRect: CGRect(x: cx - 16, y: cy - 5, width: 32, height: 13), cornerRadius: 6.5),
            circle(cx - 6, cy - 5, 8), circle(cx + 5, cy - 8, 10),
        ]
        for p in parts { ctx.stroke(p, with: .color(line), lineWidth: 3) }
        for p in parts { ctx.fill(p, with: .color(screen)) }
    }

    /// A Bluetooth badge: the rune in the tone on a small disc.
    private func bluetooth(_ ctx: GraphicsContext, _ x: CGFloat, _ y: CGFloat) {
        ctx.fill(circle(x, y, 10), with: .color(screen))
        ctx.stroke(circle(x, y, 10), with: .color(line), lineWidth: 1.5)
        var rune = Path()
        rune.addLines([
            CGPoint(x: x - 3.6, y: y - 3.4), CGPoint(x: x + 3.4, y: y + 3.2), CGPoint(x: x, y: y + 6.4),
            CGPoint(x: x, y: y - 6.4), CGPoint(x: x + 3.4, y: y - 3.2), CGPoint(x: x - 3.6, y: y + 3.4),
        ])
        ctx.stroke(rune, with: .color(tone), style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
    }

    /// A phone `h` tall centred at `cx`, `cy`, turned `rot`°: the body, the screen and, unless `plain`, the range band
    /// and a glucose line without digits (`chip`: the followed person's portrait on top). On a pick the line scrolls
    /// left by one screen of new readings after `delay` s and lands on the same shape; the newest dot rides it.
    private func phone(
        _ ctx: GraphicsContext, _ cx: CGFloat, _ cy: CGFloat, _ h: CGFloat, _ rot: Double, plain: Bool = false,
        chip: Bool = false, delay: Double = 0
    ) {
        var ctx = ctx
        ctx.translateBy(x: cx, y: cy)
        ctx.rotate(by: .degrees(rot))
        ctx.translateBy(x: -cx, y: -cy)
        let w = h * 0.5, x0 = cx - w / 2, y0 = cy - h / 2, i = w * 0.08
        let bodyRect = CGRect(x: x0, y: y0, width: w, height: h)
        ctx.fill(Path(roundedRect: bodyRect, cornerRadius: w * 0.18), with: .color(bodyFill))
        ctx.stroke(Path(roundedRect: bodyRect, cornerRadius: w * 0.18), with: .color(line), lineWidth: 1.5)
        let sx = x0 + i, sy = y0 + i * 1.6, sw = w - 2 * i, sh = h - 3.2 * i
        ctx.fill(
            Path(roundedRect: CGRect(x: sx, y: sy, width: sw, height: sh), cornerRadius: w * 0.1), with: .color(screen))
        if !plain {
            func x(_ f: CGFloat) -> CGFloat { sx + sw * f }
            func y(_ f: CGFloat) -> CGFloat { sy + sh * f }
            if chip {
                ctx.fill(circle(x(0.22), y(0.14), sw * 0.11), with: .color(tone))
                ctx.fill(
                    Path(
                        roundedRect: CGRect(x: x(0.38), y: y(0.11), width: sw * 0.4, height: sh * 0.06),
                        cornerRadius: sh * 0.03), with: .color(line.opacity(0.45)))
            }
            ctx.fill(
                Path(CGRect(x: sx, y: y(0.42), width: sw, height: sh * 0.26)), with: .color(tone.opacity(0.2)))
            // the line, a bridge of new readings, then the line again one screen `d` later
            let d = sw
            let segments: [[CGFloat]] = [
                [x(0.06), y(0.66), x(0.3), y(0.3), x(0.52), y(0.82), x(0.82), y(0.5)],
                [x(0.82), y(0.5), x(0.92), y(0.39), x(0.98), y(0.78), x(0.06) + d, y(0.66)],
                [x(0.06) + d, y(0.66), x(0.3) + d, y(0.3), x(0.52) + d, y(0.82), x(0.82) + d, y(0.5)],
            ]
            var path = Path()
            path.move(to: CGPoint(x: segments[0][0], y: segments[0][1]))
            for c in segments {
                path.addCurve(
                    to: CGPoint(x: c[6], y: c[7]), control1: CGPoint(x: c[2], y: c[3]),
                    control2: CGPoint(x: c[4], y: c[5]))
            }
            let p = t >= Self.pickS ? 0 : min(max((t - delay) / 1.1, 0), 1)
            let eased = p < 0.5 ? 4 * p * p * p : 1 - pow(-2 * p + 2, 3) / 2
            let shift = d * CGFloat(eased)
            let edge = x(0.82)
            var clipped = ctx
            clipped.clip(to: Path(CGRect(x: x(0.06), y: sy, width: edge - x(0.06), height: sh)))
            clipped.translateBy(x: -shift, y: 0)
            clipped.stroke(path, with: .color(tone), style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
            ctx.fill(circle(edge, Self.y(at: edge + shift, in: segments), 3.5), with: .color(tone))
        }
        ctx.fill(
            Path(
                roundedRect: CGRect(x: cx - w * 0.15, y: y0 + h - i * 1.15, width: w * 0.3, height: i * 0.35),
                cornerRadius: 2), with: .color(line.opacity(0.5)))
    }

    /// The y of the cubic `segments` (each x0 y0 x1 y1 x2 y2 x3 y3, x growing) where x = `x`.
    static func y(at x: CGFloat, in segments: [[CGFloat]]) -> CGFloat {
        let c = segments.first { x <= $0[6] } ?? segments[segments.count - 1]
        func at(_ t: CGFloat, _ o: Int) -> CGFloat {
            let u = 1 - t
            return u * u * u * c[o] + 3 * u * u * t * c[o + 2] + 3 * u * t * t * c[o + 4] + t * t * t * c[o + 6]
        }
        var lo: CGFloat = 0, hi: CGFloat = 1
        for _ in 0..<24 {
            let mid = (lo + hi) / 2
            if at(mid, 0) < x { lo = mid } else { hi = mid }
        }
        return at((lo + hi) / 2, 1)
    }

    /// CSS-style cubic-bezier easing (x1, y1, x2, y2) at `p`.
    static func ease(_ p: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        func b(_ t: Double, _ a: Double, _ c: Double) -> Double {
            let u = 1 - t
            return 3 * u * u * t * a + 3 * u * t * t * c + t * t * t
        }
        var lo = 0.0, hi = 1.0
        for _ in 0..<24 {
            let mid = (lo + hi) / 2
            if b(mid, x1, x2) < p { lo = mid } else { hi = mid }
        }
        return b((lo + hi) / 2, y1, y2)
    }
}

/// The sources for `person`, one radio choice each with what it brings, grouped by what the person feels (*On this
/// network · works offline*, *Online · needs internet*, *From another Sukrara · internet or Bluetooth nearby*): setup's source step (without another
/// Sukrara when the Mac reads it online) and Settings › Source.
struct SourceKindChoice: View {
    let person: Person
    @Binding var selected: SourceKind
    /// The sources offered (nil = every one for `person`).
    var only: Set<SourceKind>? = nil

    var body: some View {
        let kinds = SourceKind.forPerson(person).filter { only?.contains($0) ?? true }
        VStack(alignment: .leading, spacing: 10) {
            ForEach(SourceGroup.allCases.filter { g in kinds.contains { $0.group == g } }, id: \.self) { group in
                VStack(alignment: .leading, spacing: 4) {
                    Text(SourceChoiceModel.groupName(group).uppercased())
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .accessibilityAddTraits(.isHeader)
                    Picker(selection: $selected) {
                        ForEach(kinds.filter { $0.group == group }, id: \.self) { kind in
                            if let spec = SourceSpec(person: person, kind: kind) {
                                // the app's or service's own icon, as Android's source rows (SourceIcon)
                                HStack(spacing: 10) {
                                    SourceIcon(kind: kind)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(SourceChoiceModel.title(spec))
                                        Text(SourceChoiceModel.means(spec)).font(.caption).foregroundStyle(.secondary)
                                            .fixedSize(horizontal: false, vertical: true)
                                    }
                                }
                                .padding(.vertical, 3)
                                .tag(kind)
                            }
                        }
                    } label: {
                        EmptyView()
                    }
                    .pickerStyle(.radioGroup)
                    .labelsHidden()
                }
            }
        }
    }
}

/// Settings › Source: whose glucose (two cards), where it comes from (radio rows), then that source's own rows: the
/// person this Mac follows (Follow…, the waiting code, Stop following…, Reconnect Now), Juggluco on this network
/// (its state, address and secret), a Nightscout site (how it's going, site, token, treatments both ways) or LibreLinkUp
/// (how it's going, account, whose glucose, sensor, sign out). A change applies at once (nothing to delete on the Mac:
/// the readings are each
/// source's own store); Back is just another pick.
struct SourcePane: View {
    @Bindable var model: SettingsModel
    var openFollow: () -> Void
    var reconnect: (() -> Void)?
    /// An unanswered Mac's *Answer…*: setup's two steps, preselected (nil in renders).
    var answer: (() -> Void)? = nil

    var body: some View {
        let choice = model.source
        let running = choice.running
        Form {
            if choice.stored == nil {
                Section {
                    HStack(alignment: .firstTextBaseline) {
                        Label(
                            L10n.t(
                                "Sukrara asks once more whose glucose this is and where it comes from. It keeps reading as now until you answer."
                            ),
                            systemImage: "info.circle")
                        Spacer()
                        if let answer { Button(L10n.t("Answer…"), action: answer) }
                    }
                }
            }
            Section(L10n.t("Whose glucose")) {
                PersonChoice(
                    selected: Binding(
                        get: { running?.person },
                        set: { p in
                            guard let p else { return }
                            let kind = running?.kind ?? .sukrara
                            if let spec = SourceSpec(person: p, kind: kind) { choice.choose(spec) }
                        }))
            }
            // the person first: the sources depend on whose glucose it is
            if let running {
                Section {
                    SourceKindChoice(
                        person: running.person,
                        selected: Binding(
                            get: { running.kind },
                            set: { k in
                                if let spec = SourceSpec(person: running.person, kind: k) {
                                    choice.choose(spec)
                                }
                            }))
                } header: {
                    Text(L10n.t("Where it comes from"))
                } footer: {
                    // only true while this Mac follows someone (said to no one else)
                    if model.follow.status.hasFollow {
                        Text(
                            L10n.t(
                                "Changing the source keeps the person this Mac follows, so switching back needs no new invite."
                            )
                        )
                        .formFooter()
                    }
                }
                switch running.kind {
                case .sukrara:
                    Section { FollowingRows(follow: model.follow, openFollow: openFollow) }
                    if case .following = model.follow.status, let reconnect {
                        Section {
                            HStack {
                                Spacer()
                                Button(L10n.t("Reconnect Now"), action: reconnect)
                                    .help(L10n.t("Reconnect over the internet and to the phone over Bluetooth now"))
                            }
                        }
                    }
                case .lanJuggluco:
                    Section {
                        JugglucoRows(model: model.juggluco)
                    } header: {
                        Text(verbatim: "Juggluco")
                    } footer: {
                        Text(
                            L10n.t(
                                "In Juggluco: Settings › Web server, with Local only off. Plain http on your home network only: readings travel unencrypted between the phone and this Mac."
                            )
                        )
                        .formFooter()
                    }
                    .onAppear(perform: model.juggluco.load)
                    .onDisappear {
                        model.juggluco.saveAddress()
                        model.juggluco.saveSecret()
                    }
                case .nightscout:
                    NightscoutRows(model: model.nightscout)
                        .onAppear(perform: model.nightscout.load)
                case .libreLinkUp:
                    LibreLinkUpRows(model: model.libreLinkUp)
                }
            }
        }
        .formStyle(.grouped)
    }
}
