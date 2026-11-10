// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.readaloud

import android.content.Context
import android.content.pm.ShortcutManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.graph.CardSurface
import app.mauri.sukrara.graph.GlucoseCard
import app.mauri.sukrara.graph.stats.Bands
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Locale

/** *Read glucose aloud* (#159): THE sentence, in the person's language, said and stopped on demand. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReadAloudTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-10-28T12:00:00Z")
    private val nowS = now.epochSecond

    private fun res(locale: Locale): Resources =
        ctx.createConfigurationContext(Configuration(ctx.resources.configuration).apply { setLocale(locale) }).resources

    private fun state(
        mgdl: Int = 112,
        ageS: Long = 120,
        rate: Double? = -1.5,
    ): GlucoseState {
        val reading = Reading(now.minusSeconds(ageS), mgdl, rate, SourceId.Libre3)
        return GlucoseState.Initial.copy(role = Role.Wearer, latest = reading, recent = listOf(reading))
    }

    @After fun tearDown() {
        GlucoseCard.faceObserver = null
    }

    @Test fun `it says the glucose sentence with its age, in the person's language and unit`() {
        val seen = mutableSetOf<CardSurface>()
        GlucoseCard.faceObserver = { s, _ -> seen += s }
        val en = res(Locale.ENGLISH)
        val es = res(Locale.forLanguageTag("es"))
        assertEquals(
            "112 milligrams per decilitre, falling, in range, 2 min ago",
            ReadAloud.sentence(state(), Bands(), GlucoseUnit.MGDL, nowS, en),
        )
        assertEquals(
            "112 miligramos por decilitro, bajando, en rango, hace 2 min",
            ReadAloud.sentence(state(), Bands(), GlucoseUnit.MGDL, nowS, es),
        )
        assertEquals(
            "6.2 millimoles per litre, falling, in range, 2 min ago",
            ReadAloud.sentence(state(), Bands(), GlucoseUnit.MMOL, nowS, en),
        )
        assertEquals(
            "60 milligrams per decilitre, old reading, low, 6 min ago",
            ReadAloud.sentence(state(60, ageS = 6 * 60), Bands(), GlucoseUnit.MGDL, nowS, en),
        )
        assertEquals(setOf(CardSurface.READ_ALOUD), seen)
    }

    @Test fun `started again while it speaks, it stops`() {
        val r = ReadAloud(ctx)
        r.toggle("112 milligrams per decilitre", Locale.ENGLISH)
        assertTrue(r.speaking)
        r.toggle("112 milligrams per decilitre", Locale.ENGLISH)
        assertFalse(r.speaking)
        r.toggle("112 milligrams per decilitre", Locale.ENGLISH)
        assertTrue(r.speaking)
        r.stop()
        assertFalse(r.speaking)
    }

    @Test fun `the shortcut is named in the person's language`() {
        ReadAloud(ctx).publish()
        val shortcut =
            ctx
                .getSystemService(ShortcutManager::class.java)
                .dynamicShortcuts
                .single { it.id == ReadAloud.SHORTCUT_ID }
        assertEquals("Read glucose aloud", shortcut.longLabel.toString())
        assertEquals(ReadAloud.ACTION, shortcut.intent?.action)
        assertEquals(ReadAloudActivity::class.java.name, shortcut.intent?.component?.className)
    }
}
