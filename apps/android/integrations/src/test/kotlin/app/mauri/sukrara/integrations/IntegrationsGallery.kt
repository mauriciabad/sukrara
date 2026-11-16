// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.storage.ImportSource
import app.mauri.sukrara.designsystem.errors.Trouble
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.integrations.broadcasts.AppProblem
import app.mauri.sukrara.integrations.broadcasts.BcActions
import app.mauri.sukrara.integrations.broadcasts.BcApp
import app.mauri.sukrara.integrations.broadcasts.BcStatus
import app.mauri.sukrara.integrations.broadcasts.BcUi
import app.mauri.sukrara.integrations.broadcasts.BroadcastPayloads
import app.mauri.sukrara.integrations.broadcasts.BroadcastsContent
import app.mauri.sukrara.integrations.broadcasts.Hold
import app.mauri.sukrara.integrations.export.ExportActions
import app.mauri.sukrara.integrations.export.ExportContent
import app.mauri.sukrara.integrations.export.ExportData
import app.mauri.sukrara.integrations.export.ExportPeriod
import app.mauri.sukrara.integrations.export.ExportUi
import app.mauri.sukrara.integrations.garmin.AuraLinkState
import app.mauri.sukrara.integrations.garmin.AuraReport
import app.mauri.sukrara.integrations.garmin.AuraWatch
import app.mauri.sukrara.integrations.garmin.GarminActions
import app.mauri.sukrara.integrations.garmin.GarminContent
import app.mauri.sukrara.integrations.garmin.GarminUi
import app.mauri.sukrara.integrations.healthconnect.HcActions
import app.mauri.sukrara.integrations.healthconnect.HcStatus
import app.mauri.sukrara.integrations.healthconnect.HcUi
import app.mauri.sukrara.integrations.healthconnect.HealthConnectContent
import app.mauri.sukrara.integrations.healthconnect.HealthConnectSync
import app.mauri.sukrara.integrations.history.ImportActions
import app.mauri.sukrara.integrations.history.ImportContent
import app.mauri.sukrara.integrations.history.ImportFailure
import app.mauri.sukrara.integrations.history.ImportState
import app.mauri.sukrara.integrations.history.ImportTally
import app.mauri.sukrara.integrations.history.ImportUi
import app.mauri.sukrara.integrations.libreview.LibreViewActions
import app.mauri.sukrara.integrations.libreview.LibreViewContent
import app.mauri.sukrara.integrations.libreview.LvPageState
import app.mauri.sukrara.integrations.libreview.LvSettings
import app.mauri.sukrara.integrations.libreview.LvTakeOverUi
import app.mauri.sukrara.integrations.localserver.LanState
import app.mauri.sukrara.integrations.localserver.NightscoutApiContent
import app.mauri.sukrara.integrations.localserver.NsApiActions
import app.mauri.sukrara.integrations.localserver.NsApiState
import app.mauri.sukrara.integrations.localserver.NsApiUi
import app.mauri.sukrara.integrations.localserver.SecretView
import app.mauri.sukrara.integrations.localserver.ServerReader
import app.mauri.sukrara.integrations.localserver.XdripContent
import app.mauri.sukrara.integrations.localserver.XdripState
import app.mauri.sukrara.integrations.localserver.XdripUi
import app.mauri.sukrara.integrations.nightscout.NightscoutActions
import app.mauri.sukrara.integrations.nightscout.NightscoutContent
import app.mauri.sukrara.integrations.nightscout.NsCredentialKind
import app.mauri.sukrara.integrations.nightscout.NsPageState
import app.mauri.sukrara.integrations.nightscout.NsSettings
import app.mauri.sukrara.integrations.nightscout.NsTestUi
import app.mauri.sukrara.integrations.upload.UploadStatus
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.Instant

