// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data.readings

import app.mauri.sukrara.core.data.settings.SchemaSettings
import app.mauri.sukrara.core.data.settings.Setting
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.TrendRate
import app.mauri.sukrara.core.model.TrendSetting
import app.mauri.sukrara.core.model.storage.ReadingsRepository
import app.mauri.sukrara.core.model.storage.SettingsRepository
import app.mauri.sukrara.core.model.storage.StoredSpan
import app.mauri.sukrara.settings.SettingsSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant

/**
 * THE one place readings get their trend on Android (contracts/trend, #204): every read of [base] comes back with each
 * reading's [Reading.trend] at its own time ([TrendRate.annotate] under the *Trend arrow* [setting], the follower's
 * mirrored one when it mirrors the wearer). The hub (card, every surface, the glucose notification, the alerts), the
 * history, the uploads, the local endpoints and the export all read through it, so a late or backfilled reading
 * corrects itself and the ones after it, and a setting change applies at once. Each read reaches [LOOKBACK] further
 * back than asked, so the first readings asked for have the readings their trend needs. Writes pass through.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrendReadings(
    private val base: ReadingsRepository,
    private val setting: Flow<TrendSetting>,
) : ReadingsRepository {
    override fun window(from: Instant): Flow<List<Reading>> =
        combine(base.window(from.minus(LOOKBACK)), setting.distinctUntilChanged()) { list, s -> annotated(list, s, from) }
            .distinctUntilChanged()

    override fun latest(): Flow<Reading?> =
        base
            .latest()
            .map { it?.t }
            .distinctUntilChanged()
            .flatMapLatest { t -> if (t == null) flowOf(null) else window(t).map { it.lastOrNull() } }

    override suspend fun range(
        from: Instant,
        to: Instant,
    ): List<Reading> = annotated(base.range(from.minus(LOOKBACK), to), setting.first(), from)

    override suspend fun upsert(readings: List<Reading>) = base.upsert(readings)

    override suspend fun deleteBefore(cutoff: Instant): Int = base.deleteBefore(cutoff)

    override suspend fun spanOf(sources: Set<SourceId>): StoredSpan = base.spanOf(sources)

    override suspend fun deleteFrom(sources: Set<SourceId>): Int = base.deleteFrom(sources)

    private fun annotated(
        readings: List<Reading>,
        s: TrendSetting,
        from: Instant,
    ): List<Reading> = TrendRate.annotate(readings, s).filter { !it.t.isBefore(from) }

    companion object {
        /** The *Trend arrow* setting (`glucose.trend`, mirrored from the wearer). */
        val TrendArrow: Setting<String> =
            SchemaSettings.text("glucose.trend", TrendSetting.DEFAULT.id, SettingsSpec.OPTIONS.getValue("trend").toSet())

        /** The setting in use from [settings] (a follower's: what *Settings from* chooses). */
        fun setting(
            settings: SettingsRepository,
        ): Flow<TrendSetting> = settings.flow(TrendArrow).map(TrendSetting::of).distinctUntilChanged()

        /**
         * How much earlier each read starts: the Smooth window (5 × 5 min), the fast alerts' 5-min hold and a reading
         * before it, so the first reading asked for has the same rate and `held` as it has further into a longer read. Its
         * text band may still differ while a slow drift has kept the rate within the hysteresis of a band edge since
         * before the read (the band is remembered as long as the readings follow): a place that shows a band reads as
         * far back as the hub (its 24 h window, the uploads' hours), never only this.
         */
        val LOOKBACK: Duration = Duration.ofMinutes(40)
    }
}
