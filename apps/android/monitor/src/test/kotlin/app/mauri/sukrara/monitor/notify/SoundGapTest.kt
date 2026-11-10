// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Android mutes an app's second sound within 1 s: an info sound never lands within [SoundGap.GAP_MS] of an alert's. */
class SoundGapTest {
    private var now = 50_000L
    private val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
    private val posts = mutableListOf<Pair<String, Long>>()
    private val gap = SoundGap({ now }) { delay, run -> scheduled += (now + delay) to run }

    private fun info(
        name: String,
        id: Int = 1,
        sounds: Boolean = true,
    ) = gap.submit(id, sounds) { posts += name to now }

    /** Moves the clock to each due post in turn (a post may schedule a later one). */
    private fun advance(ms: Long) {
        val end = now + ms
        while (true) {
            val next = scheduled.filter { it.first <= end }.minByOrNull { it.first } ?: break
            scheduled.remove(next)
            now = next.first
            next.second()
        }
        now = end
    }

    @Test fun `a silent info notification posts at once`() {
        info("countdown", sounds = false)
        assertEquals(listOf("countdown" to 50_000L), posts)
    }

    @Test fun `an info sound posted with an alert of the same reading lands after the alert's second`() {
        info("sensor lost")
        gap.alertRang() // No data, decided right after
        advance(5_000)
        assertEquals(listOf("sensor lost" to 50_000L + SoundGap.GAP_MS), posts)
    }

    @Test fun `an info sound waits until the gap after a later alert is over`() {
        info("sensor ended")
        advance(800)
        gap.alertRang() // an alert 0.8 s later: the info would have landed 0.3 s after it
        val alertAt = now
        advance(5_000)
        assertEquals(1, posts.size)
        assertTrue(posts.single().second >= alertAt + SoundGap.GAP_MS)
    }

    @Test fun `a newer post of the same notification replaces the waiting one`() {
        info("old")
        advance(500)
        info("new")
        advance(5_000)
        assertEquals(listOf("new"), posts.map { it.first })
    }

    @Test fun `a cancelled notification never lands`() {
        info("ended")
        gap.cancel(1)
        advance(5_000)
        assertTrue(posts.isEmpty())
    }

    @Test fun `an alert right after an info sound waits the rest of the gap, never longer`() {
        info("sensor ended")
        advance(1_500) // it sounded at 1.1 s, 0.4 s ago
        assertEquals(SoundGap.GAP_MS - 400, gap.alertWaitMs())
        advance(SoundGap.GAP_MS - 400)
        assertEquals(0L, gap.alertWaitMs())
    }

    @Test fun `a notice that can't wait posts at once and holds the next alert back`() {
        gap.postNow(2, sounds = true) { posts += "monitoring stopped" to now }
        assertEquals(listOf("monitoring stopped" to 50_000L), posts)
        assertEquals(SoundGap.GAP_MS, gap.alertWaitMs())
        advance(700)
        assertEquals(SoundGap.GAP_MS - 700, gap.alertWaitMs())
    }

    @Test fun `silent info posts never hold an alert back`() {
        info("countdown", sounds = false)
        gap.postNow(2, sounds = false) { posts += "quiet notice" to now }
        assertEquals(0L, gap.alertWaitMs())
    }

    @Test fun `different notifications wait on their own`() {
        info("a", id = 1)
        info("b", id = 2)
        advance(5_000)
        assertEquals(setOf("a", "b"), posts.map { it.first }.toSet())
    }
}
