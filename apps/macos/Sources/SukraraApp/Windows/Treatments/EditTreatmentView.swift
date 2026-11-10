// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import SukraraCore
import SwiftUI

/// Edit Treatment (a sheet over the Treatments window, for an entry this Mac wrote): its amount, or a note's text, and
/// *When?* (the entry's own time until another is picked). The same limits as Add Treatment; Save is off while
/// nothing changed or the amount is outside them. Return saves, Escape cancels.
struct EditTreatmentView: View {
    let treatment: Treatment
    /// The new amount (0 for a note), note and time (epoch s).
    let save: (_ amount: Double, _ note: String, _ at: Int64) -> Void
    let cancel: () -> Void
    var now: () -> Date = Date.init
    @State private var text: String
    /// nil = the entry's own time.
    @State private var when: TreatmentForm.When?
    @State private var pickTime = false
    @FocusState private var focused: Bool

    init(
        treatment: Treatment, save: @escaping (Double, String, Int64) -> Void, cancel: @escaping () -> Void
    ) {
        self.treatment = treatment
        self.save = save
        self.cancel = cancel
        _text = State(initialValue: Self.text(of: treatment))
    }

    /// What the field starts with: the amount as the rows write it ("4.5"), or the note.
    static func text(of t: Treatment) -> String { t.k == .note ? t.n : TreatmentsView.amount(t.a) }

    /// The edit as typed: (amount, note), or nil when it can't be saved (an amount outside the limits, an empty note).
    static func edited(_ t: Treatment, text: String) -> (amount: Double, note: String)? {
        if t.k == .note {
            let n = text.trimmingCharacters(in: .whitespacesAndNewlines)
            return n.isEmpty ? nil : (0, Treatment.truncated(n))
        }
        guard case .value(let a) = TreatmentForm.parse(text, grams: t.k == .carbs) else { return nil }
        return (a, t.n)
    }

    /// OK on the picker with the entry's own clock time: no change (the picker only offers the last day, so an older
    /// entry would otherwise move to today or yesterday).
    static func isOwnTime(_ picked: TreatmentForm.When, of t: Treatment, calendar: Calendar = .current) -> Bool {
        guard case .at(let p) = picked else { return false }
        let parts: Set<Calendar.Component> = [.hour, .minute]
        return calendar.dateComponents(parts, from: Date(timeIntervalSince1970: TimeInterval(p)))
            == calendar.dateComponents(parts, from: Date(timeIntervalSince1970: TimeInterval(t.t)))
    }

    private var at: Int64 { when?.resolve(now: Int64(now().timeIntervalSince1970)) ?? treatment.t }

    private var result: (amount: Double, note: String)? {
        guard let e = Self.edited(treatment, text: text),
            e.amount != treatment.a || e.note != treatment.n || at != treatment.t
        else { return nil }
        return e
    }

    private var invalid: Bool {
        treatment.k != .note && TreatmentForm.parse(text, grams: treatment.k == .carbs) == .invalid
    }

    /// The chip: the entry's day and time ("Today 14:05", "Monday 26 Oct 14:05") until another time is picked.
    private var whenLabel: String {
        if let when { return AddTreatmentView.whenLabel(when, now: now()) }
        let d = Date(timeIntervalSince1970: TimeInterval(treatment.t))
        let cal = Calendar.current
        if cal.isDate(d, inSameDayAs: now()) { return L10n.t("Today \(Clock.time(d))") }
        if let y = cal.date(byAdding: .day, value: -1, to: now()), cal.isDate(d, inSameDayAs: y) {
            return L10n.t("Yesterday \(Clock.time(d))")
        }
        return d.formatted(.dateTime.weekday(.wide).day().month(.abbreviated)) + " " + Clock.time(d)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .firstTextBaseline) {
                Text(L10n.t("Edit Treatment")).font(.title2.weight(.semibold))
                Spacer()
                Button {
                    pickTime = true
                } label: {
                    Label(whenLabel, systemImage: "clock")
                }
                .buttonStyle(.bordered)
                .buttonBorderShape(.capsule)
                .help(L10n.t("When it happened"))
                .accessibilityLabel(L10n.t("When: \(whenLabel)"))
                .popover(isPresented: $pickTime, arrowEdge: .bottom) {
                    WhenPicker(when: when ?? .at(treatment.t)) { picked in
                        if let picked { when = Self.isOwnTime(picked, of: treatment) ? nil : picked }
                        pickTime = false
                    }
                }
            }
            VStack(alignment: .leading, spacing: 4) {
                Label(TreatmentKindLook.name(treatment.k), systemImage: TreatmentKindLook.symbol(treatment.k))
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .accessibilityHidden(true)
                if treatment.k == .note {
                    TextField(
                        L10n.t("Note"),
                        text: Binding(get: { text }, set: { text = TreatmentForm.filterNote($0, old: text) }),
                        prompt: Text(L10n.t("Pizza, exercise, sick…"))
                    )
                    .textFieldStyle(.roundedBorder)
                    .focused($focused)
                    .onSubmit(commit)
                    .accessibilityLabel(L10n.t("Note"))
                } else {
                    HStack(spacing: 4) {
                        TextField(
                            TreatmentKindLook.name(treatment.k),
                            text: Binding(get: { text }, set: { text = TreatmentForm.filterAmount($0) }),
                            prompt: Text(verbatim: "0")
                        )
                        .textFieldStyle(.roundedBorder)
                        .focused($focused)
                        .onSubmit(commit)
                        .accessibilityLabel(TreatmentKindLook.name(treatment.k))
                        .overlay(RoundedRectangle(cornerRadius: 5).stroke(invalid ? Color.red : .clear))
                        Text(TreatmentKindLook.unit(treatment.k)).foregroundStyle(.secondary).accessibilityHidden(true)
                    }
                }
            }
            if invalid {
                Text(L10n.t("Insulin up to 100u, carbs up to 500g.")).font(.callout).foregroundStyle(.red)
            }
            HStack {
                Spacer()
                Button(L10n.t("Cancel"), action: cancel).keyboardShortcut(.cancelAction)
                Button(L10n.t("Save"), action: commit)
                    .keyboardShortcut(.defaultAction)
                    .disabled(result == nil)
            }
            .controlSize(.large)
        }
        .padding(20)
        .frame(width: 360)
        .fixedSize(horizontal: false, vertical: true)
        .onAppear { focused = true }
    }

    private func commit() {
        guard let r = result else { return }
        NSHapticFeedbackManager.defaultPerformer.perform(.levelChange, performanceTime: .now)
        save(r.amount, r.note, at)
    }
}
