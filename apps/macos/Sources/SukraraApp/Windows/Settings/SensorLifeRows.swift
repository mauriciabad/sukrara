// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Observation
import SukraraCore
import SwiftUI

/// The sensor's life in Settings › Source for a source that doesn't say all of it (docs/44 decision 10,
/// contracts/sources/sensor-life.json): when it started (the source's, or the person's manual start for a Nightscout
/// site), how long it lasts (the source's wear length, schema `source.*Wear`) and when it ends. Whatever the Mac reads
/// takes them; the Mac rings no sensor reminders (the card's warm-up and time left, *Sensor ended* and the no-data
/// quiet follow them).
@Observable
public final class SensorLifeModel {
    @ObservationIgnored let store: any SettingsStoring
    /// The sensor the rule gives now (`AppModel.sourceSensor`; nil = unknown, or nothing read yet).
    @ObservationIgnored var sensor: () -> SensorLives.Sensor? = { nil }
    @ObservationIgnored var now: () -> Date = { Date() }
    /// The start picker shows, with the start being picked.
    var picking = false
    var draft = Date()
    private var revision = 0

    init(store: any SettingsStoring) { self.store = store }

    /// `kind`'s wear length in minutes.
    func wear(_ kind: SourceKind) -> Double {
        _ = revision
        return kind.wear.map { store.number($0) } ?? 0
    }

    func setWear(_ v: Double, for kind: SourceKind) {
        guard let n = kind.wear, store.setNumber(v, for: n) else { return }
        revision += 1
    }

    /// The manual start (epoch s), nil = none.
    var manualStart: Int? {
        _ = revision
        let v = Int(store.value(MacSettings.manualStart))
        return v > 0 ? v : nil
    }

    /// The sensor of `kind` as the rule gives it: the running source's, else (nothing read yet) the manual start alone.
    func life(_ kind: SourceKind) -> SensorLives.Sensor? {
        _ = revision
        if let s = sensor() { return s }
        return SensorLifeSettings(store: store).life(kind, said: nil, now: now().timeIntervalSince1970)
    }

    /// Opens the picker on the manual start, else now (the usual case: the sensor was just applied).
    func pick() {
        draft = manualStart.map { Date(timeIntervalSince1970: TimeInterval($0)) } ?? now()
        picking = true
    }

    /// The earliest start the picker takes: one that leaves the sensor running now.
    func earliest(_ kind: SourceKind) -> Date {
        Date(timeIntervalSince1970: SensorLives.earliestStart(wearMin: wear(kind), now: now().timeIntervalSince1970))
    }

    /// Why `draft` can't be saved: later than now, or a sensor already ended at that start; nil = it can.
    func problem(_ kind: SourceKind) -> String? {
        let t = draft.timeIntervalSince1970.rounded(.down)
        if t > now().timeIntervalSince1970 { return L10n.t("Later than now") }
        let end = Date(timeIntervalSince1970: t + max(wear(kind), 0) * 60)
        if end <= now() { return L10n.t("This sensor would have ended on \(SensorLifeRows.day(end))") }
        return nil
    }

    /// The end `draft` gives.
    func draftEnd(_ kind: SourceKind) -> Date { draft.addingTimeInterval(wear(kind) * 60) }

    func save(_ kind: SourceKind) {
        guard problem(kind) == nil else { return }
        store.set(draft.timeIntervalSince1970.rounded(.down), for: MacSettings.manualStart)
        picking = false
        revision += 1
    }

    func removeStart() {
        store.set(0.0, for: MacSettings.manualStart)
        picking = false
        revision += 1
    }
}

/// Settings › Source › *Sensor* (Nightscout, LibreLinkUp, Juggluco on the network): *Started* (the date, where it
/// came from; for a Nightscout site *Set Start…* / *Change…* opens a date-and-time picker bounded to now and to the
/// wear length, with the end it gives and *Remove Start*), *Lasts* (THE number input, the source's format: LibreLinkUp's
/// 14 / 15 days) and *End*.
struct SensorLifeRows: View {
    @Bindable var model: SensorLifeModel
    let kind: SourceKind
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        // redrawn as the source's snapshots come (each minute or so: the end and "ended" move on with them)
        Section {
            // the picker takes the Started row's place while it shows (one *Started*, never two)
            if model.picking { picker } else { started }
            if let format = kind.wear?.format {
                NumberSettingRow(
                    label: L10n.t("Lasts"), format: format,
                    value: Binding(get: { model.wear(kind) }, set: { model.setWear($0, for: kind) }))
            }
            LabeledContent(L10n.t("End")) { Text(endText) }
        } header: {
            Text(L10n.t("Sensor"))
        } footer: {
            Text(L10n.t("\(Self.name(kind)) doesn't say how long a sensor lasts.")).formFooter()
        }
        .animation(reduceMotion ? nil : .snappy, value: model.picking)
    }

    /// The source's own name (brands, not translated).
    static func name(_ kind: SourceKind) -> String {
        switch kind {
        case .nightscout: "Nightscout"
        case .libreLinkUp: "LibreLinkUp"
        case .lanJuggluco: "Juggluco"
        case .sukrara: "Sukrara"
        }
    }

    /// "Mon 16 Nov, 09:12".
    static func day(_ d: Date) -> String {
        "\(d.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated).locale(L10n.locale))), \(Clock.time(d))"
    }

    private var takesStart: Bool { kind.life.takesManualStart }

    private var startText: String {
        guard let s = model.life(kind) else { return L10n.t("Not known") }
        let day = Self.day(Date(timeIntervalSince1970: TimeInterval(s.start)))
        let from = s.from == .manual ? L10n.t("set by you") : L10n.t("from \(Self.name(kind))")
        return "\(day) · \(from)"
    }

    private var endText: String {
        guard let s = model.life(kind) else { return L10n.t("Not known") }
        let end = Date(timeIntervalSince1970: TimeInterval(s.end))
        return end <= model.now() ? L10n.t("Ended") : Self.day(end)
    }

    private var started: some View {
        LabeledContent {
            HStack {
                Text(startText)
                if takesStart {
                    Button(model.manualStart == nil ? L10n.t("Set Start…") : L10n.t("Change…"), action: model.pick)
                        .accessibilityLabel(
                            model.manualStart == nil ? L10n.t("Set Start…") : L10n.t("Change the sensor's start"))
                    if model.manualStart != nil {
                        Button(L10n.t("Remove Start"), action: model.removeStart)
                    }
                }
            }
        } label: {
            Text(L10n.t("Started"))
        }
    }

    /// The start being picked: a compact date and time field (a calendar on click), bounded so it can't be ahead of now
    /// or already ended; the end it gives (or why it can't be saved), then *Cancel* · *Save*.
    private var picker: some View {
        VStack(alignment: .leading, spacing: 6) {
            DatePicker(
                L10n.t("Started"), selection: $model.draft, in: model.earliest(kind)...model.now(),
                displayedComponents: [.date, .hourAndMinute])
            let problem = model.problem(kind)
            Text(problem ?? L10n.t("Ends \(Self.day(model.draftEnd(kind)))"))
                .font(.caption)
                .foregroundStyle(problem == nil ? Color.secondary : Color.red)
                .contentTransition(.numericText())
                .animation(reduceMotion ? nil : .snappy, value: problem ?? "")
            DialogButtons(
                L10n.t("Save"), enabled: problem == nil, cancel: { model.picking = false },
                confirm: { model.save(kind) })
        }
    }
}
