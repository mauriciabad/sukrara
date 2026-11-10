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
 * Room with KSP. The current database version's schema JSON is exported to `schemas/` and committed (a version change
 * replaces it: no migrations, a database of another version is replaced by an empty one); the unit tests
 * (Robolectric) read it as an asset to create a current-version database with `MigrationTestHelper` (room-testing).
 */
class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("androidx.room")
        extensions.configure<RoomExtension> { schemaDirectory("$projectDir/schemas") }
        // MigrationTestHelper.createDatabase loads `<database class>/<version>.json` from the test's assets
        extensions.configure<CommonExtension> { sourceSets.getByName("test").assets.srcDir("$projectDir/schemas") }
        dependencies.add("implementation", libs.lib("androidx-room-runtime"))
        dependencies.add("ksp", libs.lib("androidx-room-compiler"))
        dependencies.add("testImplementation", libs.lib("androidx-room-testing"))
    }
}
