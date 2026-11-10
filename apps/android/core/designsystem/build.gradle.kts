// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

// Theme, motion, haptics and the shared settings rows.
plugins {
    id("sukrara.android.library")
    id("sukrara.android.compose")
    id("sukrara.contracts")
}

dependencies {
    // NumberDialog takes the generated NumberSpec (the one number input, CLAUDE.md "Settings never diverge")
    api(project(":core:data"))
    // THE mute lines (MuteText) word the alert engine's MuteCopy
    api(project(":core:alerts"))
    // the generated pictures (AvatarImage) draw the protocol's Avatar numbers
    api(project(":core:protocol"))
    testImplementation(libs.junit)
    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.serialization.json)
    // UserErrorsTest reads the worded errors back in English and Spanish
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

// Robolectric reaches into FileDescriptor internals on JDK 21
tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}
