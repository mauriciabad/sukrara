// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

// Home screen.
plugins {
    id("sukrara.android.library")
    id("sukrara.android.compose")
    // the treatment list's goldens (TreatmentListGoldenTest, #207); its other screenshots are review galleries, written
    // with -Proborazzi.test.record=true
    id("sukrara.screenshots.goldens")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("sukrara.contracts")
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:graph"))
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":core:testing"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
