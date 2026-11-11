// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

// New-sensor flow, LibreView sign-in. The sensor-end notices are the alert runtime's (:monitor).
plugins {
    id("sukrara.android.library")
    id("sukrara.android.compose")
    id("sukrara.screenshots.gallery")
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    // the one duration formatter's words (CardWords.durations)
    implementation(project(":core:graph"))
    implementation(project(":sensor"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(project(":core:testing"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
