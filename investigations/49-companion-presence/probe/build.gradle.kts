// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
// No libraries: plain framework Activity / Service / Receiver, so it builds in seconds and offline.
plugins {
    id("com.android.application") version "9.1.1"
}

android {
    namespace = "dev.spike.cdmprobe"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.spike.cdmprobe"
        minSdk = 36
        targetSdk = 37
        versionCode = 1
        versionName = "1"
    }
    // full: every companion permission (what Sukrara would declare); fgsOnly: without RUN_IN_BACKGROUND /
    // USE_DATA_IN_BACKGROUND, to see whether CDM then leaves the person's own battery exemption alone
    flavorDimensions += "perms"
    productFlavors {
        create("full") { dimension = "perms" }
        create("fgsOnly") { dimension = "perms" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}
