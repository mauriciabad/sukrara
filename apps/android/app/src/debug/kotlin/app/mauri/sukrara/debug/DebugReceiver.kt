// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import app.mauri.sukrara.LaunchPlan
import app.mauri.sukrara.RoleRecord
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertReading
import app.mauri.sukrara.alerts.Firing
import app.mauri.sukrara.alerts.Side
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.graph
import app.mauri.sukrara.monitor.MonitoringService
import app.mauri.sukrara.monitor.alerts.AlertChannels
import app.mauri.sukrara.monitor.service.StartReason
import app.mauri.sukrara.pen.PenReadActivity
import app.mauri.sukrara.pen.opennov.PenDose
import app.mauri.sukrara.pen.opennov.PenRead
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action ?: return
        val handled = DebugFakes.run(context, action, intent)
        if (!handled) Log.w(TAG, "unknown debug action $action")
    }

    private companion object {
        const val TAG = "SukraraDebug"
    }
}

/**
 * Every debug command that replaces the sensor or shows / writes made-up data (CLAUDE.md: debug builds only, this
 * whole source set is compiled out of release).
 * `adb shell am broadcast -n app.mauri.sukrara/.debug.DebugReceiver -a app.mauri.sukrara.debug.<NAME> …`.
 */
object DebugFakes {
    private const val PREFIX = "app.mauri.sukrara.debug."

    private val commands =
        mapOf<String, (Context, Intent) -> Unit>(
            PREFIX + "SYNTHETIC" to ::synthetic,
            PREFIX + "PEN_READ" to ::penRead,
            PREFIX + "PEN_TAGS" to ::penTags,
            PREFIX + "PEN_JUGGLUCO" to ::penJuggluco,
            PREFIX + "ALERTS" to ::alerts,
            PREFIX + "RELAYS" to ::relays,
            PREFIX + "UPDATES" to ::updates,
        )

    /** Runs [action] when it is a known fake; false otherwise. */
    fun run(
        context: Context,
        action: String,
        intent: Intent,
    ): Boolean {
        val command = commands[action] ?: return false
        command(context, intent)
        return true
    }

    /**
     * `SYNTHETIC --ez on true|false [--ez frozen true] [--ei cycle M] [--ei endsInMin N] [--ei warmupMin W]`: the synthetic wearer source replaces the sensor (and makes this
     * phone the wearer if setup hasn't chosen a role), or goes away again. `cycle` puts "now" at minute M of the curve's
     * 3-hour cycle (135 = its lowest point, 45 mg/dL; 45 = its highest); `endsInMin` makes the sensor end N minutes from now
     * (the sensor-end warning shows from 6 h before); `warmupMin` makes it a new sensor warming up for W more minutes (no
     * readings, the warm-up countdown on the card and the notification; ready → readings start). The monitoring service restarts with the
     * new source; from the background Android may refuse that start (then "Monitoring stopped" shows: open the app).
     */
    private fun synthetic(
        context: Context,
        intent: Intent,
    ) {
        val on = intent.getBooleanExtra("on", true)
        val graph = context.graph
        graph.scope.launch {
            graph.ready.await()
            val cycleMinute = intent.getIntExtra("cycle", -1)
            val shift = if (cycleMinute >= 0) SyntheticSource.shiftFor(graph.clock.now(), cycleMinute) else 0L
            val endsInMin = intent.getIntExtra("endsInMin", -1)
            val endsAt = if (endsInMin >= 0) graph.clock.now() + java.time.Duration.ofMinutes(endsInMin.toLong()) else null
            val warmupMin = intent.getIntExtra("warmupMin", -1)
            val readyAt = if (warmupMin > 0) graph.clock.now() + java.time.Duration.ofMinutes(warmupMin.toLong()) else null
            SyntheticSource.set(
                context,
                on,
                frozen = intent.getBooleanExtra("frozen", false),
                shift = shift,
                endsAt = endsAt,
                readyAt = readyAt,
            )
            if (on) graph.role.update { if (it.role == null) RoleRecord(Role.Wearer) else it }
            context.stopService(Intent(context, MonitoringService::class.java))
            graph.monitor.starter.start(StartReason.App)
        }
    }

