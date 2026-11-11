// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The merged manifest's security and reliability rules (all modules' components together). */
class ManifestTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private val root: Element =
        DocumentBuilderFactory
            .newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File(System.getProperty("sukrara.mergedManifest") ?: error("sukrara.mergedManifest not set")))
            .documentElement
    private val components =
        listOf("activity", "activity-alias", "service", "receiver", "provider")
            .flatMap { tag -> root.getElementsByTagName(tag).let { list -> (0 until list.length).map { list.item(it) as Element } } }

    private fun Element.attr(name: String) = getAttributeNS(android, name)

    private fun named(cls: String) = components.single { it.attr("name") == cls }

    /** Exported without a permission on purpose; each must say why in its manifest. */
    private val openOnPurpose =
        setOf(
            "app.mauri.sukrara.MainActivity", // launcher and invite links
            "app.mauri.sukrara.monitor.AlertSoundProvider", // System UI reads the channel sounds
            // LSPosed hands the app its service here (advisory: it only picks the root page's hint)
            "io.github.libxposed.service.XposedProvider",
            "androidx.compose.ui.tooling.PreviewActivity", // ui-tooling, debugImplementation only (Studio previews)
            "androidx.activity.ComponentActivity", // ui-test-manifest, debugImplementation only (:feature:home Compose tests)
            // home-screen widgets: any launcher sends their broadcasts and opens their settings
            "app.mauri.sukrara.widgets.ValueWidgetReceiver",
            "app.mauri.sukrara.widgets.GraphWidgetReceiver",
            "app.mauri.sukrara.widgets.CombinedWidgetReceiver",
            "app.mauri.sukrara.widgets.WidgetConfigActivity",
            // the widgets' size probe: the launcher loads its images (a nonce in progress only, no data; WidgetProbe)
            "app.mauri.sukrara.widgets.ProbeProvider",
            // the lock-screen widget: the system's widget broadcasts (System UI's module hosts it; exported as in the PoC,
            // where it was checked on the phone); they only redraw it
            "app.mauri.sukrara.widgets.LockWidgetReceiver",
        )

    @Test
    fun `every exported component needs a permission unless it is on the list`() {
        val open = components.filter { it.attr("exported") == "true" && it.attr("permission").isEmpty() }.map { it.attr("name") }
        assertEquals(openOnPurpose, open.toSet())
    }

    @Test
    fun `only Sukrara's own intents open the new-sensor flow`() {
        assertEquals("false", named("app.mauri.sukrara.SensorSetupActivity").attr("exported"))
        val actions = root.getElementsByTagName("action").let { list -> (0 until list.length).map { list.item(it) as Element } }
        assertTrue(
            "no intent filter declares the new-sensor action",
            actions.none { it.attr("name") == app.mauri.sukrara.core.model.NavTargets.ACTION_SENSOR_SETUP },
        )
    }

    @Test
    fun `only System UI can say hello to the root status bar`() {
        val hello = named("app.mauri.sukrara.monitor.systemui.HookHelloReceiver")
        assertEquals("android.permission.STATUS_BAR_SERVICE", hello.attr("permission"))
    }

    @Test
    fun `the sound provider only serves files and file provider stays private`() {
        val sounds = named("app.mauri.sukrara.monitor.AlertSoundProvider")
        assertEquals("false", sounds.attr("grantUriPermissions"))
        assertEquals("false", named("androidx.core.content.FileProvider").attr("exported"))
    }

    @Test
    fun `monitoring and alert components are enabled so a reboot or update reaches them`() {
        listOf("MonitoringService", "BootReceiver", "AlertCheckReceiver", "AlertActionReceiver", "HealthCheckReceiver").forEach {
            assertFalse("$it is disabled", named("app.mauri.sukrara.monitor.$it").attr("enabled") == "false")
        }
    }

    /**
     * Before the first unlock after a restart only these run (docs/30-direct-boot.md, #160): the locked boot, the sound
     * provider and the wearer's monitoring (service, its alarms and the alert buttons), all on device-protected storage
     * only. Any other component there (an activity, a widget, sharing) would start code that expects the unlocked
     * storage.
     */
    @Test
    fun `only the locked boot, the sounds and the monitoring run before the first unlock`() {
        val aware = components.filter { it.attr("directBootAware") == "true" }.map { it.attr("name") }.toSet()
        assertEquals(
            setOf(
                "app.mauri.sukrara.monitor.lockedboot.LockedBootReceiver",
                "app.mauri.sukrara.monitor.AlertSoundProvider",
                "app.mauri.sukrara.monitor.MonitoringService",
                "app.mauri.sukrara.monitor.HealthCheckReceiver",
                "app.mauri.sukrara.monitor.AlertCheckReceiver",
                "app.mauri.sukrara.monitor.AlertActionReceiver",
                // Room's own, bound only with multi-instance invalidation (Sukrara doesn't enable it): never runs
                "androidx.room.MultiInstanceInvalidationService",
            ),
            aware,
        )
        val receiver = named("app.mauri.sukrara.monitor.lockedboot.LockedBootReceiver")
        assertEquals("false", receiver.attr("exported"))
        val actions = receiver.getElementsByTagName("action").let { l -> (0 until l.length).map { (l.item(it) as Element).attr("name") } }
        assertEquals(setOf("android.intent.action.LOCKED_BOOT_COMPLETED", "android.intent.action.BOOT_COMPLETED"), actions.toSet())
    }

    @Test
    fun `no backup and no device transfer`() {
        val app = root.getElementsByTagName("application").item(0) as Element
        assertEquals("false", app.attr("allowBackup"))
        assertEquals("@xml/data_extraction_rules", app.attr("dataExtractionRules"))
    }

    private fun elements(tag: String) =
        root
            .getElementsByTagName(
                tag,
            ).let { list -> (0 until list.length).map { list.item(it) as Element } }

    private val allowJson by lazy { Json.parseToJsonElement(File("../../../tools/check/allow.json").readText()).jsonObject }

    /** tools/check/allow.json's `permissions` / `queries` keys: the one list of what the app may declare, with why. */
    private fun allowed(key: String): Set<String> =
        allowJson
            .getValue(key)
            .jsonObject.keys
            .toSortedSet()

    /** #36: only what the shipped features use; a library or module that adds one fails here until the list says why. */
    @Test
    fun `the permissions are exactly allow-json's`() {
        assertEquals(allowed("permissions"), elements("uses-permission").map { it.attr("name") }.toSortedSet())
        // Bluetooth finds the sensor and phones nearby, never the person's location
        val scan = elements("uses-permission").single { it.attr("name") == "android.permission.BLUETOOTH_SCAN" }
        assertEquals("neverForLocation", scan.attr("usesPermissionFlags"))
    }

    /**
     * The Google Play build's merged manifest (play flavor, app/src/play). The owner's decision of 2026-11-11 (#142): it
     * keeps `USE_EXACT_ALARM` (Play's exact-alarm declaration) and the root features; only the update check is left
     * out, which is code, not manifest (verify-apk.sh --play).
     */
    private val play: Element by lazy {
        DocumentBuilderFactory
            .newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File(System.getProperty("sukrara.playManifest") ?: error("sukrara.playManifest not set")))
            .documentElement
    }

    private fun Element.names(tag: String) =
        getElementsByTagName(tag).let { l ->
            (0 until l.length).map {
                (l.item(it) as Element).attr("name")
            }
        }

    @Test
    fun `the Play build's permissions are allow-json's with play_permissions applied`() {
        val change = allowJson.getValue("play_permissions").jsonObject
        val remove = change.getValue("remove").jsonObject.keys
        val add = change.getValue("add").jsonObject.keys
        val permissions = play.names("uses-permission").toSortedSet()
        assertEquals((allowed("permissions") - remove + add).toSortedSet(), permissions)
        // the alert check's alarms ring on time from install: no SCHEDULE_EXACT_ALARM the person must grant first
        assertTrue("android.permission.USE_EXACT_ALARM" in permissions)
        assertFalse("android.permission.SCHEDULE_EXACT_ALARM" in permissions)
    }

    @Test
    fun `the Play build has the root features, the LSPosed module and the System UI hello`() {
        val components = listOf("service", "provider", "receiver").flatMap { play.names(it) }
        val wanted =
            listOf(
                "io.github.libxposed.service.XposedProvider",
                "app.mauri.sukrara.monitor.systemui.HookHelloReceiver",
                // the rest of the app: the monitor, the alert sounds
                "app.mauri.sukrara.monitor.MonitoringService",
                "app.mauri.sukrara.monitor.AlertSoundProvider",
            )
        for (c in wanted) assertTrue("$c missing from the Play build", c in components)
        assertTrue(play.names("permission").any { it.endsWith(".permission.SYSTEMUI_STATE") })
    }

    @Test
    fun `the Play build has the same components as the GitHub build`() {
        val tags = listOf("activity", "activity-alias", "service", "receiver", "provider")
        // the debug-only components are in both debug manifests alike, so the sets compare whole
        assertEquals(tags.flatMap { root.names(it) }.toSortedSet(), tags.flatMap { play.names(it) }.toSortedSet())
    }

    @Test
    fun `the queries are exactly allow-json's`() {
        val queries =
            elements("queries").flatMap { q ->
                (0 until q.childNodes.length).mapNotNull { q.childNodes.item(it) as? Element }.map { e ->
                    when (e.tagName) {
                        "package" -> {
                            "package:" + e.attr("name")
                        }

                        "intent" -> {
                            "intent:" +
                                (0 until e.childNodes.length)
                                    .mapNotNull { e.childNodes.item(it) as? Element }
                                    .joinToString(",") { it.attr("name") }
                        }

                        else -> {
                            e.tagName
                        }
                    }
                }
            }
        assertEquals(allowed("queries"), queries.toSortedSet())
    }

    // The release merged manifest isn't built in `check` (it needs the release key); no main-source manifest naming the
    // receiver is the same guarantee, and tools/release/verify-apk.sh checks the release APK itself.
    @Test
    fun `the debug receiver exists only in the debug source set, behind DUMP`() {
        assertEquals("android.permission.DUMP", named("app.mauri.sukrara.debug.DebugReceiver").attr("permission"))
        val modules = File("..").canonicalFile
        val mainManifests =
            modules
                .walk()
                .filter { it.name == "AndroidManifest.xml" && "/build/" !in it.path && "/src/debug/" !in it.path }
                .filter { "DebugReceiver" in it.readText() }
                .toList()
        assertEquals(emptyList<File>(), mainManifests)
    }
}
