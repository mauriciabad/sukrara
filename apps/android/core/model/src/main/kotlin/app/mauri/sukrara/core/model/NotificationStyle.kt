// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

/**
 * Where the glucose shows in the status bar, and so what the glucose notification can be (docs/36 §11; Settings ›
 * Glucose notification and setup's *Glucose notification* step): one choice, because a Live Update can't hold a graph
 * and the root value makes the chip and the number icon redundant.
 */
enum class NotificationStyle {
    /** The Live Update chip next to the clock; the notification is the basic Live Update (no graph). */
    Chip,

    /** The notification's icon is the number; the notification is the card with the graph. */
    Icon,

    /** Root: the value as text in the status bar, the notification's icon hidden; the notification is the card. */
    Root,
}