    /**
     * `UPDATES --es server http://10.0.2.2:8765/|off [--es cert <sha256>]`: the GitHub version's updater (#188) reads
     * its releases from a local test server instead of GitHub (`<server>releases` in GitHub's List-releases form, APKs
     * under `<server>download/`) and trusts the certificate [cert] (SHA-256, upper-case hex as apksigner prints it; by
     * default this build's own, the debug key) instead of the release key; it checks once at every start too. `off` =
     * GitHub and the release key again. Saved, then the app process ends: open the app. The full flavor only (the
     * play flavor has no updater). Release builds have no such choice.
     */
    private fun updates(
        context: Context,
        intent: Intent,
    ) {
        val server = intent.getStringExtra("server")?.trim().orEmpty()
        val prefs = context.getSharedPreferences(UPDATES_PREFS, Context.MODE_PRIVATE)
        if (server == "off") {
            // written before the process ends below
            prefs.edit(commit = true) { clear() }
        } else {
            // an http(s) URL ending in / (OkHttp parses only those); cleartext reaches 10.0.2.2 / loopback only (the
            // debug network security config)
            if (server.toHttpUrlOrNull() == null || !server.endsWith("/")) {
                Log.w("SukraraDebug", "UPDATES: '$server' isn't off or a server URL ending in /; nothing changed")
                return
            }
            val cert = intent.getStringExtra("cert")?.trim()?.uppercase() ?: ownCert(context)
            prefs.edit(commit = true) {
                putString("server", server)
                putString("cert", cert)
            }
        }
        Log.i("SukraraDebug", "UPDATES: $server from the next start; ending the app process now")
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /** The `UPDATES` command's test server and certificate (BuildVariant), or null: GitHub and the release key. */
    fun updateServer(context: Context): Pair<String, String>? {
        val prefs = context.getSharedPreferences(UPDATES_PREFS, Context.MODE_PRIVATE)
        val server = prefs.getString("server", null) ?: return null
        val cert = prefs.getString("cert", null) ?: return null
        return server to cert
    }

    private const val UPDATES_PREFS = "debug_updates"

    /** This build's signing certificate (the debug key), SHA-256 upper-case hex. */
    private fun ownCert(context: Context): String {
        val info =
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags
                    .of(
                        android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
                            .toLong(),
                    ),
            )
        val der = checkNotNull(info.signingInfo).apkContentsSigners.first().toByteArray()
        return java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(der)
            .joinToString("") { "%02X".format(it) }
    }

