// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.nightscout.site

import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.integrations.nightscout.NsDocs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.abs

/** One entry as the sync compares it: what a site document stands for, or a local treatment. */
data class SyncEntry(
    val id: String,
    val kind: TreatmentKind,
    val amount: Double,
    val at: Instant,
    val note: String,
)

/** What this phone remembers of the site: the ids it took from it, and the version of each own entry that went up. */
@Serializable
data class NsSyncLedger(
    val pulled: Set<String> = emptySet(),
    /** Own entry id → its `modifiedAt` (epoch ms) that the site has. */
    val pushed: Map<String, Long> = emptyMap(),
)

/** What one sync changes: entries to write here, ids to delete here, own entries to send, own entries to delete there. */
data class SyncPlan(
    val upsert: List<SyncEntry>,
    val delete: List<String>,
    val push: List<String>,
    val remove: List<String>,
) {
    val empty: Boolean get() = upsert.isEmpty() && delete.isEmpty() && push.isEmpty() && remove.isEmpty()
}

/**
 * Treatments both ways with a Nightscout source (contracts/sources/nightscout/treatments.json, docs/44 "Data"): the
 * site's arrive here read-only, this phone's go there by identifier, each side's edits and deletes reach the other, and
 * nothing is copied twice. [plan] is pure: the source reads the window, plans, applies, then updates the ledger.
 */
object NsTreatmentSync {
    private val LONG_NOTE = Regex("""^Long-acting insulin ([0-9]+(?:\.[0-9]+)?) U(?: · (.*))?$""")
    private val LOOP_EVENTS = setOf("SMB", "Temp Basal")
    private val NOTE_EVENTS = setOf("Note", "Announcement")

    /** The Sukrara id of a site document's entry ([field] insulin | carbs | note): reading it again never copies it. */
    fun pulledId(
        key: String,
        field: String,
    ): String = NsDocs.uuidV5("sukrara-ns_${key}_$field")

    /**
     * The entries [doc] stands for: its insulin (long-acting when `insulinType` says long, else rapid), its carbs and,
     * with neither, a note; the loop's own insulin (SMB, temp basals) and every other event stand for nothing. [ownId]
     * names a single entry (an own document read back) instead of the pulled ids.
     */
    fun entriesOf(
        doc: JsonObject,
        ownId: String? = null,
    ): List<SyncEntry> {
        val key = doc.text("identifier") ?: doc.text("_id") ?: return emptyList()
        val at = doc.time() ?: return emptyList()
        val event = doc.text("eventType")
        if (doc.bool("isSMB") == true || doc.text("type") == "SMB" || event in LOOP_EVENTS) return emptyList()
        val notes = doc.text("notes")?.trim().orEmpty()

        fun id(field: String) = ownId ?: pulledId(key, field)
        val insulinType = doc.text("insulinType").orEmpty()
        if (event == "Note" && insulinType.contains("long", ignoreCase = true)) {
            LONG_NOTE.matchEntire(notes)?.let { m ->
                return listOf(SyncEntry(id("insulin"), TreatmentKind.LongInsulin, m.groupValues[1].toDouble(), at, m.groupValues[2]))
            }
        }
        val out = ArrayList<SyncEntry>(2)
        doc.number("insulin")?.takeIf { it > 0 }?.let { u ->
            val kind = if (insulinType.contains("long", ignoreCase = true)) TreatmentKind.LongInsulin else TreatmentKind.RapidInsulin
            out += SyncEntry(id("insulin"), kind, u, at, notes)
        }
        doc.number("carbs")?.takeIf { it > 0 }?.let { g -> out += SyncEntry(id("carbs"), TreatmentKind.Carbs, g, at, notes) }
        if (out.isEmpty() && event in NOTE_EVENTS && notes.isNotEmpty()) out += SyncEntry(id("note"), TreatmentKind.Note, 0.0, at, notes)
        return if (ownId != null) out.take(1) else out
    }

    fun plan(
        from: Instant,
        to: Instant,
        site: List<JsonObject>,
        local: List<Treatment>,
        ledger: NsSyncLedger,
        device: String,
    ): SyncPlan {
        fun inWindow(t: Instant) = !t.isBefore(from) && !t.isAfter(to)
        val window = local.filter { inWindow(it.at) }
        val ownIds = local.filter { it.id !in ledger.pulled }.mapTo(HashSet()) { it.id } + ledger.pushed.keys
        val produced = LinkedHashMap<String, SyncEntry>()
        val oursOnSite = HashMap<String, JsonObject>()
        for (doc in site) {
            if (doc.bool("isValid") == false) continue
            val identifier = doc.text("identifier")
            val ours = (identifier != null && identifier in ownIds) || (doc.text("app") == NsDocs.APP && doc.text("device") == device)
            if (ours) {
                if (identifier != null) oursOnSite[identifier] = doc
                continue
            }
            for (e in entriesOf(doc)) produced.putIfAbsent(e.id, e)
        }
        val upsert = ArrayList<SyncEntry>()
        val delete = ArrayList<String>()
        val push = ArrayList<String>()
        val remove = ArrayList<String>()
        val byId = window.associateBy { it.id }
        for ((id, e) in produced) {
            val l = byId[id] ?: local.firstOrNull { it.id == id }
            if (l == null || l.deleted || !same(l, e)) upsert += e
        }
        for (l in window) {
            if (l.id in ledger.pulled) {
                if (!l.deleted && l.id !in produced) delete += l.id
                continue
            }
            val sent = ledger.pushed[l.id]
            if (l.deleted) {
                if (sent != null) remove += l.id
                continue
            }
            if (sent == null || l.modifiedAt.toEpochMilli() > sent) {
                push += l.id
                continue
            }
            val doc = oursOnSite[l.id]
            if (doc == null) {
                delete += l.id
            } else {
                entriesOf(doc, ownId = l.id).firstOrNull()?.takeIf { !same(l, it) }?.let { upsert += it }
            }
        }
        return SyncPlan(
            upsert.sortedWith(compareBy({ it.at }, { it.id })),
            delete.sorted(),
            push.sorted(),
            remove.sorted(),
        )
    }

    private fun same(
        l: Treatment,
        e: SyncEntry,
    ) = l.kind == e.kind && abs(l.amount - e.amount) < 1e-6 && l.at.epochSecond == e.at.epochSecond && l.note == e.note

    private fun JsonObject.text(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it != JsonNull }?.contentOrNull

    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it != JsonNull }?.booleanOrNull

    private fun JsonObject.number(k: String): Double? {
        val p = this[k] as? JsonPrimitive ?: return null
        if (p == JsonNull) return null
        return (p.doubleOrNull ?: p.contentOrNull?.toDoubleOrNull())?.takeIf { it.isFinite() }
    }

    /** `created_at` (ISO, a zone-less one as UTC), else `date` / `mills` (ms). */
    fun JsonObject.time(): Instant? {
        text("created_at")?.let { s ->
            runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull()?.let { return it }
            runCatching { LocalDateTime.parse(s).toInstant(ZoneOffset.UTC) }.getOrNull()?.let { return it }
        }
        for (k in listOf("date", "mills")) number(k)?.let { return Instant.ofEpochMilli(it.toLong()) }
        return null
    }
}
