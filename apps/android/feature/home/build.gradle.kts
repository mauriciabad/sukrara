// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

// Home screen.
plugins {
    id("sukrara.android.library")
    id("sukrara.android.compose")
    id("sukrara.screenshots.gallery")
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
}
