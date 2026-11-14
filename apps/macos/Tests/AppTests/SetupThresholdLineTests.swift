// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import SukraraAlerts
import SukraraCore
import Testing

@testable import SukraraApp

/// Setup's Alerts step: the line under each threshold says only what sets the alert off (the comparison the engine
/// makes; the pop-up beside it has the number and unit), "Off" for an alert that is off, nothing for no data. No span,
/// no "In range", no name repeated (Android's AlertThresholdRowsTest).
@Suite struct SetupThresholdLineTests {
    @Test func eachLevelSaysItsComparison() {
        #expect(SetupView.thresholdLine(.veryLow, 55, on: true, unit: .mgdl) == "Up to 55")
        #expect(SetupView.thresholdLine(.low, 70, on: true, unit: .mgdl) == "Below 70")
        #expect(SetupView.thresholdLine(.high, 180, on: true, unit: .mgdl) == "From 180")
        #expect(SetupView.thresholdLine(.extremelyHigh, 300, on: true, unit: .mgdl) == "From 300")
        #expect(SetupView.thresholdLine(.low, 70, on: true, unit: .mmol) == "Below 3.9")
    }

    @Test func offSaysOffAndNoDataSaysNothing() {
        #expect(SetupView.thresholdLine(.veryHigh, 250, on: false, unit: .mgdl) == "Off")
        #expect(SetupView.thresholdLine(.noData, 20, on: true, unit: .mgdl) == nil)
    }
}
