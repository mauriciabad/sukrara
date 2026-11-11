// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

// Settings pages.
plugins {
    id("sukrara.android.library")
    id("sukrara.android.compose")
    id("sukrara.screenshots.gallery")
}

dependencies {
    api(project(":core:model"))
    // the alert settings model (SPEC §3) the alert pages edit
    api(project(":core:alerts"))
    // the schema settings store and keys, settings transfer, the diagnostic log
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    // THE clock formatter (24-hour setting) for "muted until" times
    implementation(project(":core:graph"))
    // the settings file pickers (Settings › Advanced); Back clears the settings search
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    testImplementation(project(":core:testing"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
