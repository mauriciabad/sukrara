// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

// Standalone investigation build (issue #68, docs/31-db-encryption.md). Not part of apps/android; run it with that
// project's wrapper: apps/android/gradlew -p investigations/68-db-encryption assembleDebug assembleDebugAndroidTest
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "inv68-db-encryption"
