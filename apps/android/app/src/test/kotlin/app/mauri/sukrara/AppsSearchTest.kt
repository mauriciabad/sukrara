// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.designsystem.rows.ROW_TAG
import app.mauri.sukrara.designsystem.rows.SettingsGroupTitle
import app.mauri.sukrara.designsystem.rows.SettingsHighlight
import app.mauri.sukrara.designsystem.rows.SettingsScaffoldPage
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.integrations.AppsActions
import app.mauri.sukrara.integrations.AppsContent
import app.mauri.sukrara.integrations.AppsUi
import app.mauri.sukrara.integrations.ServerReader
import app.mauri.sukrara.integrations.localserver.XdripState
import app.mauri.sukrara.integrations.upload.UploadStatus
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * #181: the settings search finds Share with apps' rows ([AppsSearch], the PoC's search did): the page, as a wearer and
 * a follower see it, shows every entry with its group, has no row the search can't find (but the apps that read the
 * xDrip+ server, listed by their own names), and flashes each entry's row when a result opens it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AppsSearchTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-11-12T10:00:00Z")
    private val reader = ServerReader("com.garmin.android.apps.connectmobile", "Garmin Connect")

    private fun ui(wearer: Boolean) = AppsUi(wearer, xdripOn = true, XdripState.Off, listOf(reader), UploadStatus.Off)

    private fun string(id: Int) = context.getString(id)

    private fun firstText(node: SemanticsNode): String? =
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
            ?: node.children.firstNotNullOfOrNull { firstText(it) }

    private fun groupOf(node: SemanticsNode): String? =
        generateSequence(node.parent) { it.parent }.firstNotNullOfOrNull { it.config.getOrNull(SettingsGroupTitle) }

    @Test
    fun everyRowIsFoundInItsGroupAndFlashes() {
        var wearer by mutableStateOf(true)
        var draw by mutableIntStateOf(0)
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                key(draw) { SettingsScaffoldPage("Share with apps", {}) { AppsContent(ui(wearer), now, AppsActions()) } }
            }
        }
        val problems = mutableListOf<String>()
        for (w in listOf(true, false)) {
            wearer = w
            draw++
            compose.waitForIdle()
            val entries = AppsSearch.entries.filter { it.shownOn(pens = false, wearer = w) }
            val rows =
                compose
                    .onAllNodesWithTag(ROW_TAG, useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .mapNotNull { n -> firstText(n)?.let { it to groupOf(n) } }
            val texts =
                compose
                    .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .flatMap { n -> n.config[SemanticsProperties.Text].map { it.text } }
            for ((title, group) in rows) {
                if (title == reader.label) continue
                val e = entries.firstOrNull { string(it.title) == title }
                when {
                    e == null -> problems += "wearer=$w: \"$title\" isn't in AppsSearch"
                    e.group?.let(::string) != group -> problems += "wearer=$w: \"$title\" is in $group, AppsSearch says ${e.group}"
                }
            }
            for (e in entries) {
                if (string(e.title) !in texts) problems += "wearer=$w: \"${string(e.title)}\" isn't on the page"
                val row = e.highlight?.let(::string) ?: continue
                SettingsHighlight.request(row)
                draw++
                compose.waitForIdle()
                if (SettingsHighlight.shown != row) problems += "wearer=$w: \"$row\" isn't flashed"
            }
        }
        SettingsHighlight.request(null)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
