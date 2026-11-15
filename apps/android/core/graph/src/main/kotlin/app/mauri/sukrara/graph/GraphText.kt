// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import app.mauri.sukrara.graph.stats.Band
import app.mauri.sukrara.settings.SettingsSpec

/**
 * The text forms of the structured graph settings: how they are stored and sent in the wearer's settings event
 * (contracts/protocol/PROTOCOL-v2.md "Settings"; the Mac's `SettingsText` reads the same spellings). Every parser takes
 * anything and returns a value the settings accept, or null = use the default.
 */
object GraphText {
    /** "TIR:86400:1:1,TIR:604800:1:1,GMI:1209600:1:0" (kind:window s:dimmed:on). */
    fun slotsText(slots: List<RingSpec>): String = slots.joinToString(",", transform = ::slotText)

    fun slotText(r: RingSpec): String = "${r.kind.name}:${r.windowS}:${if (r.faded) 1 else 0}:${if (r.on) 1 else 0}"

    /** Exactly three valid metrics, else null. */
    fun parseSlots(text: String): List<RingSpec>? = text.split(",").mapNotNull(::parseSlot).takeIf { it.size == 3 }

    /** The periods a metric of [kind] can have: GMI from 7 days (an estimated A1c needs weeks), the others `metricWindow`. */
    fun windowSpec(kind: SlotKind) = if (kind == SlotKind.GMI) SettingsSpec.GMI_WINDOW else SettingsSpec.METRIC_WINDOW

    /**
     * One metric ("TIR:2592000:1:1"); null when malformed. The period snaps to its kind's format ([windowSpec]); a
     * missing dimmed or on field reads as 1.
     */
    fun parseSlot(text: String): RingSpec? {
        val p = text.trim().split(":")
        if (p.size < 2) return null
        val kind = SlotKind.entries.firstOrNull { it.name == p[0] } ?: return null
        val window = p[1].toDoubleOrNull() ?: return null
        return RingSpec(
            windowSpec(kind).coerce(window).toLong(),
            kind = kind,
            faded = p.getOrNull(2) != "0",
            on = p.getOrNull(3) != "0",
        )
    }

    /** "TIGHT,TARGET,LOW,VERY_LOW,HIGH,VERY_HIGH": each of the six bands once, else null. */
    fun parseRingOrder(text: String): List<Band>? {
        val order = text.split(",").map { it.trim() }.mapNotNull { n -> Band.entries.firstOrNull { it.name == n } }
        return order.takeIf { it.size == Band.entries.size && it.toSet().size == Band.entries.size }
    }

    fun ringOrderText(order: List<Band>): String = order.joinToString(",") { it.name }

    /** "100,120" → distinct values the `line` format accepts, lowest first, at most its `maxItems`. */
    fun parseLines(text: String): List<Int> {
        val spec = SettingsSpec.LINE
        return text
            .split(",")
            .mapNotNull { it.trim().toDoubleOrNull() }
            .filter(spec::valid)
            .map { it.toInt() }
            .distinct()
            .sorted()
            .take(spec.maxItems)
    }

    fun linesText(lines: List<Int>): String = lines.joinToString(",")
}
