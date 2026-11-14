// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.testing

import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.TrendRate
import app.mauri.sukrara.core.model.TrendSetting

/**
 * [this] with THE trend as the app's one annotate point gives it (`TrendRate.annotate`), for tests that hand readings
 * to a consumer directly: From the sensor by default, so "a reading with rate x" in a test shows the arrow of x.
 */
fun List<Reading>.trended(setting: TrendSetting = TrendSetting.Sensor): List<Reading> = TrendRate.annotate(this, setting)

/** One reading with the trend of its own sensor rate ([trended]). */
fun Reading.trended(): Reading = listOf(this).trended().single()
