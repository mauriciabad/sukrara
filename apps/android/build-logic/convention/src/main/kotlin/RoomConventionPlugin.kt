// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import androidx.room.gradle.RoomExtension
import app.mauri.sukrara.buildlogic.lib
import app.mauri.sukrara.buildlogic.libs
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Room with KSP. The schema JSON of every database version is exported to `schemas/` and committed, so migrations
 * can be tested with `MigrationTestHelper` (room-testing): the unit tests (Robolectric) read them as assets.
 */
class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("androidx.room")
        extensions.configure<RoomExtension> { schemaDirectory("$projectDir/schemas") }
        // MigrationTestHelper loads `<database class>/<version>.json` from the test's assets
        extensions.configure<CommonExtension> { sourceSets.getByName("test").assets.srcDir("$projectDir/schemas") }
        dependencies.add("implementation", libs.lib("androidx-room-runtime"))
        dependencies.add("ksp", libs.lib("androidx-room-compiler"))
        dependencies.add("testImplementation", libs.lib("androidx-room-testing"))
    }
}
