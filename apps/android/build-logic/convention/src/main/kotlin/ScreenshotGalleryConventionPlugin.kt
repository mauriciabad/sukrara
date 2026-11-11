// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import app.mauri.sukrara.buildlogic.lib
import app.mauri.sukrara.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

/**
 * A Compose module's screenshot gallery (apply after `sukrara.android.compose`): every unit test run records its
 * Roborazzi screenshots into `build/outputs/roborazzi` (synthetic data, for the UX review). Nothing is committed or
 * compared: images live only under docs/img or evidence folders. Committed goldens are `sukrara.screenshots.goldens`.
 */
class ScreenshotGalleryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        screenshotDependencies()
        dependencies.add("testImplementation", libs.lib("roborazzi-compose"))
        dependencies.add("testImplementation", libs.lib("androidx-compose-ui-test-junit4"))
        dependencies.add("debugImplementation", libs.lib("androidx-compose-ui-test-manifest"))
        tasks.withType<Test>().configureEach { systemProperty("roborazzi.test.record", "true") }
    }
}

/** Roborazzi's capture API on the unit tests' classpath (the module's Robolectric tests draw, Roborazzi saves). */
internal fun Project.screenshotDependencies() {
    dependencies.add("testImplementation", libs.lib("roborazzi"))
}
