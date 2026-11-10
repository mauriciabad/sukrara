// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

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
     * Before the first unlock after a restart only these run (docs/30-direct-boot.md): they read the device-protected
     * plan and never the app graph. Any other component there would start code that expects the unlocked storage.
     */
    @Test
    fun `only the locked boot receiver and the sound provider run before the first unlock`() {
        val aware = components.filter { it.attr("directBootAware") == "true" }.map { it.attr("name") }.toSet()
        assertEquals(
            setOf(
                "app.mauri.sukrara.monitor.lockedboot.LockedBootReceiver",
                "app.mauri.sukrara.monitor.AlertSoundProvider",
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
