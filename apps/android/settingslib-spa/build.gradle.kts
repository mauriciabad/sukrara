/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Adapted for Sukrara (NOTICE): AGP's built-in Kotlin instead of kotlin-android, the app's version catalog (Compose
 * BOM, material3), its SDK levels and JDK 21, no chart / Lottie / jacoco / androidTest. No Sukrara convention plugin:
 * those add Sukrara's namespace, GPL header and ktlint style, and this module keeps AOSP's sources as they are.
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.android.settingslib.spa"
    compileSdk = 37

    defaultConfig {
        minSdk = 36
    }

    // AOSP's layout (src/, res/, AndroidManifest.xml at the module root), so a diff against upstream stays a diff
    sourceSets.getByName("main") {
        kotlin.directories.add("src")
        res.directories.add("res")
        manifest.srcFile("AndroidManifest.xml")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures { compose = true }
    lint {
        targetSdk = 37
        // Lint runs in `./gradlew check` like on every module and fails on an error (upstream has none; its
        // warnings stay warnings), with the warnings Sukrara treats as errors (QualityConventionPlugin) too
        abortOnError = true
        checkReleaseBuilds = true
        error +=
            setOf(
                "HardcodedText",
                "MissingTranslation",
                "ExtraTranslation",
                "NewApi",
                "InlinedApi",
                "MissingClass",
                "StringFormatInvalid",
                "StringFormatMatches",
            )
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.appcompat)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.graphics.shapes)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.navigation.compose)
    api(libs.androidx.window)
    api(libs.google.material)
    debugApi(libs.androidx.compose.ui.tooling)
}
