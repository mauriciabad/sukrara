// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data

import app.mauri.sukrara.core.data.settings.SurfaceSettings
import app.mauri.sukrara.core.model.SensorEndConfig
import app.mauri.sukrara.settings.SettingsSpec
import org.junit.Assert.assertEquals
import org.junit.Test

/** Settings › Sensor end as stored (`sensor.reminders` JSON), its *Remind again* field (number `endedRepeatHours`). */
class SensorEndSettingsTest {
    @Test
    fun `the repeat's default is the schema's`() {
        assertEquals(SettingsSpec.ENDED_REPEAT_HOURS.default.toInt(), SensorEndConfig().endedRepeatHours)
        assertEquals(SensorEndConfig(), SurfaceSettings.decodeSensorEnd("""{"countdown":true}"""))
    }

    @Test
    fun `the repeat is kept, off included, and an invalid one is brought into range`() {
        for (h in listOf(0, 1, 4, 12)) {
            val c = SensorEndConfig(endedRepeatHours = h)
            assertEquals(c, SurfaceSettings.decodeSensorEnd(SurfaceSettings.encodeSensorEnd(c)))
        }
        assertEquals(12, SurfaceSettings.decodeSensorEnd("""{"endedRepeatHours":99}""").endedRepeatHours)
    }
}
