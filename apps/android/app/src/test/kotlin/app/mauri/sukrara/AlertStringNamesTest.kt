// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertReading
import app.mauri.sukrara.alerts.Firing
import app.mauri.sukrara.alerts.MuteLine
import app.mauri.sukrara.alerts.Side
import app.mauri.sukrara.alerts.TreatmentKind
import app.mauri.sukrara.core.model.DurationWords
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.designsystem.alerts.AlertWords
import app.mauri.sukrara.designsystem.alerts.MuteText
import app.mauri.sukrara.monitor.alerts.AlertCopy
import app.mauri.sukrara.monitor.alerts.AlertNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Every string the alert texts look up by name at run time ([AlertWords]: `getIdentifier`, which neither the compiler
 * nor the resource shrinker can see) resolves in the app's merged resources, in English and Spanish: every
 * [AlertKind]'s headline and label, every [Side]'s name, every context, value, mute and duration line. 0.14.1's release
 * build lost them to the shrinker and no alert was posted (keep_strings.xml and verify-apk.sh guard the APK; this test
 * guards the names the code builds, e.g. a new kind or side without its strings).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AlertStringNamesTest {
    private val app: Context = ApplicationProvider.getApplicationContext()

    /** The app's real lookup by name (it throws `Resources.NotFoundException`, as the alert did), recording every name. */
    private class Recording(
        private val inner: AlertWords,
        private val language: String,
    ) : AlertWords {
        val names = sortedSetOf<String>()

        override fun get(name: String): String {
            names += name
            return runCatching { inner.get(name) }.getOrElse { throw AssertionError("$language: no string '$name'", it) }
        }

        override val durations: DurationWords get() = inner.durations
    }

    private fun readings(kind: AlertKind): List<AlertReading?> =
        listOf(
            null,
            AlertReading(t = 1_000, mgdl = if (kind.side == Side.LOW) 60 else 220, rate = null),
            AlertReading(t = 1_000, mgdl = 120, rate = -2.4),
            AlertReading(t = 1_000, mgdl = 120, rate = 2.4),
            AlertReading(t = 1_000, mgdl = 39, rate = null),
            AlertReading(t = 1_000, mgdl = 401, rate = null),
        )

    /** One line of every [MuteLine] (one and many minutes where the wording differs), for [side]. */
    private fun lines(side: Side): List<MuteLine> =
        listOf(
            MuteLine.SideMuted(side, null),
            MuteLine.SideMuted(side, 1),
            MuteLine.SideMuted(side, 90),
            MuteLine.SideAfter(side, TreatmentKind.CARBS, 1),
            MuteLine.SideAfter(side, TreatmentKind.CARBS, 45),
            MuteLine.SideAfter(side, TreatmentKind.RAPID, 1),
            MuteLine.SideAfter(side, TreatmentKind.RAPID, 150),
            MuteLine.SideFalling(side),
            MuteLine.SideLess(side, 30),
            MuteLine.AllTimed(1),
            MuteLine.AllTimed(120),
            MuteLine.AllForever,
            MuteLine.NotifUntil(1_000),
            MuteLine.NightUntil(1_000),
            MuteLine.Pill(1),
            MuteLine.Pill(75),
            MuteLine.PillForever,
            MuteLine.Choice(30),
            MuteLine.ChoiceForever,
        ).onEach(::covered)

    /** Doesn't compile when a new [MuteLine] comes without a line in [lines] (then add it there, and here). */
    private fun covered(l: MuteLine) =
        when (l) {
            is MuteLine.SideMuted, is MuteLine.SideAfter, is MuteLine.SideFalling, is MuteLine.SideLess -> Unit
            is MuteLine.AllTimed, MuteLine.AllForever, is MuteLine.NotifUntil, is MuteLine.NightUntil -> Unit
            is MuteLine.Pill, MuteLine.PillForever, is MuteLine.Choice, MuteLine.ChoiceForever -> Unit
        }

    /** Every alert and mute text, through [w], as the notifications and screens build them. */
    private fun everyText(w: AlertWords): List<String> =
        buildList {
            for (kind in AlertKind.entries) {
                for (treated in 0..2) for (escalated in listOf(false, true)) add(AlertCopy.headline(kind, treated, w, escalated))
                for (unit in GlucoseUnit.entries) {
                    for (r in readings(kind)) add(AlertCopy.body(Firing(kind, "e", 1, r, 70.0, 2_000), w, unit))
                }
            }
            for (side in Side.entries) {
                add(MuteText.sideName(side, w))
                for (l in lines(side)) add(MuteText.line(l, w) { "7:00" })
            }
        }

    private fun inLanguage(tag: String): Context =
        app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })

    @Test fun `every name the alert texts build resolves to a real string, in English and Spanish`() {
        for (tag in listOf("en", "es")) {
            val ctx = inLanguage(tag)
            val w = Recording(AlertWords.of(ctx), tag)
            everyText(w).forEach { assertTrue("$tag: an empty text", it.isNotBlank()) }
            for (name in w.names) {
                assertNotEquals("$tag: no string '$name'", 0, ctx.resources.getIdentifier(name, "string", ctx.packageName))
            }
            // the names built from a kind or a side were all reached
            for (kind in AlertKind.entries) assertTrue("alert_headline_${kind.wire}" in w.names)
            for (side in Side.entries) assertTrue("side_${side.wire}" in w.names)
        }
    }

    @Test fun `every kind's label and headline are strings the code names directly`() {
        for (kind in AlertKind.entries) {
            assertTrue(kind.wire, AlertNotifier.kindLabel(app, kind).isNotBlank())
            assertEquals(
                "the plain alert's title is the alert's own headline",
                AlertWords.of(app).get("alert_headline_${kind.wire}"),
                app.getString(AlertNotifier.headlineRes(kind)),
            )
        }
    }
}
