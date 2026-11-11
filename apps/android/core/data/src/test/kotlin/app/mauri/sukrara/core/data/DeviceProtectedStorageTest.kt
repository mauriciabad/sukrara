// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.data.secure.SecretName
import app.mauri.sukrara.core.data.settings.SchemaSettings
import app.mauri.sukrara.core.model.DefaultDispatchers
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.testing.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * The storage split of #160 (docs/30-direct-boot.md): what the wearer's monitoring needs before the first unlock after
 * a restart is in device-protected storage, everything else credential-encrypted and refused until the unlock (never
 * read as missing); the readings taken before the unlock reach the history at the unlock, with no gap and nothing twice.
 */
@RunWith(RobolectricTestRunner::class)
class DeviceProtectedStorageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock()
    private val de = context.createDeviceProtectedStorageContext().filesDir
    private val ce = context.filesDir
    private var unlocked = true
    private val opened = mutableListOf<DataModule>()

    private fun data() =
        DataModule(context, "0.12.0-test", DefaultDispatchers, clock, secretBox = SoftwareSecretBox(), userUnlocked = { unlocked })
            .also { opened += it }

    @After
    fun close() = opened.forEach { d -> runCatching { d.database.close() } }

    private val alerts = SchemaSettings.text("alerts.on", default = "")
    private val unit = SchemaSettings.text("general.unit", "mgdl", setOf("mgdl", "mmol"))
    private val healthConnect = SchemaSettings.bool("healthConnect.on", default = false)
    private val settingsFrom = SchemaSettings.text("follower.settingsFrom", "wearer", setOf("wearer", "default", "custom"))

    private fun reading(
        minutesAgo: Long,
        mgdl: Int = 100,
    ) = Reading(clock.now() - Duration.ofMinutes(minutesAgo), mgdl, null, SourceId.Libre3)

    @Test
    fun `what monitoring needs is device-protected, the rest credential-encrypted`() =
        runBlocking {
            assertNotEquals(ce.canonicalPath, de.canonicalPath)
            val data = data()
            data.settings.set(alerts, "{}")
            data.settings.set(unit, "mmol")
            data.settings.set(healthConnect, true)
            data.settings.set(settingsFrom, "custom")
            data.secureStore.write(SecretName.SENSOR, byteArrayOf(1))
            data.secureStore.write(SecretName.IDENTITY_OWNER, byteArrayOf(2))
            data.typedStore("alert_state", Int.serializer(), 0, 0).update { 1 }
            data.typedStore("role", Int.serializer(), 0, 0).update { 1 }
            data.typedStore("setup", Int.serializer(), 0, 0).update { 1 }

            fun inDe(path: String) = File(de.parentFile, path).exists() && !File(ce.parentFile, path).exists()

            fun inCe(path: String) = File(ce.parentFile, path).exists() && !File(de.parentFile, path).exists()
            assertTrue(inDe("datastore/alerts.preferences_pb"))
            assertTrue(inDe("datastore/settings.preferences_pb"))
            assertTrue(inCe("datastore/integrations.preferences_pb"))
            assertTrue("the sharing settings stay with the identity", inCe("datastore/sharing.preferences_pb"))
            assertTrue(inDe("files/secure/sensor.bin"))
            assertTrue("the sharing identity stays credential-encrypted", inCe("files/secure/identity.owner.bin"))
            assertTrue(inDe("files/state/alert_state.json"))
            assertTrue(inDe("files/state/role.json"))
            assertTrue(inCe("files/state/setup.json"))
        }

    @Test
    fun `before the first unlock the device-protected stores work and the others refuse, never read as missing`() =
        runBlocking {
            // one module (DataStore allows one instance per file in a process): the lock is read at every use
            val data = data()
            val record = data.typedStore("monitor-record", Int.serializer(), 0, 0)
            data.settings.set(alerts, "{\"on\":true}")
            data.secureStore.write(SecretName.SENSOR, byteArrayOf(7))
            record.update { 5 }

            unlocked = false
            assertEquals("{\"on\":true}", data.settings.flow(alerts).first())
            assertEquals(7.toByte(), data.secureStore.read(SecretName.SENSOR)!![0])
            assertEquals(5, record.data.first())
            record.update { 6 }

            refused { data.settings.flow(healthConnect).first() }
            refused { data.settings.set(healthConnect, true) }
            refused { data.secureStore.read(SecretName.IDENTITY_OWNER) }
            refused { data.secureStore.write(SecretName.IDENTITY_OWNER, byteArrayOf(1)) }
            refused { data.typedStore("setup", Int.serializer(), 0, 0).data.first() }
            refused { data.database }
            refused { data.unlock() }
        }

    private suspend fun refused(block: suspend () -> Any?) {
        try {
            block()
            fail("credential-encrypted storage was used before the unlock")
        } catch (_: LockedStorageException) {
        }
    }

    @Test
    fun `the diagnostic log waits for the unlock and keeps the order`() =
        runBlocking {
            unlocked = false
            val data = data()
            data.diagLog.log("test", "before")
            assertTrue(data.diagLog.snapshot().contains("before"))
            assertFalse(File(ce, "diag/diag.log").exists())
            unlocked = true
            data.diagLog.log("test", "after")
            data.diagLog.snapshot()
            val written = File(ce, "diag/diag.log").readText()
            assertTrue(written.indexOf("before") in 0 until written.indexOf("after"))
        }

    @Test
    fun `readings taken before the unlock show on the hub and join the history at the unlock, no gap and nothing twice`() =
        runBlocking {
            // before the restart: unlocked, readings in the history and the last hours kept device-protected
            val before = data()
            val hub = before.hubReadings(flowOf(Role.Wearer))
            hub.upsert(listOf(reading(30), reading(29), reading(28)))
            assertEquals(3, before.readings.range(Instant.EPOCH, clock.now()).size)
            before.database.close()

            // the restart, before the first unlock
            unlocked = false
            val locked = data()
            val lockedHub = locked.hubReadings(flowOf(Role.Wearer))
            assertFalse(locked.unlocked.value)
            assertEquals("the hours before the restart", 3, lockedHub.window(Instant.EPOCH).first().size)
            lockedHub.upsert(listOf(reading(27, 90)))
            lockedHub.upsert(listOf(reading(28), reading(26, 80)))
            assertEquals(listOf(100, 100, 100, 90, 80), lockedHub.window(Instant.EPOCH).first().map { it.mgdl })
            assertEquals(80, lockedHub.latest().first()!!.mgdl)

            // the unlock
            unlocked = true
            locked.unlock()
            assertTrue(locked.unlocked.value)
            val history = locked.readings.range(Instant.EPOCH, clock.now())
            assertEquals(listOf(30L, 29, 28, 27, 26), history.map { Duration.between(it.t, clock.now()).toMinutes() })
            assertEquals("the hub reads the history now", history, lockedHub.window(Instant.EPOCH).first())
            locked.unlock()
            lockedHub.upsert(listOf(reading(26, 80), reading(25, 75)))
            assertEquals(6, locked.readings.range(Instant.EPOCH, clock.now()).size)
            assertFalse("nothing waits for a merge", File(de, DeviceProtected.LOCKED_READINGS).exists())
        }
}
