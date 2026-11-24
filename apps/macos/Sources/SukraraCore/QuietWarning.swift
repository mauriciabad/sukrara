// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// THE rule of the quiet-alerts warning (schema `alerts.quietHidden`; Android's `QuietWarning` is the same): the system
/// lets Sukrara's alerts through but turned some of them down (the Mac: banners or sound off; Android: an alert
/// channel below sound). The person may hide the warning on purpose (another device next to them rings), for exactly
/// what is down then. It shows again when anything else goes down, and whatever comes back up leaves the hidden list,
/// so turning it down again later (by accident, say) warns again.
public enum QuietWarning {
    /// The warning shows: something is down that the person didn't hide.
    public static func shown(down: Set<String>, hidden: Set<String>) -> Bool { !down.isSubset(of: hidden) }

    /// What stays hidden: only what is still down (stored again when it shrinks; empty once all is back up).
    public static func kept(down: Set<String>, hidden: Set<String>) -> Set<String> { hidden.intersection(down) }

    /// The stored form: the parts, sorted, comma-separated ("" = nothing hidden).
    public static func encode(_ parts: Set<String>) -> String { parts.sorted().joined(separator: ",") }

    public static func decode(_ text: String) -> Set<String> {
        Set(text.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty })
    }
}
