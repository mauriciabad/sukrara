// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data.readings

import app.mauri.sukrara.core.data.CorruptionLog
import app.mauri.sukrara.core.data.files.FileCodec
import app.mauri.sukrara.core.data.files.FileLoad
import app.mauri.sukrara.core.data.files.FileStore
import app.mauri.sukrara.core.data.json.SukraraJson
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.storage.ReadingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Duration
import java.time.Instant

/**
 * This phone's own readings in **device-protected** storage (#160, docs/30-direct-boot.md): readable before the first
 * unlock after a restart, when the history (Room) isn't.
 * - [window]: the readings of the last [KEEP] (`recent-readings.json`, rewritten with every reading, also while the
 *   phone is unlocked), so right after a restart the card, the glucose notification and the alert rules (rates, how
 *   long a low has lasted) have what came before it; in memory it grows up to [WINDOW] while the phone stays locked.
 * - [lockedReadings]: every reading taken before the unlock (`locked-readings.csv`, appended), which [mergeInto] adds to
 *   the history at the unlock and then deletes: no gap, and no duplicate (the history keeps one reading per time and
 *   source, so a merge cut short and repeated adds nothing twice).
 *
 * Only this phone's own sensor's readings: never a followed person's ([SourceId.Follower]) or imported ones. Their
 * values are health data outside credential-encrypted storage: docs/ARCHITECTURE.md "Android storage" says what that
 * costs. A file of another version is not read (no backwards compatibility): it starts empty.
 */
