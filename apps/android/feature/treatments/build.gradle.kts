// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

// Treatment dialog, quick treatment, QS tile.
plugins {
    id("sukrara.android.library")
    id("sukrara.android.compose")
    id("sukrara.screenshots.gallery")
    id("sukrara.contracts")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:graph"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":core:testing"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
