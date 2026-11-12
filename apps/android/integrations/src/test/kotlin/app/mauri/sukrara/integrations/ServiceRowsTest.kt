// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.mauri.sukrara.designsystem.icons.Service
import app.mauri.sukrara.designsystem.icons.packageIconTag
import app.mauri.sukrara.designsystem.icons.serviceIconTag
import app.mauri.sukrara.designsystem.rows.ROW_ICON_TAG
import app.mauri.sukrara.designsystem.rows.ROW_TAG
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.integrations.broadcasts.AppProblem
import app.mauri.sukrara.integrations.broadcasts.BcActions
import app.mauri.sukrara.integrations.broadcasts.BcApp
import app.mauri.sukrara.integrations.broadcasts.BcStatus
import app.mauri.sukrara.integrations.broadcasts.BcUi
import app.mauri.sukrara.integrations.broadcasts.BroadcastPayloads
import app.mauri.sukrara.integrations.broadcasts.BroadcastsContent
import app.mauri.sukrara.integrations.history.ImportActions
import app.mauri.sukrara.integrations.history.ImportContent
import app.mauri.sukrara.integrations.history.ImportState
import app.mauri.sukrara.integrations.history.ImportUi
import app.mauri.sukrara.integrations.localserver.XdripState
import app.mauri.sukrara.integrations.upload.UploadStatus
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * #181 ("in settings we had app icons in some items, bring all of them back"): every Share with apps row that stands
 * for another app or service shows that app's own icon, as the PoC's did (its AppIconsTest.serviceRowsHaveTheirIcon):
 * LibreView, Tidepool, the Nightscout upload and API, the xDrip+ server, Health Connect, each app that reads the xDrip+
 * server (Garmin Connect), each app that gets glucose broadcasts, and the accounts history comes back from. The other
 * rows of those groups keep a symbol, so the titles line up. Review images: build/outputs/roborazzi/icons-*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ServiceRowsTest {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-10-27T14:00:00Z")

    private fun show(content: @Composable () -> Unit) =
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainer).verticalScroll(rememberScrollState())) { content() }
            }
        }

    /** The row titled [title] carries the icon tagged [tag]. */
    private fun assertRowIcon(
        title: String,
        tag: String,
    ) {
        val icons =
            compose
                .onAllNodes(
                    hasTestTag(tag) and hasAnyAncestor(hasTestTag(ROW_TAG) and hasAnyDescendant(hasText(title))),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes()
        assertTrue("$title has no $tag", icons.isNotEmpty())
    }

    /** The row titled [title] carries an icon of some kind (a symbol: SPA's 24 dp icon). */
    private fun assertRowSymbol(title: String) = assertRowIcon(title, ROW_ICON_TAG)

    /** The icons load off the main thread: let them arrive before the review image. */
    private fun capture(name: String) {
        Thread.sleep(300)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/icons-$name.png")
    }

    @Test
    fun shareWithAppsRowsShowTheirApps() {
        show {
            AppsContent(
                AppsUi(
                    wearer = true,
                    xdripOn = true,
                    xdrip = XdripState.Listening(null),
                    readers = listOf(ServerReader("com.garmin.android.apps.connectmobile", "Garmin Connect")),
                    nightscout = UploadStatus.Off,
                ),
                now,
                AppsActions(),
            )
        }
        assertRowIcon("LibreView", serviceIconTag(Service.LibreView))
        assertRowIcon("Tidepool", serviceIconTag(Service.Tidepool))
        assertRowIcon("Nightscout upload", serviceIconTag(Service.Nightscout))
        assertRowIcon("xDrip+ server", serviceIconTag(Service.Xdrip))
        assertRowIcon("Garmin Connect", packageIconTag("com.garmin.android.apps.connectmobile"))
        assertRowIcon("Health Connect", serviceIconTag(Service.HealthConnect))
        assertRowIcon("Nightscout API on this phone", serviceIconTag(Service.Nightscout))
        for (t in listOf("Send glucose to apps", "Import history", "Export data")) assertRowSymbol(t)
        capture("apps")
    }

    @Test
    fun broadcastAppsShowTheirIcons() {
        show {
            BroadcastsContent(
                BcUi(
                    true,
                    listOf(
                        BcApp("app.aaps", "AAPS", setOf(BroadcastPayloads.XDRIP), allowed = true),
                        BcApp(
                            "com.example.changed",
                            "Changed app",
                            setOf(BroadcastPayloads.LIBRE),
                            allowed = false,
                            problem = AppProblem.SignerChanged,
                        ),
                        BcApp("com.example.old", "Old app", emptySet(), allowed = true, problem = AppProblem.NotInstalled),
                    ),
                    BcStatus(null, null),
                ),
                now,
                BcActions(),
            )
        }
        assertRowIcon("AAPS", packageIconTag("app.aaps"))
        assertRowIcon("Changed app", packageIconTag("com.example.changed"))
        assertRowIcon("Old app", packageIconTag("com.example.old"))
        assertRowSymbol("Remove Changed app")
        capture("broadcasts")
    }

    @Test
    fun aFollowersAllowedAppsShowTheirIcons() {
        show {
            BroadcastsContent(
                BcUi(false, listOf(BcApp("app.aaps", "AAPS", setOf(BroadcastPayloads.XDRIP), allowed = true)), BcStatus(null, null)),
                now,
                BcActions(),
            )
        }
        assertRowIcon("Remove AAPS", packageIconTag("app.aaps"))
    }

    @Test
    fun importAccountsShowTheirIcons() {
        show { ImportContent(ImportUi(true, ImportState.Idle, tidepoolSignedIn = false), ImportActions()) }
        assertRowIcon("Tidepool", serviceIconTag(Service.Tidepool))
        assertRowIcon("LibreView", serviceIconTag(Service.LibreView))
    }
}