class RecentReadings(
    recentFile: File,
    private val lockedFile: File,
    corruption: CorruptionLog,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
) {
    private val recent = FileStore(recentFile, Codec, corruption, clock, io)
    private val mutex = Mutex()

    /** Every own reading of the last [WINDOW] this process knows, oldest first; null until the file was read. */
    private val current = MutableStateFlow<List<Reading>?>(null)

    /** The own readings at or after [from] (at most [WINDOW] back), oldest first, updated as new ones arrive. */
    fun window(from: Instant): Flow<List<Reading>> =
        flow {
            mutex.withLock { loaded() }
            emitAll(current.filterNotNull().map { all -> all.filter { !it.t.isBefore(from) } })
        }.distinctUntilChanged()

    /**
     * Adds this phone's own readings among [readings] to the window (and its file); [locked]: also to the readings the
     * unlock merges into the history. The window in memory is updated first, so a disk that refuses still leaves the
     * reading with the card and the alerts; the [IOException] is thrown after.
     */
    suspend fun add(
        readings: List<Reading>,
        locked: Boolean,
    ) {
        val own = readings.filter { it.source !in SourceId.Follower && it.source !in SourceId.Imported }
        if (own.isEmpty()) return
        mutex.withLock {
            val now = clock.now()
            val byKey = (loaded() + own).associateBy { it.t to it.source }
            val next = byKey.values.filter { !it.t.isBefore(now - WINDOW) }.sortedBy { it.t }
            current.value = next
            var failure: IOException? = null
            if (locked) failure = runCatchingIo { appendLocked(own) }
            failure = runCatchingIo { recent.save(next.filter { !it.t.isBefore(now - KEEP) }) } ?: failure
            failure?.let { throw it }
        }
    }

    /** The readings taken before the unlock that the history doesn't have yet, oldest first. */
    suspend fun lockedReadings(): List<Reading> = withContext(io) { mutex.withLock { readLocked() } }

    /**
     * Adds the readings taken before the unlock to [history] (one reading per time and source: nothing twice), then
     * forgets them. Returns how many there were.
     */
    suspend fun mergeInto(history: ReadingsRepository): Int {
        val waiting = lockedReadings()
        waiting.chunked(MERGE_BATCH).forEach { history.upsert(it) }
        withContext(io) {
            mutex.withLock {
                if (lockedFile.exists() &&
                    !lockedFile.delete()
                ) {
                    throw IOException("can't delete ${lockedFile.name}")
                }
            }
        }
        return waiting.size
    }

    /** A role change: every reading of [sources] goes, here and in the readings waiting for the unlock. */
    suspend fun deleteFrom(sources: Set<SourceId>) {
        if (sources.isEmpty()) return
        mutex.withLock {
            val all = loaded()
            val kept = all.filter { it.source !in sources }
            if (kept.size != all.size) {
                current.value = kept
                recent.save(kept.filter { !it.t.isBefore(clock.now() - KEEP) })
            }
            withContext(io) {
                val waiting = readLocked()
                if (waiting.any { it.source in sources }) {
                    FileStore.writeAtomically(lockedFile, encodeLocked(waiting.filter { it.source !in sources }))
                }
            }
        }
    }

    private suspend fun loaded(): List<Reading> {
        current.value?.let { return it }
        val read =
            when (val load = recent.load()) {
                is FileLoad.Loaded -> load.value

                // missing, or damaged (copied aside and reported by the store): the window starts empty
                else -> emptyList()
            }
        val since = clock.now() - WINDOW
        return read.filter { !it.t.isBefore(since) }.sortedBy { it.t }.also { current.value = it }
    }

    private suspend fun appendLocked(readings: List<Reading>) =
        withContext(io) {
            lockedFile.parentFile?.mkdirs()
            val fresh = !lockedFile.exists() || lockedFile.length() == 0L
            FileOutputStream(lockedFile, true).use { out ->
                if (fresh) out.write("$LOCKED_HEADER\n".encodeToByteArray())
                out.write(readings.joinToString("") { row(it) + "\n" }.encodeToByteArray())
                out.fd.sync()
            }
        }

    /** Lines that can't be read (a write cut short by a crash) are skipped; a file of another version is not read. */
    private fun readLocked(): List<Reading> {
        if (!lockedFile.exists()) return emptyList()
        val lines = lockedFile.readLines()
        if (lines.firstOrNull() != LOCKED_HEADER) return emptyList()
        return lines
            .drop(1)
            .mapNotNull(::parseRow)
            .distinctBy { it.t to it.source }
            .sortedBy { it.t }
    }

    private fun encodeLocked(readings: List<Reading>): ByteArray =
        (listOf(LOCKED_HEADER) + readings.map(::row)).joinToString("\n", postfix = "\n").encodeToByteArray()

    private suspend fun runCatchingIo(block: suspend () -> Unit): IOException? =
        try {
            block()
            null
        } catch (e: IOException) {
            e
        }

    @Serializable
    private data class Saved(
        val v: Int,
        val readings: List<Row> = emptyList(),
    )

    @Serializable
    private data class Row(
        val t: Long,
        val mgdl: Int,
        val rate: Double? = null,
        val source: String,
    )

    private object Codec : FileCodec<List<Reading>> {
        override fun encode(value: List<Reading>): ByteArray =
            SukraraJson
                .encodeToString(
                    Saved.serializer(),
                    Saved(VERSION, value.map { Row(it.t.toEpochMilli(), it.mgdl, it.rateMgdlPerMin, it.source.id) }),
                ).encodeToByteArray()

        override fun decode(bytes: ByteArray): List<Reading> {
            val saved = SukraraJson.decodeFromString(Saved.serializer(), bytes.decodeToString())
            if (saved.v != VERSION) return emptyList()
            return saved.readings.map { Reading(Instant.ofEpochMilli(it.t), it.mgdl, it.rate, SourceId(it.source)) }
        }
    }

    companion object {
        /** What the window's file keeps: the last 3 hours. */
        val KEEP: Duration = Duration.ofHours(3)

        /** What the window keeps in memory while the phone stays locked (the hub's own window). */
        val WINDOW: Duration = Duration.ofHours(24)

        private const val VERSION = 1
        private const val LOCKED_HEADER = "sukrara-locked-readings v1"
        private const val MERGE_BATCH = 500

        private fun row(r: Reading) = "${r.t.toEpochMilli()},${r.mgdl},${r.rateMgdlPerMin ?: ""},${r.source.id}"

        private fun parseRow(line: String): Reading? {
            val p = line.split(',')
            if (p.size != 4 || p[3].isEmpty()) return null
            val t = p[0].toLongOrNull() ?: return null
            val mgdl = p[1].toIntOrNull() ?: return null
            val rate = if (p[2].isEmpty()) null else p[2].toDoubleOrNull() ?: return null
            return Reading(Instant.ofEpochMilli(t), mgdl, rate, SourceId(p[3]))
        }
    }
}
