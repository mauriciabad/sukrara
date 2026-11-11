// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Screenshot goldens committed in `src/test/snapshots`: the Roborazzi Gradle plugin's tasks, recorded on CI only
 * (`.github/workflows/goldens.yml`: Robolectric draws text a little differently on macOS and Linux) and compared by
 * `verifyRoborazziDebug` in CI's checks; locally `compareRoborazziDebug` shows the diff. A plain test run neither
 * writes nor compares. A module's own Compose test dependencies stay in its build file.
 */
class ScreenshotGoldensConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("io.github.takahirom.roborazzi")
        screenshotDependencies()
    }
}
