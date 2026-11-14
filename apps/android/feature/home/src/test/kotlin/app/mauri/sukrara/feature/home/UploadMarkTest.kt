// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.home.Samples.NOW
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The LibreView marks (#123, #207): an entry already in LibreView shows nothing (Google Photos' way), one that isn't
 * there yet or waits for a stopped upload shows its mark, and every mark is a button to the LibreView page.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h2000dp")
class UploadMarkTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun state(marks: Map<String, UploadMark>): HomeUiState {
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
            treatments =
                listOf(
                    Samples.treatment("a", TreatmentKind.Carbs, 30.0, 25),
                    Samples.treatment("b", TreatmentKind.RapidInsulin, 4.5, 30),
                    Samples.treatment("c", TreatmentKind.LongInsulin, 12.0, 35),
                ),
            undo = null,
            marks = marks,
        )
    }

    @Test fun `only a mark that says something shows, and each opens the LibreView page`() {
        val asked = mutableListOf<HomeAction>()
        val state = state(mapOf("a" to UploadMark.Uploaded, "b" to UploadMark.Pending, "c" to UploadMark.Stopped))
        compose.setContent { SukraraTheme(dark = false, dynamic = false) { HomeContent(state, HomeCallbacks(onAction = { asked += it })) } }
        compose.onNodeWithContentDescription(context.getString(R.string.home_treatment_in_clinic)).assertDoesNotExist()
        for (label in listOf(R.string.home_treatment_not_in_clinic, R.string.home_treatment_clinic_stopped)) {
            compose.onNodeWithContentDescription(context.getString(label)).performScrollTo().performClick()
        }
        assertEquals(listOf(HomeAction.OpenClinicUpload, HomeAction.OpenClinicUpload), asked)
    }

    @Test fun `an entry that just got to LibreView shows the check for 3 seconds`() {
        var marks by mutableStateOf(mapOf("a" to UploadMark.Pending))
        compose.mainClock.autoAdvance = false
        compose.setContent { SukraraTheme(dark = false, dynamic = false) { HomeContent(state(marks), HomeCallbacks()) } }
        compose.mainClock.advanceTimeBy(1_000)
        val done = context.getString(R.string.home_treatment_in_clinic)
        compose.onNodeWithContentDescription(done).assertDoesNotExist()
        compose.runOnUiThread {
            marks = mapOf("a" to UploadMark.Uploaded)
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription(done).assertExists()
        compose.mainClock.advanceTimeBy(JUST_UPLOADED_MS + 1_000)
        compose.onNodeWithContentDescription(done).assertDoesNotExist()
    }
}
