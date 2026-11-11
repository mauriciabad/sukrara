// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.history

import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.storage.HistoryImport
import app.mauri.sukrara.core.model.storage.HistoryImportStore
import app.mauri.sukrara.core.model.storage.ImportSource
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import app.mauri.sukrara.designsystem.errors.Trouble
import app.mauri.sukrara.designsystem.errors.UserErrors
import app.mauri.sukrara.integrations.tidepool.TidepoolClient
import app.mauri.sukrara.integrations.tidepool.TpFailure
import app.mauri.sukrara.integrations.tidepool.TpSignedOut
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Duration
import java.time.Instant

/** What one import did, counted as it goes. */
data class ImportTally(
    val readings: Int = 0,
    val entries: Int = 0,
    /** Readings and entries that were here already (or the same one twice in what was read). */
    val alreadyHere: Int = 0,
    /** Older than the history the phone keeps ([HistoryImport.PERIOD]), or newer than [HistoryImport.NEWEST_AGE]. */
    val outsidePeriod: Int = 0,
    /** Rows that are nothing Sukrara keeps (finger-sticks, ketones, food in servings). */
    val skipped: Int = 0,
) {
    val added: Int get() = readings + entries
}

/** Why an import ended without finishing. */
sealed interface ImportFailure {
    /** The chosen file isn't a glucose file (LibreView's "Glucose Data" layout). */
    data object NotGlucoseData : ImportFailure

    /** Its times are in a layout Sukrara doesn't know: [example] is the first one it couldn't read, as written. */
    data class UnknownDates(
        val example: String,
    ) : ImportFailure

    /** The file couldn't be opened or read. */
    data object Unreadable : ImportFailure

    /** Not signed in to Tidepool (or Tidepool ended the session): sign in on the Tidepool page. */
    data object SignedOut : ImportFailure

    /** Network or server trouble that didn't pass. */
    data class Network(
        val why: Trouble,
    ) : ImportFailure
}

/** The import's state, for the page and the Share with apps row. */
sealed interface ImportState {
    data object Idle : ImportState

    data class Running(
        val source: ImportSource,
        /** Weeks of history done of [steps] (a file: chunks of it). */
        val step: Int,
        val steps: Int,
        val tally: ImportTally,
    ) : ImportState

    data class Done(
        val source: ImportSource,
        val tally: ImportTally,
        val at: Instant,
    ) : ImportState

    /** *Stop*: what was stored stays (each week is one transaction); importing again adds only what's missing. */
    data class Stopped(
        val source: ImportSource,
        val tally: ImportTally,
    ) : ImportState

    data class Failed(
        val source: ImportSource,
        val why: ImportFailure,
        val tally: ImportTally,
    ) : ImportState
}

/**
 * Import history (Settings › Share with apps › Import history, and setup's *Bring your history back*): the last
 * [HistoryImport.PERIOD] from Tidepool's data API, a LibreView file or a Sukrara export, through THE import path
 * ([HistoryImportStore]), one week at a time. One import at a time; it runs in the app's scope, so leaving the page
 * doesn't stop it, *Stop* does. Readings newer than [HistoryImport.NEWEST_AGE] are left to the sensor.
 *
 * Tidepool: one request per week of data and one for the notes, a pause of [pauseMs] between requests; a 429 / 503
 * waits for Tidepool's `Retry-After` (else [busyWaitS], at most [MAX_WAIT_S]) and tries the same request again, up to
 * [RETRIES] times.
 */
