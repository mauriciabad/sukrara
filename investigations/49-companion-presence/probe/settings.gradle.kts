// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
// CdmProbe: a standalone app for investigation 49 (CompanionDeviceManager presence). Built with Sukrara's Gradle
// wrapper: apps/android/gradlew -p investigations/49-companion-presence/probe assembleDebug
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "cdmprobe"