    /**
     * `RELAYS --es urls ws://10.0.2.2:7447[,…]|public|off`: where this debug build's relay connections go, for both
     * roles (`LaunchPlan`; nothing saved = off: no relay). URLs = only those (a local test relay, tools/relay; the
     * emulator reaches the computer's `127.0.0.1` as `10.0.2.2`), whatever relays the wearer's invite names;
     * `public` = the public relays on purpose. Saved, then the app process ends so every relay connection is made
     * again from it on the next start (open the app). A choice that isn't one is refused and nothing changes.
     */
    private fun relays(
        context: Context,
        intent: Intent,
    ) {
        val choice = intent.getStringExtra("urls")?.trim().orEmpty()
        val access = LaunchPlan.relays(choice)
        if (access == null) {
            Log.w("SukraraDebug", "RELAYS: '$choice' isn't off, public or ws:// / wss:// URLs; nothing changed")
            return
        }
        DebugRelays.save(context, choice)
        Log.i("SukraraDebug", "RELAYS: $access from the next start; ending the app process now")
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /**
     * `ALERTS [--es kinds low,falling_fast] [--el gapMs 0] [--ez reminder true]`: posts made-up alerts of [kinds] (contract ids) through the
     * real notifier, one after another [gapMs] apart, with the alert settings in use (each kind's alarm, sound,
     * channel). Outside the alert runtime: their actions change nothing, and they replace a real alert showing in the
     * same slot (emulator only). For checking how Android plays alerts that
     * come close together (`adb logcat -b events -s notification_alert`: politeness state and mute reason).
     * `reminder`: plain notifications on the same channels as `CATEGORY_REMINDER` instead (the comparison).
     */
    private fun alerts(
        context: Context,
        intent: Intent,
    ) {
        val graph = context.graph
        graph.scope.launch {
            graph.ready.await()
            val settings =
                graph.alerts.store.settings
                    .first()
            val gap = intent.getLongExtra("gapMs", 0L)
            val reminder = intent.getBooleanExtra("reminder", false)
            val notifier = graph.alerts.notifier
            val kinds =
                (intent.getStringExtra("kinds") ?: "low,falling_fast").split(',').mapNotNull { w ->
                    AlertKind.entries.firstOrNull { it.wire == w.trim() }.also {
                        if (it ==
                            null
                        ) {
                            Log.w("SukraraDebug", "ALERTS: no kind $w")
                        }
                    }
                }
            kinds.forEachIndexed { i, kind ->
                if (i > 0 && gap > 0) kotlinx.coroutines.delay(gap)
                val now = graph.clock.now().epochSecond
                val reading = AlertReading(now, if (kind.side == Side.HIGH) 260 else 62, -3.0)
                if (reminder) {
                    // the control: a plain notification on the kind's channel posted as CATEGORY_REMINDER
                    val channel = AlertChannels.id(context, kind, settings.rule(kind).alarm)
                    val n =
                        android.app.Notification
                            .Builder(context, channel)
                            .setSmallIcon(context.applicationInfo.icon)
                            .setContentTitle("Debug reminder ${kind.wire}")
                            .setCategory(android.app.Notification.CATEGORY_REMINDER)
                            .build()
                    context.getSystemService(android.app.NotificationManager::class.java).notify(9100 + i, n)
                } else {
                    notifier.show(
                        Firing(kind, "debug-$now-$i", 1, reading, 70.0, now),
                        settings,
                        silent = false,
                        who = null,
                        relaxedMin = 0,
                    )
                }
            }
        }
    }

    /**
     * `PEN_READ [--es doses "-9:8.5,-8:1"] [--es serial AB1234567] [--ez first true] [--el firstAgo 7200]`: a made-up
     * NovoPen read (minutes from now : units) goes through the real pen path (ledger, engine, treatment log) and its
     * card shows over the current app. Emulators have no NFC: this also shows the pen surfaces there. `first` = a first
     * read `firstAgo` minutes ago (default 10; nothing older is logged), so the doses that follow are new. A big read
     * (*N injections logged · Review*): `--ez first true --el firstAgo 7200 --es doses "-6000:6,-3000:5,-30:4"`.
     */
    private fun penRead(
        context: Context,
        intent: Intent,
    ) {
        val graph = context.graph
        graph.scope.launch {
            graph.ready.await()
            graph.pens.fakeNfc.value = true
            val now = graph.clock.now().epochSecond
            val penNow = 400 * 86400L
            val serial = intent.getStringExtra("serial") ?: "AB1234567"

            fun read(at: Long, doses: List<Pair<Long, Double>>) =
                PenRead(serial, "NovoPen", at, penNow + (at - now), doses.map { (t, u) -> PenDose(penNow - (now - t), t, u, 0x08000000) })
            // an earlier half-unit dose identifies the pen as an Echo Plus (an empty read would be the untested NovoPen 6)
            if (intent.getBooleanExtra("first", false)) {
                val firstAt = now - intent.getLongExtra("firstAgo", 10) * 60
                graph.pens.pens.onRead(read(firstAt, listOf(firstAt - 86400L to 4.5)))
            }
            val doses =
                (intent.getStringExtra("doses") ?: "-1:1,-1:8.5").split(',').mapIndexed { i, d ->
                    val (min, u) = d.split(':')
                    (now + min.toLong() * 60 + i) to u.toDouble()
                }
            graph.pens.pens.onRead(read(now, doses))
            // the read card is enabled a moment after fakeNfc (or never while reading is Off): a refused start is only logged
            kotlinx.coroutines.delay(500)
            runCatching {
                context.startActivity(
                    Intent(context, PenReadActivity::class.java)
                        .putExtra(PenReadActivity.PREVIEW, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { Log.w("SukraraDebug", "pen card not shown", it) }
        }
    }

    /**
     * `PEN_TAGS --ez blocked true|false`: shows the taps-blocked state (home banner, Settings › Insulin pens and
     * Settings › Sensor) as if Android blocked Sukrara's tag intents; real NFC is untouched. Also shows the pen surfaces on an emulator
     * without NFC. The screens read it on resume.
     */
    private fun penTags(
        context: Context,
        intent: Intent,
    ) {
        val graph = context.graph
        graph.pens.fakeNfc.value = true
        app.mauri.sukrara.core.data.nfc.TagIntents.fakeBlocked.value = intent.getBooleanExtra("blocked", true)
        graph.home.refresh()
    }

    /**
     * `PEN_JUGGLUCO --ez installed true|false`: Settings › Insulin pens shows *Juggluco also reads pens* as if Juggluco
     * were installed (pen reading On). The page reads it on resume.
     */
    private fun penJuggluco(
        context: Context,
        intent: Intent,
    ) {
        val graph = context.graph
        graph.pens.fakeNfc.value = true
        graph.pens.fakeJuggluco.value = intent.getBooleanExtra("installed", true)
    }
}