class HistoryImporter(
    private val store: HistoryImportStore,
    /** Runs a block with the signed-in Tidepool client (`TidepoolUploader.withAccount`); null when signed out. */
    private val tidepool: suspend (suspend (TidepoolClient) -> Unit) -> Unit?,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
    private val log: (String) -> Unit = {},
    private val pauseMs: Long = 1000,
    private val busyWaitS: Long = 30,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutableState = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = mutableState.asStateFlow()

    private var scope: CoroutineScope? = null
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        this.scope = scope
    }

    /** Starts importing the file [read] returns (null: it couldn't be read); ignored while an import runs. */
    fun launchFile(read: suspend () -> String?) = launch { importFile(read()) }

    /** Starts the Tidepool download; ignored while an import runs. */
    fun launchTidepool() = launch { importTidepool() }

    /** *Stop*: cancels the running import (what it stored stays). */
    fun stop() {
        job?.cancel()
    }

    /** *OK* on a finished, stopped or failed import: back to the sources. */
    fun dismiss() {
        if (mutableState.value !is ImportState.Running) mutableState.value = ImportState.Idle
    }

    private fun launch(block: suspend () -> Unit) {
        val s = scope ?: return
        if (job?.isActive == true) return
        job =
            s.launch {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // never the process the alerts run in: a storage error is a failed import, said on the page
                    log("import: ${e.javaClass.simpleName}")
                    val running = mutableState.value as? ImportState.Running
                    fail(running?.source ?: ImportSource.File, ImportFailure.Network(UserErrors.of(e)), running?.tally ?: ImportTally())
                }
            }
    }

    /** Imports a file's text (null: unreadable). */
    suspend fun importFile(text: String?) {
        var source = ImportSource.File
        mutableState.value = ImportState.Running(source, 0, 1, ImportTally())
        if (text == null) return fail(source, ImportFailure.Unreadable, ImportTally())
        val parsed = withContext(io) { HistoryCsv.parse(text, clock.zone, clock.now()) }
        val history =
            when (parsed) {
                is HistoryCsv.Result.Refused -> {
                    val why =
                        when (val problem = parsed.why) {
                            // the layout only (digits as 0): never a reading's time in the log
                            is FileProblem.UnknownDates -> {
                                log("import file: dates not read, like \"${FileDates.shape(problem.example)}\"")
                                ImportFailure.UnknownDates(problem.example)
                            }

                            FileProblem.NotGlucoseData -> {
                                log("import file: not a glucose file")
                                ImportFailure.NotGlucoseData
                            }
                        }
                    return fail(source, why, ImportTally())
                }

                is HistoryCsv.Result.Ok -> {
                    parsed.history
                }
            }
        source = history.source
        var tally = ImportTally(skipped = history.skipped)
        val weeks = weeks()
        runSteps(source, { tally }) {
            for ((i, week) in weeks.withIndex()) {
                tally = store(tally, week, history.readings, history.treatments)
                mutableState.value = ImportState.Running(source, i + 1, weeks.size, tally)
            }
            tally = tally.copy(outsidePeriod = tally.outsidePeriod + outside(history.readings, history.treatments))
        }
        log("import file (${source.id}): ${tally.readings} readings, ${tally.entries} entries, ${tally.alreadyHere} here")
    }

    /** Downloads the last [HistoryImport.PERIOD] from the signed-in Tidepool account. */
    suspend fun importTidepool() {
        val source = ImportSource.Tidepool
        var tally = ImportTally()
        val weeks = weeks()
        val steps = weeks.size + 1
        mutableState.value = ImportState.Running(source, 0, steps, tally)
        try {
            runSteps(source, { tally }) {
                val ran =
                    tidepool { client ->
                        for ((i, week) in weeks.withIndex()) {
                            if (i > 0) pause(pauseMs)
                            val data = politely { client.data(week.first, week.second, TidepoolHistory.TYPES) }
                            tally = store(tally, week, TidepoolHistory.readings(data), TidepoolHistory.treatments(data))
                            mutableState.value = ImportState.Running(source, i + 1, steps, tally)
                        }
                        pause(pauseMs)
                        val (from, to) = weeks.first().first to weeks.last().second
                        val notes = TidepoolHistory.notes(politely { client.notes(from, to) })
                        tally = store(tally, from to to, emptyList(), notes)
                        mutableState.value = ImportState.Running(source, steps, steps, tally)
                    }
                if (ran == null) throw TpSignedOut()
            }
        } catch (e: TpSignedOut) {
            fail(source, ImportFailure.SignedOut, tally)
        } catch (e: TpFailure) {
            log("import tidepool: ${e.message}")
            fail(source, ImportFailure.Network(e.trouble), tally)
        } catch (e: IOException) {
            log("import tidepool: ${e.javaClass.simpleName}")
            fail(source, ImportFailure.Network(UserErrors.of(e)), tally)
        }
        log("import tidepool: ${tally.readings} readings, ${tally.entries} entries, ${tally.alreadyHere} here")
    }

    /** Runs [work]; at its end the state is Done, or Stopped when it was cancelled (what it stored stays). */
    private suspend fun runSteps(
        source: ImportSource,
        tally: () -> ImportTally,
        work: suspend () -> Unit,
    ) {
        try {
            work()
            mutableState.value = ImportState.Done(source, tally(), clock.now())
        } catch (e: CancellationException) {
            mutableState.value = ImportState.Stopped(source, tally())
            throw e
        }
    }

    private fun fail(
        source: ImportSource,
        why: ImportFailure,
        tally: ImportTally,
    ) {
        mutableState.value = ImportState.Failed(source, why, tally)
    }

    /** One blocking Tidepool request on [io], tried again after a 429 / 503 as Tidepool asks. */
    private suspend fun <T> politely(request: () -> T): T {
        var tries = 0
        while (true) {
            try {
                return withContext(io) { request() }
            } catch (e: TpFailure) {
                if (!e.busy || ++tries > RETRIES) throw e
                val waitS = (e.retryAfterS ?: busyWaitS).coerceIn(1, MAX_WAIT_S)
                log("import tidepool: busy (${e.code}), waiting ${waitS}s")
                pause(waitS * 1000)
            }
        }
    }

    /** The history the phone keeps, as weeks oldest first: [from, to) each, the last one ending now. */
    private fun weeks(): List<Pair<Instant, Instant>> {
        val now = clock.now()
        val from = now - HistoryImport.PERIOD
        return generateSequence(from) { it + WEEK }
            .takeWhile { it < now }
            .map { it to minOf(it + WEEK, now) }
            .toList()
    }

    /** Stores the part of [readings] and [treatments] in [week] (readings only up to [HistoryImport.NEWEST_AGE] ago). */
    private suspend fun store(
        tally: ImportTally,
        week: Pair<Instant, Instant>,
        readings: List<Reading>,
        treatments: List<TreatmentRecord>,
    ): ImportTally {
        val newest = clock.now() - HistoryImport.NEWEST_AGE
        val rs = readings.filter { it.t >= week.first && it.t < week.second && it.t <= newest }
        val ts = treatments.filter { it.treatment.at >= week.first && it.treatment.at < week.second }
        val r = store.importReadings(rs)
        val t = store.importTreatments(ts)
        return tally.copy(
            readings = tally.readings + r,
            entries = tally.entries + t,
            alreadyHere = tally.alreadyHere + (rs.size - r) + (ts.size - t),
        )
    }

    /** How many of a file's readings and entries fall outside the period (older, in the future, or too fresh). */
    private fun outside(
        readings: List<Reading>,
        treatments: List<TreatmentRecord>,
    ): Int {
        val now = clock.now()
        val from = now - HistoryImport.PERIOD
        val newest = now - HistoryImport.NEWEST_AGE
        return readings.count { it.t < from || it.t > newest } + treatments.count { it.treatment.at < from || it.treatment.at >= now }
    }

    companion object {
        val WEEK: Duration = Duration.ofDays(7)
        const val RETRIES = 3
        const val MAX_WAIT_S = 120L
    }
}
