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
import app.mauri.sukrara.core.model.UnlockedSound
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
 * alert channels in use not blocked or silenced, and Do Not Disturb not silencing the alarm-level ones (a non-alarm
 * alert in Do Not Disturb is the person's choice: "Like an alarm" is the way through); the alarm-level channels
 * overriding Do Not Disturb (access granted; [syncDndBypass] applies it); Bluetooth on and allowed when
 * [needsBluetooth].
 */
class AndroidAlertReadiness(
    context: Context,
    private val scope: CoroutineScope,
    dispatchers: DispatcherProvider,
    private val alertSettings: Flow<AlertSettings>,
    private val needsBluetooth: Flow<Boolean>,
    private val failedSteps: Flow<Set<String>>,
    /** The person's answer about *Vibrate only when unlocked* (schema `alerts.unlockedSound`) and how to store it. */
    private val unlockedSound: Flow<UnlockedSound>,
    private val saveUnlockedSound: suspend (UnlockedSound) -> Unit,
) : AlertReadinessSource {
    private val ctx = context.applicationContext
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val refreshes = MutableStateFlow(0L)

    // until the stored answer is read (a moment), no claim either way about it
    @Volatile private var last = Inputs(AlertSettings(), needsBluetooth = false, failedSteps = emptySet(), unlocked = UnlockedSound.Off)

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

    override fun fix(requirement: AlertRequirement) {
        val pkg = "package:${ctx.packageName}".toUri()
        val intent =
            when (requirement) {
                // its way out is Copy report (on the screens); here, the app's page (storage, battery, notifications)
                AlertRequirement.AlertDelivery -> {
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

                // no access yet: Android's Do Not Disturb access list (the per-app page is a system API); with access, a
                // channel still not overriding is one the person edited (Android keeps their choice): its own page
                AlertRequirement.AlarmsInDnd -> {
                    if (!nm.isNotificationPolicyAccessGranted) {
                        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    } else {
                        alarmKinds(last.settings)
                            .mapNotNull { nm.getNotificationChannel(AlertChannels.id(it, alarm = true)) }
                            .firstOrNull { !it.canBypassDnd() }
                            ?.let { channelSettings(it.id) } ?: appNotificationSettings()
                    }
                }

                // *Vibrate only when unlocked* is on Android's cooldown page (no public action: then the notifications page)
                AlertRequirement.SoundWhenUnlocked -> {
                    if (NotificationCooldown.PAGES.any(::start)) return
                    appNotificationSettings()
                }

                // the channel page holds both the importance and "Override Do Not Disturb"
                AlertRequirement.AlertChannel -> {
                    channelsInUse(last.settings).firstOrNull { it.silenced() || it.blockedByDnd() }?.let { channelSettings(it.id) }
                        ?: appNotificationSettings()
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
            dndSilencesAlarms = channels.any { it.blockedByDnd() },
            alarmsBypassDnd = alarmsBypassDnd(inputs.settings),
            vibrateOnlyUnlocked = NotificationCooldown.vibrateOnlyWhenUnlocked(ctx),
            unlockedSound = inputs.unlocked,
            needsBluetooth = inputs.needsBluetooth,
            bluetoothOn = bluetooth?.isEnabled == true,
            bluetoothPermitted = bluetoothPermitted(),
        )
    }

    /**
     * `setBypassDnd(true)` only sticks when the app holds Do Not Disturb access as the channel is created or updated
     * (AOSP PreferencesHelper.createNotificationChannel, frameworks/base main: an existing channel takes the app's
     * bypass only `if (existing.getUserLockedFields() == 0 && hasDndAccess)`; NotificationManagerService passes
     * `mConditionProviders.isPackageOrComponentAllowed(pkg)`). So once the person grants access (they come back to the
     * app: a resume refresh, or the registered-only ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED), the channels are
     * created again: the alarm ones take the bypass, and Android keeps every choice the person made (a channel they
     * edited keeps its own override setting; ids never change). #15.
     */
    private fun syncDndBypass() {
        val bypass = ALARM_CHANNELS.mapNotNull { nm.getNotificationChannel(it)?.canBypassDnd() }
        if (needsDndSync(nm.isNotificationPolicyAccessGranted, bypass)) runCatching { AlertChannels.ensure(ctx) }
    }

    /** Every alarm-level channel in use overrides Do Not Disturb (one not created yet will, exactly when access is granted). */
    private fun alarmsBypassDnd(s: AlertSettings): Boolean {
        val access = nm.isNotificationPolicyAccessGranted
        return alarmKinds(s).all { nm.getNotificationChannel(AlertChannels.id(it, alarm = true))?.canBypassDnd() ?: access }
    }

    private fun alarmKinds(s: AlertSettings) = if (s.on) AlertKind.entries.filter { s.rule(it).on && s.rule(it).alarm } else emptyList()

    /** The channel each enabled kind posts on (a channel not created yet gets our defaults: nothing to warn about). */
    private fun channelsInUse(s: AlertSettings): List<NotificationChannel> =
        if (!s.on) {
            emptyList()
        } else {
            AlertKind.entries.filter { s.rule(it).on }.mapNotNull { nm.getNotificationChannel(AlertChannels.id(it, s.rule(it).alarm)) }
        }

    private fun NotificationChannel.silenced() = importance < NotificationManager.IMPORTANCE_DEFAULT

    private fun NotificationChannel.blockedByDnd(): Boolean {
        if (id !in ALARM_CHANNELS) return false
        return when (nm.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_NONE -> {
                true
            }

            // the policy of the modes active now (Bedtime, Driving…), not only the default Do Not Disturb
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> {
                !canBypassDnd() &&
                    nm.consolidatedNotificationPolicy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS == 0
            }

            else -> {
                false
            }
        }
    }

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

        /** The channels are created again when access is granted and an alarm channel doesn't override yet. */
        fun needsDndSync(
            accessGranted: Boolean,
            alarmChannelsBypass: List<Boolean>,
        ) = accessGranted && alarmChannelsBypass.any { !it }

        val ALARM_CHANNELS: Set<String> = AlertKind.entries.map { AlertChannels.id(it, alarm = true) }.toSet()
    }
}
