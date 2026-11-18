// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.setup

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.designsystem.icons.Service
import app.mauri.sukrara.designsystem.icons.serviceIconTag
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Setup's connect steps show the app or service they connect by its own icon before the title (as the Mac's setup):
 * Juggluco, a Nightscout site, LibreLinkUp. Review images: build/outputs/roborazzi/connect-step-*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-w411dp-h891dp-xxhdpi")
class ConnectStepIconTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun ui(kind: SourceKind) =
        SetupUi(
            step = SetupStep.Sensor,
            index = 5,
            count = 6,
            facts = SetupFacts(spec = SourceSpec(Person.Yours, kind), ownSensorLive = false, hasIdentity = false),
            record = SetupRecord(),
            readiness = AlertReadiness(emptySet()),
            alerts =
                AlertThresholds(
                    app.mauri.sukrara.alerts
                        .AlertSettings(),
                ),
            gate = RestoreGate.evaluate(false),
            share = app.mauri.sukrara.share.owner.OwnerShareState.NoIdentity,
            restore = RestoreUi.Idle,
            busy = false,
            single = false,
            canBack = true,
        )

    private fun check(
        kind: SourceKind,
        service: Service,
    ) {
        compose.setContent { SetupContent(ui(kind), SetupActions()) }
        compose.onAllNodesWithTag(serviceIconTag(service), useUnmergedTree = true).assertCountEquals(1)
        // the icon loads off the main thread
        Thread.sleep(300)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/connect-step-${service.name}.png")
    }

    @Test
    fun jugglucoStepShowsJuggluco() = check(SourceKind.PhoneApp, Service.Juggluco)

    @Test
    fun siteStepShowsNightscout() = check(SourceKind.Nightscout, Service.Nightscout)

    @Test
    fun lluStepShowsLibreLinkUp() = check(SourceKind.LibreLinkUp, Service.LibreLinkUp)
}
