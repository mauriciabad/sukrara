// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data.readings

import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.storage.ReadingsRepository
import app.mauri.sukrara.core.model.storage.StoredSpan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Instant

/**
 * The glucose hub's readings (#160): the history ([history], Room) once it is open, and before that — after a restart,
 * until the first unlock — this phone's own [recent] readings in device-protected storage. Every own reading also goes
 * to [recent] while the history is open, so the next restart starts with the last hours. [unlock] adds what arrived
 * before it to the history and switches over in one step: a reading arriving meanwhile waits for it, so none is lost
 * between the two and none is stored twice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HubReadings(
    history: () -> ReadingsRepository,
    private val recent: RecentReadings,
    /** The history can be read (the phone was unlocked since it started and [unlock] ran); this class sets it. */
    private val open: MutableStateFlow<Boolean>,
    /** The diagnostic log (what failed, never values). */
    private val log: (String) -> Unit,
) : ReadingsRepository {
    private val room by lazy(history)
    private val mutex = Mutex()

    /** True from [unlock] on (at once when the phone was already unlocked). */
    val historyOpen: StateFlow<Boolean> = open.asStateFlow()

    override fun window(from: Instant): Flow<List<Reading>> = open.flatMapLatest { if (it) room.window(from) else recent.window(from) }

    override fun latest(): Flow<Reading?> =
        open.flatMapLatest { if (it) room.latest() else recent.window(Instant.EPOCH).map { list -> list.lastOrNull() } }

    override suspend fun range(
        from: Instant,
        to: Instant,
    ): List<Reading> = if (open.value) room.range(from, to) else recent.window(from).first().filter { it.t.isBefore(to) }

    override suspend fun upsert(readings: List<Reading>) {
        if (readings.isEmpty()) return
        mutex.withLock {
            if (open.value) {
                room.upsert(readings)
                keep("recent readings") { recent.add(readings, locked = false) }
            } else {
                // the card and the alerts have it either way; a refused disk loses it only for the history
                keep("readings before the unlock") { recent.add(readings, locked = true) }
            }
        }
    }

    override suspend fun deleteBefore(cutoff: Instant): Int = if (open.value) room.deleteBefore(cutoff) else 0

    override suspend fun spanOf(sources: Set<SourceId>): StoredSpan = if (open.value) room.spanOf(sources) else StoredSpan.EMPTY

    override suspend fun deleteFrom(sources: Set<SourceId>): Int =
        mutex.withLock {
            keep("recent readings") { recent.deleteFrom(sources) }
            if (open.value) room.deleteFrom(sources) else 0
        }

    /**
     * The history is readable: the readings taken before the unlock go into it, then it is the hub's source. Also
     * at every start of an unlocked phone (a process that died before the unlock left some). A failing merge keeps
     * them for the next start and still opens the history.
     */
    suspend fun unlock() =
        mutex.withLock {
            try {
                val merged = recent.mergeInto(room)
                if (merged > 0) log("merged $merged readings taken before the unlock")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("merge before the unlock failed: ${e.javaClass.simpleName}")
            }
            open.value = true
        }

    private suspend fun keep(
        what: String,
        block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (e: IOException) {
            log("$what not saved: ${e.javaClass.simpleName}")
        }
    }
}
