// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.readiness

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.AlertReadinessSource
import app.mauri.sukrara.core.model.AlertRequirement
import app.mauri.sukrara.core.model.DispatcherProvider
import app.mauri.sukrara.core.model.DndStatus
import app.mauri.sukrara.core.model.SoundTest
import app.mauri.sukrara.core.model.UnlockedSound
import app.mauri.sukrara.monitor.alerts.AlertCategory
import app.mauri.sukrara.monitor.alerts.AlertChannels
import app.mauri.sukrara.monitor.notify.Channels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * THE alert checklist of the app: home's reliability row, the settings Reliability page and the setup
 * permission step all render this one instance (`AppGraph.alerts.readiness`). It re-reads the phone when a screen
 * asks ([refresh], on every resume), when the alert settings or the role's source change, and while someone collects
 * it, on the system broadcasts that flip an item without leaving the app (Bluetooth, Do Not Disturb, a blocked
 * channel, the exact-alarm grant).
 *
 * Items: the alert runtime's last steps worked ([failedSteps], the hub's alert state); notifications on; the
 * full-screen intent when a kind rings like an alarm; exact alarms; the battery exemption (and not "Restricted"); the
 * alert channels in use not blocked or silenced; the sound while unlocked answered from a test ([testUnlockedSound]);
 * Bluetooth on and allowed when [needsBluetooth]. Besides, never a fault: where Sukrara stands with Do Not Disturb
 * ([DndHold]: an exception or not, what a mode on now holds back), changed with [letThroughDnd] (#184).
 */
class AndroidAlertReadiness(
    context: Context,
    private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val alertSettings: Flow<AlertSettings>,
    private val needsBluetooth: Flow<Boolean>,
    private val failedSteps: Flow<Set<String>>,
    /** The person's answer about *Vibrate only when unlocked* (schema `alerts.unlockedSound`) and how to store it. */
    private val unlockedSound: Flow<UnlockedSound>,
    private val saveUnlockedSound: suspend (UnlockedSound) -> Unit,
    /** Posts [AlertKind]'s test alert without Sukrara's own unlocked sound ([AlertReadinessSource.testUnlockedSound]). */
    private val sendUnlockedTest: (AlertKind, AlertSettings) -> Unit = { _, _ -> },
) : AlertReadinessSource {
    private val ctx = context.applicationContext
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val refreshes = MutableStateFlow(0L)

    /** Do Not Disturb access as last read here: null until the first read (a grant is a change this process saw). */
    @Volatile private var dndAccess: Boolean? = null

    /** The person asked to let Sukrara through and went to grant the access: the grant, when it comes, applies it. */
    @Volatile private var throughAsked = false

    // until the stored answer is read (a moment), no claim either way about it
    @Volatile private var last = Inputs(AlertSettings(), needsBluetooth = false, failedSteps = emptySet(), unlocked = UnlockedSound.Heard)

    override val readiness: StateFlow<AlertReadiness> by lazy {
        combine(
            combine(alertSettings, unlockedSound, ::Pair),
            needsBluetooth,
            failedSteps,
            refreshes,
            systemChanges(),
        ) { (settings, unlocked), bluetooth, failed, _, _ ->
            last = Inputs(settings, bluetooth, failed, unlocked)
            facts(last).readiness()
        }.flowOn(dispatchers.default)
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), facts(last).readiness())
    }

    override fun refresh() {
        refreshes.value++
    }

    override fun answerUnlockedSound(answer: UnlockedSound) {
        scope.launch { saveUnlockedSound(answer) }
    }

    override fun testUnlockedSound(): Boolean {
        val settings = last.settings
        val kind = unlockedTestKind(settings) ?: return false
        sendUnlockedTest(kind, settings)
        return true
    }

    /**
     * Let Sukrara through: without Do Not Disturb access, Android's access list (the grant, seen on the way back, gives
     * the alarm channels the override: [syncDndBypass]); with it, the override now. Don't let it through: the override
     * goes now (no access needed). Either way a channel whose sound Sukrara can't move opens its own page, where the
     * person flips *Override Do Not Disturb* themselves.
     */
    override fun letThroughDnd(through: Boolean) {
        if (through && !nm.isNotificationPolicyAccessGranted) {
            throughAsked = true
            start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) || start(appNotificationSettings())
            return
        }
        throughAsked = false
        scope.launch(dispatchers.default) {
            val kept = runCatching { AlertChannels.setDndBypass(ctx, through).kept }.getOrDefault(emptySet())
            refresh()
            relevantAlarmKinds(last.settings).firstOrNull { it in kept }?.let {
                start(channelSettings(AlertChannels.id(ctx, it, alarm = true)))
            }
        }
    }

    override fun fix(requirement: AlertRequirement) {
        val pkg = "package:${ctx.packageName}".toUri()
        val intent =
            when (requirement) {
                // its way out is Copy report (on the screens); here, the app's page (storage, battery, notifications)
                AlertRequirement.AlertShown, AlertRequirement.AlertDelivery -> {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
                }

                AlertRequirement.Notifications -> {
                    appNotificationSettings()
                }

                AlertRequirement.FullScreenIntent -> {
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
                }

                AlertRequirement.ExactAlarm -> {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
                }

                AlertRequirement.BatteryExemption -> {
                    if (ctx.getSystemService(ActivityManager::class.java).isBackgroundRestricted) {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
                    } else {
                        // REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is declared: a CGM's alerts are the accepted use case
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
                    }
                }

                // a choice, never a fault: the one button flips it
                AlertRequirement.DoNotDisturb -> {
                    letThroughDnd(!readiness.value.dnd.through)
                    return
                }

                // *Vibrate only when unlocked* is on Android's cooldown page (no public action: then the notifications page)
                AlertRequirement.SoundWhenUnlocked -> {
                    if (NotificationCooldown.PAGES.any(::start)) return
                    appNotificationSettings()
                }

                // a silenced channel: its page (importance, sound)
                AlertRequirement.AlertChannel -> {
                    channelsInUse(last.settings).firstOrNull { it.silenced() }?.let { channelSettings(it.id) } ?: appNotificationSettings()
                }

                AlertRequirement.Bluetooth -> {
                    if (bluetoothPermitted()) {
                        Intent(
                            Settings.ACTION_BLUETOOTH_SETTINGS,
                        )
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
                    }
                }
            }
        start(intent) || start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
    }

    private data class Inputs(
        val settings: AlertSettings,
        val needsBluetooth: Boolean,
        val failedSteps: Set<String>,
        val unlocked: UnlockedSound,
    )

    private fun facts(inputs: Inputs): ReadinessFacts {
        syncDndBypass()
        val channels = channelsInUse(inputs.settings)
        val alertGroupBlocked =
            listOf(Channels.Group.ALERTS, Channels.Group.ALERTS_ALARM).any { nm.getNotificationChannelGroup(it.id)?.isBlocked == true }
        val bluetooth = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        val dnd = AlertCategory.Dnd.of(nm)
        return ReadinessFacts(
            failedSteps = inputs.failedSteps,
            notificationsEnabled = nm.areNotificationsEnabled(),
            anyAlarmKind = alarmKinds(inputs.settings).isNotEmpty(),
            fullScreenAllowed = nm.canUseFullScreenIntent(),
            // always true while the manifest holds USE_EXACT_ALARM; kept so a future switch to SCHEDULE_EXACT_ALARM is covered
            exactAlarmAllowed = ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            batteryExempt = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName),
            backgroundRestricted = ctx.getSystemService(ActivityManager::class.java).isBackgroundRestricted,
            alertChannelSilenced = alertGroupBlocked || channels.any { it.silenced() },
            dnd =
                DndStatus(
                    through = DndHold.through(relevantAlarmKinds(inputs.settings).mapNotNull { alarmChannel(it)?.canBypassDnd() }),
                    heldNow = DndHold.held(channels.map { DndHold.Channel(Channels.isAlarm(it.id), it.canBypassDnd()) }, dnd),
                ),
            vibrateOnlyUnlocked = NotificationCooldown.vibrateOnlyWhenUnlocked(ctx),
            unlockedSound = inputs.unlocked,
            soundTest = soundTest(inputs.settings, dnd),
            needsBluetooth = inputs.needsBluetooth,
            bluetoothOn = bluetooth?.isEnabled == true,
            bluetoothPermitted = bluetoothPermitted(),
        )
    }

    /**
     * Android takes an app's `setBypassDnd` only while the app holds Do Not Disturb access (AOSP
     * PreferencesHelper.createNotificationChannel, frameworks/base main: an existing channel takes it only `if
     * (existing.getUserLockedFields() == 0 && hasDndAccess)`). So when the person grants the access *Let Sukrara through*
     * sent them to, and comes back to the app (a resume refresh, or the registered-only
     * ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED), the alarm channels take the override, keeping every choice
     * the person made ([AlertChannels.setDndBypass]). Nothing else ever changes it: re-creating the channels keeps the
     * override each one has, so a choice made in Android's own settings stays. An alarm channel moved away from goes
     * once its notification does. #15, #184.
     */
    private fun syncDndBypass() {
        val access = nm.isNotificationPolicyAccessGranted
        val granted = access && dndAccess == false
        dndAccess = access
        runCatching {
            if (granted && throughAsked) {
                throughAsked = false
                AlertChannels.setDndBypass(ctx, bypass = true)
            } else {
                Channels.dropOldAlarms(ctx)
            }
        }
    }

    /** The alarm channel [kind] posts on now, or null before the channels exist. */
    private fun alarmChannel(kind: AlertKind): NotificationChannel? = nm.getNotificationChannel(AlertChannels.id(ctx, kind, alarm = true))

    /**
     * The kinds whose alarm channels the Do Not Disturb choice is about: those that ring like an alarm, or every kind
     * while none does (the choice holds for when one does).
     */
    private fun relevantAlarmKinds(s: AlertSettings): List<AlertKind> = alarmKinds(s).ifEmpty { AlertKind.entries }

    /**
     * The kind the unlocked test sends: the first alert on whose channel sounds and vibrates (Android's *Vibrate only
     * when unlocked* drops the sound only where there is a vibration), alarm-level first ([NotificationCooldown.testKind]).
     */
    private fun unlockedTestKind(s: AlertSettings): AlertKind? {
        if (!s.on) return null
        val on = AlertKind.entries.filter { s.rule(it).on }
        return NotificationCooldown.testKind(on, { s.rule(it).alarm }) { kind ->
            nm.getNotificationChannel(AlertChannels.id(ctx, kind, s.rule(kind).alarm))?.let {
                it.sound != null && !it.silenced() && it.shouldVibrate()
            } ?: true
        }
    }

    /** Whether the unlocked test can show Android's behaviour now (no mode holding it back, the sound up). */
    private fun soundTest(
        s: AlertSettings,
        dnd: AlertCategory.Dnd?,
    ): SoundTest {
        val kind = unlockedTestKind(s) ?: return SoundTest.NoAlert
        if (dnd != null && dnd.filter != NotificationManager.INTERRUPTION_FILTER_ALL) return SoundTest.ModeOn
        val audio = ctx.getSystemService(AudioManager::class.java)
        val stream = if (s.rule(kind).alarm) AudioManager.STREAM_ALARM else AudioManager.STREAM_NOTIFICATION
        return if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL || audio.getStreamVolume(stream) == 0) {
            SoundTest.Silent
        } else {
            SoundTest.Ready
        }
    }

    private fun alarmKinds(s: AlertSettings) = if (s.on) AlertKind.entries.filter { s.rule(it).on && s.rule(it).alarm } else emptyList()

    /** The channel each enabled kind posts on (a channel not created yet gets our defaults: nothing to warn about). */
    private fun channelsInUse(s: AlertSettings): List<NotificationChannel> =
        if (!s.on) {
            emptyList()
        } else {
            val alarmIds = AlertChannels.alarmIds(ctx)
            AlertKind.entries.filter { s.rule(it).on }.mapNotNull { kind ->
                nm.getNotificationChannel(if (s.rule(kind).alarm) alarmIds.getValue(kind) else Channels.alert(kind.wire))
            }
        }

    private fun NotificationChannel.silenced() = importance < NotificationManager.IMPORTANCE_DEFAULT

    /** Nearby devices: connecting and scanning (the families found by a scan need it; the new-sensor flow asks for both). */
    private fun bluetoothPermitted() =
        listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN).all {
            ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

    /** One emission at start and one per broadcast that can flip an item while the app stays open. */
    private fun systemChanges(): Flow<Unit> =
        callbackFlow {
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        trySend(Unit)
                    }
                }
            val filter =
                IntentFilter().apply {
                    addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                    addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
                    addAction(NotificationManager.ACTION_NOTIFICATION_POLICY_CHANGED)
                    addAction(NotificationManager.ACTION_CONSOLIDATED_NOTIFICATION_POLICY_CHANGED)
                    addAction(NotificationManager.ACTION_APP_BLOCK_STATE_CHANGED)
                    addAction(NotificationManager.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED)
                    addAction(NotificationManager.ACTION_NOTIFICATION_CHANNEL_GROUP_BLOCK_STATE_CHANGED)
                    addAction(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
                    // the unlocked test's state (vibrate / silent)
                    addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
                    // sent to registered receivers only (NotificationManagerService, FLAG_RECEIVER_REGISTERED_ONLY)
                    addAction(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED)
                }
            // system broadcasts reach a not-exported receiver
            ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            awaitClose { ctx.unregisterReceiver(receiver) }
        }.onStart { emit(Unit) }

    private fun appNotificationSettings() =
        Intent(
            Settings.ACTION_APP_NOTIFICATION_SETTINGS,
        ).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)

    private fun channelSettings(id: String) =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, id)

    private fun start(intent: Intent): Boolean =
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            Log.w("Sukrara.readiness", "no screen for ${intent.action}: ${e.javaClass.simpleName}")
            false
        }

    internal companion object {
        const val STOP_AFTER_MS = 5_000L
    }
}
