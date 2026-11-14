// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TrendSetting
import app.mauri.sukrara.core.model.storage.TreatmentDelivery
import app.mauri.sukrara.core.testing.trended
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.home.Samples.NOW
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone
import kotlin.math.sin

/**
 * Goldens of the treatment list's states (#207): just uploaded (the check), not in LibreView yet, the upload stopped,
 * from the pen, *Sending…*, *Not sent* with *Retry*, with and without the glucose then; and the Treatments page by day.
 * Light and dark, synthetic entries and readings. Recorded on CI by .github/workflows/goldens.yml (only this class:
 * the module's other screenshots are review galleries), compared by checks.yml with `-Proborazzi.test.verify=true`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class TreatmentListGoldenTest {
    @get:Rule val compose = createComposeRule()

    private val savedZone = TimeZone.getDefault()
    private val savedLocale = Locale.getDefault()

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(savedZone)
        Locale.setDefault(savedLocale)
    }

    /** A meal's rise and fall over the last 30 hours, one reading a minute, with a gap 13–14 h ago. */
    private val readings: List<Reading> =
        (0 until 30 * 60)
            .filter { it !in 13 * 60..14 * 60 }
            .map { ago ->
                val t = NOW.minus(Duration.ofMinutes(ago.toLong()))
                Reading(t, (130 + 55 * sin(ago / 70.0)).toInt(), null, SourceId.Libre3)
            }.reversed()
            .trended(TrendSetting.DEFAULT)

    private fun entry(
        id: String,
        kind: TreatmentKind,
        amount: Double,
        minutesAgo: Long,
        note: String = "",
    ) = Samples.treatment(id, kind, amount, minutesAgo).copy(note = note)

    private val entries =
        listOf(
            entry("up", TreatmentKind.RapidInsulin, 4.5, 6),
            entry("meal", TreatmentKind.Carbs, 45.0, 12, "Pasta"),
            entry("pen", TreatmentKind.RapidInsulin, 8.5, 95),
            entry("long", TreatmentKind.LongInsulin, 12.0, 180),
            entry("snack", TreatmentKind.Carbs, 15.0, 250),
            entry("walk", TreatmentKind.Note, 0.0, 13 * 60 + 30, "Walk"),
            entry("old", TreatmentKind.RapidInsulin, 3.0, 26 * 60),
        )

    private fun state(): HomeUiState {
        val glucose = Samples.wearer()
        val look = homeLook(glucose, NOW, GlucoseUnit.MGDL)
        return HomeUiState(
            glucose = glucose,
            now = NOW,
            look = look,
            reason = NoDataReason.of(glucose, look),
            banner = null,
            firstProblem = null,
            nextSetupStep = null,
            dataProblems = emptyList(),
            treatments = entries,
            history = readings,
            undo = null,
            fromPen = setOf("pen"),
            marks =
                mapOf(
                    "up" to UploadMark.Uploaded,
                    "meal" to UploadMark.Pending,
                    "pen" to UploadMark.Uploaded,
                    "snack" to UploadMark.Stopped,
                ),
            delivery = mapOf("long" to TreatmentDelivery.Pending, "snack" to TreatmentDelivery.Failed),
        )
    }

    private fun list(
        name: String,
        dark: Boolean,
    ) {
        val s = state()
        compose.setContent {
            SukraraTheme(dark = dark, dynamic = false) {
                Column(Modifier.width(411.dp).background(MaterialTheme.colorScheme.background).testTag("list")) {
                    s.rows(entries.take(6)).forEach { row ->
                        TreatmentRow(
                            row,
                            NOW,
                            GlucoseUnit.MGDL,
                            onOpen = {},
                            onDelete = {},
                            onOpenUpload = {},
                            onRetry = {},
                            // the first row's entry got to LibreView a moment ago: its check shows for 3 s
                            justUploaded = row.treatment.id == "up",
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("list").captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun page(
        name: String,
        dark: Boolean,
    ) {
        val s = state()
        compose.setContent {
            SukraraTheme(dark = dark, dynamic = false) {
                TreatmentHistoryContent(s, HomeCallbacks(), onBack = {}, zone = ZoneOffset.UTC)
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/$name.png")
    }

    @Test fun listLight() = list("treatment-list-light", dark = false)

    @Test fun listDark() = list("treatment-list-dark", dark = true)

    @Test fun pageLight() = page("treatment-history-light", dark = false)

    @Test fun pageDark() = page("treatment-history-dark", dark = true)
}
