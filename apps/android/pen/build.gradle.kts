// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

// Insulin pens: NFC read (OpenNov), PenEngine, the pen ledger, the read card and the pen notifications.
plugins {
    id("sukrara.android.library")
    id("sukrara.android.compose")
    id("sukrara.contracts")
    id("org.jetbrains.kotlin.plugin.serialization")
    alias(libs.plugins.roborazzi)
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    // THE clock formatter (the 24-hour setting) for dose times
    implementation(project(":core:graph"))
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(project(":core:testing"))
    // the read card's words from the real resources (en + es)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    // the pen screens' goldens (PenShotsTest): Settings › Insulin pens and home's banner, drawn as the app does
    testImplementation(project(":feature:settings"))
    testImplementation(project(":feature:home"))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Goldens in src/test/snapshots, recorded on CI by .github/workflows/goldens.yml; `verifyRoborazziDebug` (CI) compares,
// `compareRoborazziDebug` shows a local diff. A plain test run neither writes nor compares.
tasks.withType<Test>().configureEach {
    // Robolectric on SDK 36 reaches into FileDescriptor internals
    jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}
