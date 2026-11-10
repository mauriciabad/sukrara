// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.systemui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.data.settings.HookPosition
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.graph.Freshness
import app.mauri.sukrara.graph.stats.Bands
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Locale

/**
 * TalkBack in System UI says THE sentence in the person's language (#159): the root icons after their marks, the
 * module's text by the face it shows, also the faces it reaches while the app is silent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SystemUiWordsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-10-28T12:00:00Z")
    private val nowS = now.epochSecond
    private val en = res(Locale.ENGLISH)
    private val es = res(Locale.forLanguageTag("es"))

    private fun res(locale: Locale): Resources =
        ctx.createConfigurationContext(Configuration(ctx.resources.configuration).apply { setLocale(locale) }).resources

    private fun state(
        mgdl: Int = 112,
        ageS: Long = 30,
        rate: Double? = -1.5,
        sensor: SensorState = SensorState.Active(null),
    ): GlucoseState {
        val reading = Reading(now.minusSeconds(ageS), mgdl, rate, SourceId.Libre3)
        return GlucoseState.Initial.copy(role = Role.Wearer, latest = reading, recent = listOf(reading), sensor = sensor)
    }

    private fun icons(
        s: GlucoseState,
        r: Resources,
    ) = SlotIcons.plan(RootStatusBar.look(s, nowS), SystemUiWords.icons(s, Bands(), nowS, r))

    @Test fun `the icons say the sentence in English and Spanish`() {
        val p = icons(state(), en)
        assertEquals("Sukrara: 112 milligrams per decilitre, falling, in range", p.desc)
        assertEquals("Sukrara, falling", p.trendDesc)
        val e = icons(state(), es)
        assertEquals("Sukrara: 112 miligramos por decilitro, bajando, en rango", e.desc)
        assertEquals("Sukrara, bajando", e.trendDesc)
        // the range in words where the colour shows it (#26)
        assertEquals("Sukrara: 250 miligramos por decilitro, subiendo rápido, alta", icons(state(250, rate = 2.5), es).desc)
    }

    @Test fun `struck, no trend, no readings, ended`() {
        val old = icons(state(ageS = 6 * 60), en)
        assertEquals("Sukrara: 112 milligrams per decilitre, old reading, in range", old.desc)
        assertEquals("Sukrara, old reading", old.trendDesc)
        assertEquals("Sukrara, lectura antigua", icons(state(ageS = 6 * 60), es).trendDesc)
        assertEquals("Sukrara, trend unknown", icons(state(rate = null), en).trendDesc)
        val since = Freshness.timeText(nowS - 25 * 60, nowS)
        assertEquals("Sukrara. Glucose: no reading since $since", icons(state(ageS = 25 * 60), en).desc)
        assertEquals("Sukrara. Glucosa: sin lecturas desde $since", icons(state(ageS = 25 * 60), es).desc)
        assertEquals("Sukrara. Sensor ended. Replace sensor", icons(state(ageS = 6 * 60, sensor = SensorState.Ended), en).desc)
    }

    private fun hook(
        s: GlucoseState,
        unit: GlucoseUnit = GlucoseUnit.MGDL,
    ) = RootStatusBar.hookState(s, RootPrefs(true, HookPosition.AFTER_CLOCK, true, true), Bands(), unit)

    /** What the module's TalkBack text says at [at] for the state the app sent at [nowS]. */
    private fun said(
        h: HookState,
        at: Long,
    ) = HookText.spoken(h, HookText.face(h, at), at)

    @Test fun `the module's text says the face it shows, also once the app falls silent`() {
        val s = state()
        val bare = hook(s)
        val h = bare.copy(spoken = SystemUiWords.text(s, bare, Bands(), nowS, es))
        assertEquals("Sukrara: 112 miligramos por decilitro, bajando, en rango", said(h, nowS))
        // no new state: struck at 5 min, no readings at 20 min, the day from 12 h
        assertEquals("Sukrara: 112 miligramos por decilitro, lectura antigua, en rango", said(h, nowS + 6 * 60))
        val at = nowS - 30
        assertEquals("Sukrara. Glucosa: sin lecturas desde ${Freshness.timeText(at, nowS + 25 * 60)}", said(h, nowS + 25 * 60))
        val day = nowS + 13 * 3600
        assertEquals("Sukrara. Glucosa: sin lecturas desde ${Freshness.timeText(at, day)}", said(h, day))
        assertEquals(true, h.spoken.size <= HookState.MAX_SPOKEN)
    }

    @Test fun `the module's text in mmol per litre and a warm-up's minutes`() {
        val s = state()
        val mmol = hook(s, GlucoseUnit.MMOL)
        val h = mmol.copy(spoken = SystemUiWords.text(s, mmol, Bands(), nowS, en))
        assertEquals("Sukrara: 6.2 millimoles per litre, falling, in range", said(h, nowS))
        val warming = state(sensor = SensorState.WarmingUp(now.plusSeconds(40 * 60)))
        val w = hook(warming)
        val wh = w.copy(spoken = SystemUiWords.text(warming, w, Bands(), nowS, en))
        assertEquals("Sukrara. Sensor warming up, 40 min left", said(wh, nowS))
        assertEquals("Sukrara. Sensor warming up, 39 min left", said(wh, nowS + 60))
    }

    @Test fun `the sentences reach System UI unchanged`() {
        val s = state()
        val bare = hook(s)
        val sent = bare.copy(spoken = SystemUiWords.text(s, bare, Bands(), nowS, es))
        val got = HookState.from(Intent(SystemUi.actionState("app")).putExtras(sent.toBundle()))
        assertEquals(sent.spoken, got.spoken)
        assertEquals(sent, got)
    }
}
