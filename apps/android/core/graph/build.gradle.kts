// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

// The one card renderer: GlucoseCard, graph, layout, freshness.
plugins {
    id("sukrara.android.library")
    id("sukrara.android.compose")
    id("sukrara.contracts")
    id("sukrara.screenshots.goldens")
}

android {
    testOptions.unitTests.all {
        // `-Dlayout.write=true`: CardLayoutTest writes its shared answers into contracts/graph/layout/expect.json
        System.getProperty("layout.write")?.let { v -> it.systemProperty("layout.write", v) }
    }
}

dependencies {
    api(project(":core:model"))
    // the schema settings keys of the graph's settings (GraphSettings); the generated SettingsSpec is :core:model's
    implementation(project(":core:data"))
    // ColorUtils, PathParser for the Canvas drawing
    implementation(libs.androidx.core.ktx)
    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.serialization.json)
    // Robolectric resources (CardWords en + es) and the card's screenshots
    testImplementation(libs.robolectric)
}
