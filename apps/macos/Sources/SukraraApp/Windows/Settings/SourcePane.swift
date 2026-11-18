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
        }
    }

    static func means(_ s: SourceSpec) -> String {
        switch s.kind {
        case .sukrara:
            s.person == .yours
                ? L10n.t("Your phone reads your sensor and shares it with this Mac · internet or Bluetooth nearby")
                : L10n.t("Their phone shares it with this Mac after an invite · internet or Bluetooth nearby")
        case .lanJuggluco:
            L10n.t("Juggluco on a phone on this Wi-Fi reads the sensor; Sukrara reads Juggluco · no internet needed")
        }
    }
}

/// The two big choices of *Whose glucose will Sukrara show?*: an illustration each, the answer and one line, side by
/// side. Picking one lifts it (tonal fill, a stronger outline, a spring) and its art's ring breathes out once with a
/// trackpad tap; Reduce Motion: it changes at once. VoiceOver: two buttons with the selected trait.
struct PersonChoice: View {
    @Binding var selected: Person?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(spacing: 12) {
            ForEach(Person.allCases, id: \.self) { p in
                let on = selected == p
                Button {
                    if !on { NSHapticFeedbackManager.defaultPerformer.perform(.alignment, performanceTime: .now) }
                    selected = p
                } label: {
                    VStack(alignment: .leading, spacing: 8) {
                        PersonArt(person: p, selected: on).frame(height: 120)
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
                    .scaleEffect(on && !reduceMotion ? 1.01 : 1)
                    .contentShape(RoundedRectangle(cornerRadius: 16))
                }
                .buttonStyle(.plain)
                .accessibilityElement(children: .combine)
                .accessibilityAddTraits(on ? [.isSelected, .isButton] : .isButton)
            }
        }
        .animation(reduceMotion ? nil : .spring(duration: 0.35, bounce: 0.25), value: selected)
    }
}

