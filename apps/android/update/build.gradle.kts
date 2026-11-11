// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

// The GitHub version's updater (#188): the daily check of the GitHub releases, the update notification, the download,
// its checks (SHA-256, Sukrara's release certificate) and the install through PackageInstaller. Only the `full`
// flavor depends on it (app/build.gradle.kts `fullImplementation`): the Google Play build has none of it, not even
// REQUEST_INSTALL_PACKAGES (ManifestTest, verify-apk.sh --play).
plugins {
    id("sukrara.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:data"))
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    testImplementation(project(":core:testing"))
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
}

tasks.withType<Test>().configureEach {
    // UpdateEndpointTest compares the built-in release certificate with this file
    inputs
        .file(rootDir.resolve("../../tools/release/android-cert.sha256"))
        .withPropertyName("releaseCert")
        .withPathSensitivity(PathSensitivity.NONE)
}
