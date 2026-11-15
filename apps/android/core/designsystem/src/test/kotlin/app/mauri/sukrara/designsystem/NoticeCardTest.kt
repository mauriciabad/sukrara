// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.designsystem.banner.NOTICE_DISMISS_TAG
import app.mauri.sukrara.designsystem.banner.NoticeAction
import app.mauri.sukrara.designsystem.banner.NoticeCard
import app.mauri.sukrara.designsystem.banner.NoticeTone
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The owner, 2026-11-15: the LibreView banner's close "has a tiny x icon … it doesn't look like warnings that are
 * similar are using a shared component". Every notice is the one [NoticeCard]: its close is Material's standard icon
 * button (a 48 dp target, the 24 dp close icon) in every tone, and no other file draws a banner, a warning or a note.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NoticeCardTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun everyToneClosesWithTheSameFullSizeButton() {
        var closed = 0
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                Column {
                    for (tone in NoticeTone.entries) {
                        NoticeCard(
                            "Your clinic sees nothing since 11:00.",
                            title = "LibreView isn't getting your glucose",
                            tone = tone,
                            action = NoticeAction("Fix", {}),
                            onDismiss = { closed++ },
                        )
                    }
                }
            }
        }
        val closes = compose.onAllNodesWithTag(NOTICE_DISMISS_TAG)
        val target = with(compose.density) { 48.dp.toPx() }
        for (i in NoticeTone.entries.indices) {
            // the 40 dp button's touch target reaches 48 dp (Material's minimum interactive size)
            val touch = closes[i].fetchSemanticsNode().touchBoundsInRoot
            assertTrue("close target ${touch.width} × ${touch.height} px", touch.width >= target - 1 && touch.height >= target - 1)
            closes[i].performClick()
        }
        assertEquals(NoticeTone.entries.size, closed)
        // TalkBack names the close, and every main action stays a button
        assertEquals(NoticeTone.entries.size, compose.onAllNodes(hasContentDescription("Dismiss")).fetchSemanticsNodes().size)
        assertEquals(NoticeTone.entries.size, compose.onAllNodes(hasText("Fix") and hasClickAction()).fetchSemanticsNodes().size)
    }

    /** SPA's banner parts and hand-made error cards stay out of the app: a notice is a [NoticeCard]. */
    @Test
    fun noOtherNoticeCards() {
        val banned =
            Regex(
                """\b(SettingsBanner|SettingsBannerContent|BannerTitleHeader|BannerHeader)\(|colorScheme\.errorContainer|cs\.errorContainer""",
            )
        // the error container elsewhere is no notice: the theme, a swipe's armed delete, the pen's floating read card
        // (it shakes on a failed read) and the new-sensor page's "untested" tag
        val allowed = setOf("Notice.kt", "SukraraTheme.kt", "SwipeToDelete.kt", "PenReadCard.kt", "NewSensorScreen.kt")
        val android = File("../..").canonicalFile
        val hits =
            android
                .walkTopDown()
                .onEnter { it.name != "build" && it.name != "settingslib-spa" && !it.name.startsWith(".") }
                .filter { it.extension == "kt" && "/src/main/" in it.path && it.name !in allowed }
                .flatMap { f ->
                    f.readLines().mapIndexedNotNull { i, l ->
                        if (banned.containsMatchIn(l)) "${f.relativeTo(android)}:${i + 1}: ${l.trim()}" else null
                    }
                }.toList()
        assertTrue("draw banners, warnings and notes with NoticeCard (banner/Notice.kt):\n" + hits.joinToString("\n"), hits.isEmpty())
    }
}
