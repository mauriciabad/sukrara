// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.graph.Clock
import app.mauri.sukrara.graph.Freshness
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneOffset
import java.util.Locale

/**
 * #166: the app installs the phone's *Use 24-hour format* into [Clock], read live: the setting wins over the language's
 * default in both directions, a change applies at the next time formatted, and it changes [Clock.signature] (every
 * redraw key: the notification and the widgets draw again when Settings says the clock changed).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class PhoneClockTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    /** 21:08 UTC. */
    private val t = 21 * 3600L + 8 * 60

    private fun use24Hour(on: Boolean) = Settings.System.putString(app.contentResolver, Settings.System.TIME_12_24, if (on) "24" else "12")

    private fun time(locale: Locale) = norm(Clock.time(t, locale = locale, zone = ZoneOffset.UTC))

    private fun norm(s: String) = s.replace('\u202F', ' ').replace('\u00A0', ' ')

    @After fun tearDown() {
        Clock.hour24Source = null
    }

    @Test fun clockTimesFollowThePhonesSettingLive() {
        PhoneClock.follow(app)

        use24Hour(true)
        assertTrue(Clock.hour24)
        // US English defaults to 12-hour: the phone's setting wins
        assertEquals("21:08", time(Locale.US))
        val in24 = Clock.signature()

        // switched in Settings while Sukrara runs: the next time formatted follows, no restart
        use24Hour(false)
        assertFalse(Clock.hour24)
        assertEquals("9:08 PM", time(Locale.US))
        // UK English defaults to 24-hour: the phone's 12-hour wins there too
        assertEquals(
            Clock.time(t, hour24 = false, locale = Locale.UK, zone = ZoneOffset.UTC),
            Clock.time(t, locale = Locale.UK, zone = ZoneOffset.UTC),
        )
        assertTrue(time(Locale.UK).startsWith("9:08"))
        assertNotEquals("the redraw keys change with it", in24, Clock.signature())
    }

    @Test fun theCardsTimesGoThroughIt() {
        PhoneClock.follow(app)
        val now = t + 600
        use24Hour(true)
        assertEquals("21:08", Freshness.timeText(t, now, Locale.US, ZoneOffset.UTC))
        use24Hour(false)
        assertEquals("9:08 PM", norm(Freshness.timeText(t, now, Locale.US, ZoneOffset.UTC)))
    }
}
