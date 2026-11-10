// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.lockedboot

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.monitor.notify.Channels
import app.mauri.sukrara.monitor.notify.NotificationIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * The storage split and the boot path on Android's classes: the plan lives in device-protected storage, the receiver
 * reads only it, posts the lock-screen notice and the alarm before the unlock, and clears both at the boot after it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LockedBootReceiverTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val am = ctx.getSystemService(AlarmManager::class.java)
    private val store = LockedBootStore.of(ctx)
    private val now = System.currentTimeMillis() / 1000

    @Before
    fun locked() {
        Channels.create(ctx)
        shadowOf(ctx.getSystemService(UserManager::class.java)).setUserUnlocked(false)
        shadowOf(ctx.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
    }

    private fun send(action: String) = LockedBootReceiver().onReceive(ctx, Intent(action))

    private fun shown(): Notification? = shadowOf(nm).getNotification(NotificationIds.LOCKED_BOOT)

    @Test
    fun `the plan is in device-protected storage, apart from the app's other files`() {
        val de = ctx.createDeviceProtectedStorageContext().filesDir
        store.write(LockedBootPlan(monitoring = true))
        assertTrue(File(de, LockedBootStore.FILE).exists())
        assertNotEquals("not credential-encrypted storage", ctx.filesDir.canonicalPath, de.canonicalPath)
        assertFalse(File(ctx.filesDir, LockedBootStore.FILE).exists())
    }

    @Test
    fun `the store reads back what it wrote, nothing when missing, and a damaged file as monitoring with no alarm`() {
        assertNull(store.read())
        val plan = LockedBootPlan(true, now - 60, 20, 60, alarm = true, sound = "soft")
        store.write(plan)
        assertEquals(plan, LockedBootStore.of(ctx).read())
        File(ctx.createDeviceProtectedStorageContext().filesDir, LockedBootStore.FILE).writeText("{not json")
        assertEquals(LockedBootPlan.UNREADABLE, store.read())
    }

    @Test
    fun `an unchanged plan isn't written again`() {
        val file = File(ctx.createDeviceProtectedStorageContext().filesDir, LockedBootStore.FILE)
        store.write(LockedBootPlan(monitoring = true))
        file.delete()
        store.write(LockedBootPlan(monitoring = true))
        assertFalse(file.exists())
    }

    @Test
    fun `locked boot posts the silent lock-screen notice and arms the alarm, the alarm rings as no data, the unlock clears`() {
        store.write(LockedBootPlan(true, now - 60, 20, 60, alarm = true))
        send(Intent.ACTION_LOCKED_BOOT_COMPLETED)
        val waiting = requireNotNull(shown())
        assertEquals(Channels.STATUS, waiting.channelId)
        assertEquals(Notification.VISIBILITY_PUBLIC, waiting.visibility)
        assertEquals(Notification.GROUP_ALERT_SUMMARY, waiting.groupAlertBehavior)
        assertNotNull(waiting.contentIntent)
        assertNotNull(shadowOf(am).nextScheduledAlarm)

        send(LockedBootReceiver.ACTION_RING)
        assertEquals("alert_alarm.no_data", shown()!!.channelId)
        assertNotNull("rings again after the re-alert interval", shadowOf(am).nextScheduledAlarm)

        shadowOf(ctx.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        send(Intent.ACTION_BOOT_COMPLETED)
        assertNull(shown())
        assertNull(shadowOf(am).nextScheduledAlarm)
    }

    @Test
    fun `no data at notification volume when no alert the person has on is an alarm`() {
        store.write(LockedBootPlan(true, now - 3600, 20, 60, alarm = false))
        send(Intent.ACTION_LOCKED_BOOT_COMPLETED)
        assertEquals("alert.no_data", shown()!!.channelId)
    }

    @Test
    fun `nothing shows when nothing was set up to monitor or the phone has no screen lock`() {
        send(Intent.ACTION_LOCKED_BOOT_COMPLETED)
        assertNull(shown())
        store.write(LockedBootPlan(true, now - 60, 20, 60))
        shadowOf(ctx.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(false)
        send(Intent.ACTION_LOCKED_BOOT_COMPLETED)
        assertNull(shown())
        assertNull(shadowOf(am).nextScheduledAlarm)
    }
}
