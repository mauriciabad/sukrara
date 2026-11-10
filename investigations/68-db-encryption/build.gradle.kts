// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

// The same AGP / Kotlin / KSP / Room versions and SDK levels as apps/android (gradle/libs.versions.toml), plus
// SQLCipher for Android. Everything measured lives in src/androidTest (DbEncryptionBench).
plugins {
    id("com.android.application") version "9.1.1"
    id("org.jetbrains.kotlin.jvm") version "2.3.20" apply false
    id("com.google.devtools.ksp") version "2.3.12"
}

android {
    namespace = "app.mauri.sukrara.inv68"
    compileSdk = 37
    defaultConfig {
        applicationId = "app.mauri.sukrara.inv68"
        minSdk = 36
        targetSdk = 37
        versionCode = 1
        versionName = "1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Sukrara ships arm64-v8a only (build-logic AndroidApplicationConventionPlugin)
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation("androidx.room:room-runtime:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("net.zetetic:sqlcipher-android:4.20.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("junit:junit:4.13.2")
}
