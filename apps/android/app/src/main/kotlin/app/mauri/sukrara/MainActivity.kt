// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.NavTargets
import app.mauri.sukrara.designsystem.dialog.LocalGlucoseUnit
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.graph.ClockText
import app.mauri.sukrara.monitor.service.StartReason
import app.mauri.sukrara.sensor.pairing.PairingLoad
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The one activity: launcher, invite links (`sukrara:` / `https://sukrara.mauri.app/join`). */
class MainActivity : ComponentActivity() {
    private var deepLink by mutableStateOf<Uri?>(null)

    /** A request to open the new-sensor flow, until the navigation has opened it. */
    private var openNewSensor by mutableStateOf(false)

    /** The pen whose page a pen read card asked for, until the navigation has opened it. */
    private var openPen by mutableStateOf<PenTarget?>(null)

    /** Health Connect asked for the privacy rationale, until the navigation has opened the Health Connect page. */
    private var openHealth by mutableStateOf(false)

    /** The 24-hour setting and time zone the screens show times in ([ClockText.signature]), read again on every return. */
    private var clockLook by mutableStateOf(ClockText.signature())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            deepLink = intent?.data
            askNewSensor(intent)
            openPen = penPage(intent)
            openHealth = healthRationale(intent)
        }
        val graph = graph
        setContent {
            // the person's glucose unit: every number the screens show and every number input follows it
            val unit by remember { graph.graphConfig.map { it.unit }.distinctUntilChanged() }.collectAsState(GlucoseUnit.MGDL)
            SukraraTheme {
                CompositionLocalProvider(LocalGlucoseUnit provides unit, LocalClockLook provides clockLook) {
                    SukraraNavigation(
                        graph,
                        deepLink,
                        openNewSensor,
                        { openNewSensor = false },
                        Modifier.fillMaxSize(),
                        openPen = openPen,
                        onPenShow = { openPen = null },
                        openHealthConnect = openHealth,
                        onHealthConnectShow = { openHealth = false },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLink = intent.data
        askNewSensor(intent)
        penPage(intent)?.let { openPen = it }
        if (healthRationale(intent)) openHealth = true
    }

    /**
     * The new-sensor flow for [i] (a notification, a widget, a sensor tap), only when Sukrara asked for it
     * ([SensorSetupGate]: another app's intent just opens the app). A tap only when no sensor is being read: resting
     * the phone on the running sensor must not re-pair it (a new pairing would interrupt its readings); then the app
     * just opens.
     */
    private fun askNewSensor(i: Intent?) {
        val request = SensorSetupGate.request(i) ?: return
        if (!request.fromSensorTap) {
            openNewSensor = true
            return
        }
        lifecycleScope.launch {
            val current = (graph.sensor.pairings.load() as? PairingLoad.Ok)?.pairing
            if (current == null || current.ended || current.failed != null) openNewSensor = true
        }
    }

    override fun onResume() {
        super.onResume()
        // *Use 24-hour format* or the time zone changed while Sukrara was away (#166): every time shown redraws now
        clockLook = ClockText.signature()
        // permissions and the checklist change outside the app
        graph.home.refresh()
        // opening the app is an allowed foreground-service start: (re)start monitoring if something is set up; not
        // once the app has gone to the background again (Android would refuse and "Monitoring stopped" would show)
        lifecycleScope.launch {
            graph.ready.await()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) graph.startMonitoring(StartReason.App)
        }
        // opening the app while a very low rings is an answer on this phone: the followers' escalation is withdrawn (#39)
        lifecycleScope.launch { graph.alerts.runtime.acknowledge() }
    }
}

/**
 * What the screens' clock times were drawn with ([ClockText.signature]). Nothing reads it: a static local, so a new value
 * recomposes everything below (the remembered state stays) and every time shown formats again in the new setting.
 */
private val LocalClockLook = staticCompositionLocalOf { "" }

/**
 * The pen page a pen read card's *Check it* / *Review* asks for (only the wearer's phone has pens; Settings checks
 * again).
 */
private fun penPage(i: Intent?): PenTarget? =
    i
        ?.takeIf { it.action == NavTargets.ACTION_PEN_PAGE }
        ?.getStringExtra(NavTargets.EXTRA_PEN_SERIAL)
        ?.takeIf { it.isNotBlank() }
        ?.let { PenTarget(it, i.getBooleanExtra(NavTargets.EXTRA_PEN_REVIEW, false)) }

/** Health Connect's link to this app's privacy rationale (the manifest's `.HealthPermissionsRationale` alias). */
private fun healthRationale(i: Intent?): Boolean =
    i?.action == Intent.ACTION_VIEW_PERMISSION_USAGE && i.hasCategory(HEALTH_PERMISSIONS_CATEGORY)

private const val HEALTH_PERMISSIONS_CATEGORY = "android.intent.category.HEALTH_PERMISSIONS"