/**
 * The integration pages for the UX review (synthetic state only): light + dark × default + largest font, plus Spanish.
 * Images go to build/outputs/roborazzi (copied to the review folder; no goldens are committed).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class IntegrationsGallery {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-10-27T14:00:00Z")
    private val site = NsSettings(on = true, url = "https://my-ns.example.org")
    private val configured = NsPageState(site, NsCredentialKind.Token, UploadStatus.Ok(now - Duration.ofMinutes(3), 12))
    private val readings = (0L until 300L).map { Reading(now - Duration.ofMinutes(it * 5), 120, null, SourceId.Libre3) }
    private val export = ExportData(now - Duration.ofDays(14), now, readings, emptyList(), null)

    private val lvFrom = now - Duration.ofDays(2)

    private val gcm = ServerReader("com.garmin.android.apps.connectmobile", "Garmin Connect")
    private val ciq = ServerReader("com.garmin.connectiq", "Connect IQ")
    private val sent =
        AuraLinkState(
            garminConnect = true,
            answering = true,
            watches =
                listOf(
                    AuraWatch(
                        1,
                        "Descent Mk3i 43mm",
                        true,
                        true,
                        now - Duration.ofMinutes(1),
                        reading =
                            now - Duration.ofMinutes(1),
                    ),
                ),
            fetched = now - Duration.ofMinutes(3),
            fetchOpen = true,
        )

    private val pages: List<Pair<String, @Composable () -> Unit>> =
        listOf(
            "apps-off" to { AppsContent(AppsUi(true, XdripState.Off, emptyList(), UploadStatus.Off), now, AppsActions()) },
            "apps-on" to {
                AppsContent(
                    AppsUi(
                        true,
                        XdripState.Listening(now - Duration.ofMinutes(2)),
                        listOf(gcm),
                        configured.status,
                        UploadStatus.Ok(now - Duration.ofMinutes(1), 1),
                        broadcastApps = 2,
                        broadcasts = BcStatus(now - Duration.ofMinutes(1), null),
                        healthConnect = HcStatus.Written(now - Duration.ofMinutes(1), 1),
                        aura = sent,
                    ),
                    now,
                    AppsActions(),
                )
            },
            // one watch with the face, current
            "garmin-sent" to { GarminContent(GarminUi(sent, XdripState.Off, listOf(gcm)), now, GarminActions()) },
            // two watches: one away with an older reading, one without the face; the xDrip+ server on for other faces
            "garmin-two-watches" to {
                GarminContent(
                    GarminUi(
                        sent.copy(
                            watches =
                                listOf(
                                    AuraWatch(
                                        1,
                                        "Descent Mk3i 43mm",
                                        false,
                                        true,
                                        now - Duration.ofMinutes(25),
                                        reading =
                                            now - Duration.ofMinutes(25),
                                    ),
                                    AuraWatch(2, "Forerunner 265", true, false),
                                ),
                            fetched = null,
                        ),
                        XdripState.Listening(now - Duration.ofMinutes(4)),
                        listOf(gcm),
                    ),
                    now,
                    GarminActions(),
                )
            },
            // Garmin Connect loses the messages: the face keeps up by asking every 5 minutes
            "garmin-slow" to {
                GarminContent(
                    GarminUi(
                        sent.copy(
                            watches =
                                listOf(
                                    AuraWatch(
                                        1,
                                        "Descent Mk3i 43mm",
                                        true,
                                        true,
                                        failed = now,
                                        noAnswer = true,
                                        reading =
                                            now - Duration.ofMinutes(9),
                                    ),
                                ),
                            fetched = now - Duration.ofMinutes(2),
                            report =
                                AuraReport(
                                    now - Duration.ofMinutes(2),
                                    emptyList(),
                                    now - Duration.ofMinutes(2),
                                    now - Duration.ofMinutes(3),
                                ),
                        ),
                    ),
                    now,
                    GarminActions(),
                )
            },
            "garmin-no-face" to {
                GarminContent(
                    GarminUi(
                        AuraLinkState(
                            garminConnect = true,
                            answering = true,
                            watches = listOf(AuraWatch(1, "Descent Mk3i 43mm", true, false)),
                        ),
                    ),
                    now,
                    GarminActions(),
                )
            },
            "garmin-no-watch" to { GarminContent(GarminUi(AuraLinkState(garminConnect = true, answering = true)), now, GarminActions()) },
            "garmin-not-answering" to {
                GarminContent(GarminUi(AuraLinkState(garminConnect = true, answering = false)), now, GarminActions())
            },
            "garmin-no-gcm" to { GarminContent(GarminUi(AuraLinkState(garminConnect = false)), now, GarminActions()) },
            "garmin-off" to { GarminContent(GarminUi(AuraLinkState(on = false, garminConnect = true)), now, GarminActions()) },
            "xdrip-off" to { XdripContent(XdripUi(false, XdripState.Off), now, {}) },
            "xdrip-on" to { XdripContent(XdripUi(true, XdripState.Listening(now - Duration.ofMinutes(2)), listOf(gcm, ciq)), now, {}) },
            "xdrip-busy" to { XdripContent(XdripUi(true, XdripState.PortBusy, listOf(gcm)), now, {}) },
            "xdrip-juggluco" to { XdripContent(XdripUi(true, XdripState.LeftToJuggluco, listOf(gcm)), now, {}) },
            "import-idle" to
                {
                    ImportContent(
                        ImportUi(true, ImportState.Idle, tidepoolSignedIn = true, tidepoolEmail = "me@example.com"),
                        ImportActions(),
                    )
                },
            "import-running" to {
                ImportContent(
                    ImportUi(true, ImportState.Running(ImportSource.Tidepool, 5, 14, ImportTally(readings = 1440, entries = 37)), true),
                    ImportActions(),
                )
            },
            "import-done" to {
                ImportContent(
                    ImportUi(
                        true,
                        ImportState.Done(
                            ImportSource.LibreView,
                            ImportTally(8640, 212, alreadyHere = 310, outsidePeriod = 1200, skipped = 4),
                            now,
                        ),
                        false,
                    ),
                    ImportActions(),
                )
            },
            "import-signed-out" to {
                ImportContent(
                    ImportUi(true, ImportState.Failed(ImportSource.Tidepool, ImportFailure.SignedOut, ImportTally()), false),
                    ImportActions(),
                )
            },
            "import-dates" to {
                ImportContent(
                    ImportUi(
                        true,
                        ImportState.Failed(ImportSource.File, ImportFailure.UnknownDates("11-10-2026 9:15 PM UTC"), ImportTally()),
                        false,
                        libreViewEmail = "me@example.com",
                    ),
                    ImportActions(),
                )
            },
            "import-follower" to { ImportContent(ImportUi(false, ImportState.Idle, false), ImportActions()) },
            "apps-busy-follower" to
                { AppsContent(AppsUi(false, XdripState.PortBusy, emptyList(), UploadStatus.Off), now, AppsActions()) },
            "nsapi-off" to { NightscoutApiContent(NsApiUi(false, NsApiState.Off), now, NsApiActions()) },
            "nsapi-on" to {
                NightscoutApiContent(NsApiUi(true, NsApiState.Listening(now - Duration.ofMinutes(1))), now, NsApiActions())
            },
            "nsapi-secret" to {
                NightscoutApiContent(
                    NsApiUi(true, NsApiState.Listening(null), SecretView.Shown("0f1e2d3c4b5a69788796a5b4c3d2e1f0")),
                    now,
                    NsApiActions(),
                )
            },
            "nsapi-lan-on" to {
                NightscoutApiContent(
                    NsApiUi(
                        true,
                        NsApiState.Listening(now - Duration.ofMinutes(1), "192.168.1.23"),
                        SecretView.Shown("0f1e2d3c4b5a69788796a5b4c3d2e1f0", "9a8b7c6d5e4f30211203f4e5d6c7b8a9"),
                        lanOn = true,
                        lan = LanState.Listening("192.168.1.23", "192.168.1.40", now - Duration.ofMinutes(2)),
                        lanSecretMade = true,
                    ),
                    now,
                    NsApiActions(),
                )
            },
            "nsapi-lan-other" to {
                NightscoutApiContent(NsApiUi(true, NsApiState.Listening(null), lanOn = true, lan = LanState.OtherWifi), now, NsApiActions())
            },
            "nsapi-lan-permission" to {
                NightscoutApiContent(
                    NsApiUi(true, NsApiState.Listening(null), lanOn = true, lan = LanState.NoPermission),
                    now,
                    NsApiActions(),
                )
            },
            "nsapi-unreadable" to {
                NightscoutApiContent(NsApiUi(true, NsApiState.SecretUnreadable, SecretView.Unreadable), now, NsApiActions())
            },
            "nightscout-off" to {
                NightscoutContent(NsPageState(NsSettings(), null, UploadStatus.Off), true, NsTestUi.Idle, now, NightscoutActions())
            },
            "nightscout-ok" to { NightscoutContent(configured, true, NsTestUi.Idle, now, NightscoutActions()) },
            "nightscout-auth-failed" to {
                NightscoutContent(configured.copy(status = UploadStatus.AuthFailed), true, NsTestUi.Idle, now, NightscoutActions())
            },
            "nightscout-paused" to {
                NightscoutContent(
                    configured.copy(status = UploadStatus.Paused("xDrip-LibreReceiver")),
                    true,
                    NsTestUi.Idle,
                    now,
                    NightscoutActions(),
                )
            },
            "libreview-signed-out" to {
                LibreViewContent(
                    LvPageState(LvSettings(), false, "", UploadStatus.Off, null),
                    true,
                    LvTakeOverUi.Idle,
                    now,
                    LibreViewActions(),
                    false,
                    {},
                )
            },
            "libreview-ok" to {
                LibreViewContent(
                    LvPageState(LvSettings(on = true), true, "person@example.com", UploadStatus.Ok(now - Duration.ofMinutes(4), 3), lvFrom),
                    true,
                    LvTakeOverUi.Idle,
                    now,
                    LibreViewActions(),
                    false,
                    {},
                )
            },
            // the sensor's glucose from before the switch-on is on the phone: offered, never sent by itself
            "libreview-earlier" to {
                LibreViewContent(
                    LvPageState(
                        LvSettings(on = true),
                        true,
                        "person@example.com",
                        UploadStatus.Ok(now - Duration.ofMinutes(4), 288),
                        lvFrom,
                        earlierFrom = lvFrom - Duration.ofDays(8),
                    ),
                    true,
                    LvTakeOverUi.Idle,
                    now,
                    LibreViewActions(),
                    false,
                    {},
                )
            },
            // a family that can't go up (read through Juggluco): said at once in the switch, in the status once it stalls
            "libreview-other-source" to {
                LibreViewContent(
                    LvPageState(
                        LvSettings(on = true),
                        true,
                        "person@example.com",
                        UploadStatus.Behind(now - Duration.ofHours(3), null, SourceId.Juggluco),
                        lvFrom,
                        otherSource = SourceId.Juggluco,
                    ),
                    true,
                    LvTakeOverUi.Idle,
                    now,
                    LibreViewActions(),
                    false,
                    {},
                )
            },
            // a Libre 3 LibreView doesn't register: the reason, and that it is tried again by itself
            "libreview-behind" to {
                LibreViewContent(
                    LvPageState(
                        LvSettings(on = true),
                        true,
                        "person@example.com",
                        UploadStatus.Behind(now - Duration.ofDays(4), Trouble.Refused),
                        lvFrom,
                    ),
                    true,
                    LvTakeOverUi.Idle,
                    now,
                    LibreViewActions(),
                    false,
                    {},
                )
            },
            // another device uploads for the account: the status row opens Take over
            "libreview-auth-failed" to {
                LibreViewContent(
                    LvPageState(LvSettings(on = true), true, "person@example.com", UploadStatus.AuthFailed, lvFrom),
                    true,
                    LvTakeOverUi.Idle,
                    now,
                    LibreViewActions(),
                    false,
                    {},
                )
            },
            // LibreView no longer accepts the saved password: the account screen asks again
            "libreview-sign-in-again" to {
                LibreViewContent(
                    LvPageState(LvSettings(on = true), false, "person@example.com", UploadStatus.Setup, lvFrom, signInAgain = true),
                    true,
                    LvTakeOverUi.Idle,
                    now,
                    LibreViewActions(),
                    false,
                    {},
                )
            },
            "broadcasts-none" to { BroadcastsContent(BcUi(true, emptyList(), BcStatus(null, null)), now, BcActions()) },
            "broadcasts-on" to {
                BroadcastsContent(
                    BcUi(
                        true,
                        listOf(
                            BcApp("app.aaps", "AAPS", setOf(BroadcastPayloads.XDRIP), allowed = true),
                            BcApp(
                                "de.michelinside.glucodatahandler",
                                "GlucoDataHandler",
                                BroadcastPayloads.ACTIONS.toSet(),
                                allowed = true,
                            ),
                            BcApp("com.example.watch", "Watch app", setOf(BroadcastPayloads.GLUCODATA), allowed = false),
                        ),
                        BcStatus(now - Duration.ofMinutes(1), null),
                    ),
                    now,
                    BcActions(),
                )
            },
            "broadcasts-changed-gone" to {
                BroadcastsContent(
                    BcUi(
                        true,
                        listOf(
                            BcApp("app.aaps", "AAPS", setOf(BroadcastPayloads.XDRIP), allowed = false, problem = AppProblem.SignerChanged),
                            BcApp("com.example.old", "com.example.old", emptySet(), allowed = true, problem = AppProblem.NotInstalled),
                        ),
                        BcStatus(now - Duration.ofHours(2), Hold.NoApp),
                    ),
                    now,
                    BcActions(),
                )
            },
            "broadcasts-follower" to { BroadcastsContent(BcUi(false, null, BcStatus(null, null)), now, BcActions()) },
            "health-off" to { HealthConnectContent(HcUi(false, HcStatus.Off), now, HcActions()) },
            "health-denied" to { HealthConnectContent(HcUi(false, HcStatus.Off, denied = true), now, HcActions()) },
            "health-on" to { HealthConnectContent(HcUi(true, HcStatus.Written(now - Duration.ofMinutes(1), 1)), now, HcActions()) },
            "health-revoked" to { HealthConnectContent(HcUi(true, HcStatus.NeedsPermission), now, HcActions()) },
            "health-rate-limited" to {
                HealthConnectContent(
                    HcUi(true, HcStatus.Failed(HealthConnectSync.ERROR_RATE_LIMIT, now - Duration.ofMinutes(3))),
                    now,
                    HcActions(),
                )
            },
            "health-follower" to { HealthConnectContent(HcUi(false, HcStatus.Follower), now, HcActions()) },
            "export" to { ExportContent(ExportUi(ExportPeriod.Days(14), true, export), ExportActions()) },
            "export-follower-counting" to { ExportContent(ExportUi(ExportPeriod.All, false, null, follower = true), ExportActions()) },
        )

    private fun gallery(
        dark: Boolean,
        fontScale: Float,
        prefix: String = "",
    ) {
        var current by mutableStateOf(pages.first().second)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SukraraTheme(dark = dark, dynamic = false) {
                    Column(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).verticalScroll(rememberScrollState()),
                    ) {
                        // the page's text colour, as the app's settings scaffold gives it (dark titles stay legible)
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { current() }
                    }
                }
            }
        }
        val theme = if (dark) "dark" else "light"
        val font = if (fontScale > 1f) "font2x" else "font1x"
        val repeats = mutableListOf<String>()
        pages.forEach { (name, page) ->
            current = page
            compose.waitForIdle()
            // once per page: no row icon twice on one page
            if (!dark && fontScale == 1f && prefix.isEmpty()) {
                compose.repeatedRowIcons().takeIf { it.isNotEmpty() }?.let { repeats += "$name repeats $it" }
            }
            // app icons load off the main thread
            Thread.sleep(150)
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/integrations-$prefix$name-$theme-$font.png")
        }
        assertTrue(repeats.joinToString("\n"), repeats.isEmpty())
    }

    @Test fun lightDefault() = gallery(dark = false, fontScale = 1f)

    @Test fun darkDefault() = gallery(dark = true, fontScale = 1f)

    @Test fun lightLargest() = gallery(dark = false, fontScale = 2f)

    @Test fun darkLargest() = gallery(dark = true, fontScale = 2f)

    @Test
    @Config(qualifiers = "es")
    fun spanish() = gallery(dark = false, fontScale = 1f, prefix = "es-")
}