/// The art of `person`, drawn in the system's colours (light and dark), never a glucose number. Yours: an arm with a
/// sensor, its signal and the phone beside it; someone else's: a smaller person with a sensor, a dotted path to a phone
/// showing their line. The ring behind it breathes out once when it is picked (none with Reduce Motion).
struct PersonArt: View {
    let person: Person
    let selected: Bool
    @State private var breath: CGFloat = 1
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        // someone else's in purple (Android's tertiary): never a red or pink, the colours of a low
        let tone: Color = person == .yours ? .accentColor : .purple
        Canvas { ctx, size in
            let c = CGPoint(x: size.width / 2, y: size.height / 2)
            let r = size.height * 0.46
            ctx.fill(circle(c, r), with: .color(tone.opacity(selected ? 0.22 : 0.12)))
            if breath < 1 {
                ctx.stroke(
                    circle(c, r * (1 + 0.22 * breath)), with: .color(tone.opacity(0.5 * (1 - breath))), lineWidth: 3)
            }
            if person == .yours { yours(ctx, c, r, tone) } else { theirs(ctx, c, r, tone) }
        }
        .accessibilityHidden(true)
        .onChange(of: selected) { _, on in
            guard on, !reduceMotion else { return }
            breath = 0
            withAnimation(.easeOut(duration: 0.7)) { breath = 1 }
        }
    }

    private func circle(_ c: CGPoint, _ r: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
    }

    private func yours(_ ctx: GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ tone: Color) {
        var arm = ctx
        arm.translateBy(x: c.x, y: c.y)
        arm.rotate(by: .degrees(-28))
        let w = r * 0.42
        let rect = CGRect(x: -r * 1.25, y: -w / 2 + r * 0.18, width: r * 1.7, height: w)
        arm.fill(Path(roundedRect: rect, cornerRadius: w / 2), with: .color(Color(nsColor: .quaternaryLabelColor)))
        arm.stroke(Path(roundedRect: rect, cornerRadius: w / 2), with: .color(.secondary), lineWidth: 1.5)
        let sensor = CGPoint(x: c.x - r * 0.38, y: c.y + r * 0.02)
        disc(ctx, sensor, r * 0.2, tone)
        for i in 1...2 {
            let a = r * (0.18 + 0.16 * CGFloat(i))
            var p = Path()
            p.addArc(center: sensor, radius: a, startAngle: .degrees(-60), endAngle: .degrees(10), clockwise: false)
            ctx.stroke(
                p, with: .color(tone.opacity(0.85 - 0.25 * Double(i))),
                style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
        }
        phone(ctx, CGPoint(x: c.x + r * 0.62, y: c.y), r * 0.95, tone)
    }

    private func theirs(_ ctx: GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ tone: Color) {
        let who = CGPoint(x: c.x - r * 0.55, y: c.y + r * 0.05)
        ctx.fill(circle(CGPoint(x: who.x, y: who.y - r * 0.38), r * 0.2), with: .color(tone))
        ctx.fill(
            Path(
                roundedRect: CGRect(x: who.x - r * 0.3, y: who.y - r * 0.12, width: r * 0.6, height: r * 0.72),
                cornerRadius: r * 0.28),
            with: .color(tone))
        disc(ctx, CGPoint(x: who.x + r * 0.2, y: who.y + r * 0.12), r * 0.12, tone)
        let phoneAt = CGPoint(x: c.x + r * 0.62, y: c.y)
        var path = Path()
        path.move(to: CGPoint(x: who.x + r * 0.34, y: who.y - r * 0.42))
        path.addQuadCurve(
            to: CGPoint(x: phoneAt.x - r * 0.3, y: phoneAt.y - r * 0.5), control: CGPoint(x: c.x + r * 0.1, y: c.y - r))
        ctx.stroke(
            path, with: .color(tone.opacity(0.8)), style: StrokeStyle(lineWidth: 2.5, lineCap: .round, dash: [1, 7]))
        phone(ctx, phoneAt, r * 0.95, tone)
    }

    /// A Libre seen from above: a white disc with the filament's dot, ringed in the tone.
    private func disc(_ ctx: GraphicsContext, _ at: CGPoint, _ r: CGFloat, _ tone: Color) {
        ctx.fill(circle(at, r), with: .color(.white))
        ctx.fill(circle(at, r * 0.32), with: .color(.gray.opacity(0.5)))
        ctx.stroke(circle(at, r), with: .color(tone), lineWidth: 2)
    }

    /// A phone: the body, the screen, the in-range band and a gentle line ending in a dot (no digits).
    private func phone(_ ctx: GraphicsContext, _ at: CGPoint, _ h: CGFloat, _ tone: Color) {
        let w = h * 0.52
        let body = CGRect(x: at.x - w / 2, y: at.y - h / 2, width: w, height: h)
        ctx.fill(Path(roundedRect: body, cornerRadius: w * 0.18), with: .color(Color(nsColor: .quaternaryLabelColor)))
        ctx.stroke(Path(roundedRect: body, cornerRadius: w * 0.18), with: .color(.secondary), lineWidth: 1.5)
        let inset = w * 0.1
        let screen = CGRect(
            x: body.minX + inset, y: body.minY + inset * 1.6, width: w - inset * 2, height: h - inset * 3.2)
        ctx.fill(Path(roundedRect: screen, cornerRadius: w * 0.08), with: .color(Color(nsColor: .textBackgroundColor)))
        ctx.fill(
            Path(
                CGRect(
                    x: screen.minX, y: screen.minY + screen.height * 0.38, width: screen.width,
                    height: screen.height * 0.3)),
            with: .color(tone.opacity(0.18)))
        var line = Path()
        line.move(to: CGPoint(x: screen.minX + screen.width * 0.06, y: screen.minY + screen.height * 0.6))
        let end = CGPoint(x: screen.minX + screen.width * 0.82, y: screen.minY + screen.height * 0.5)
        line.addCurve(
            to: end, control1: CGPoint(x: screen.minX + screen.width * 0.3, y: screen.minY + screen.height * 0.25),
            control2: CGPoint(x: screen.minX + screen.width * 0.55, y: screen.minY + screen.height * 0.8))
        ctx.stroke(line, with: .color(tone), style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
        ctx.fill(circle(end, 3.5), with: .color(tone))
    }
}

/// The sources for `person`, one radio choice each with what it brings (setup's source step and Settings › Source).
struct SourceKindChoice: View {
    let person: Person
    @Binding var selected: SourceKind

    var body: some View {
        Picker(selection: $selected) {
            ForEach(SourceKind.forPerson(person), id: \.self) { kind in
                if let spec = SourceSpec(person: person, kind: kind) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(SourceChoiceModel.title(spec))
                        Text(SourceChoiceModel.means(spec)).font(.caption).foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
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

/// Settings › Source: whose glucose (two cards), where it comes from (radio rows), then that source's own rows: the
/// person this Mac follows (Follow…, the waiting code, Stop following…, Reconnect Now) or Juggluco on this network
/// (its state, address and secret). A change applies at once (nothing to delete on the Mac: the readings are each
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
                }
            }
        }
        .formStyle(.grouped)
    }
}
