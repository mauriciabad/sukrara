// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

// Pure Kotlin types and ports every module shares (additive only).
plugins {
    id("sukrara.jvm.library")
    // TrendRateTest runs contracts/trend
    id("sukrara.contracts")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.serialization.json)
}
