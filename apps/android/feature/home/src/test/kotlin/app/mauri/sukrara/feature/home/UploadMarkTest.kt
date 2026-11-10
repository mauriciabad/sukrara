// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import android.content.Context
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

/** Every entry's LibreView cloud mark is a button to the LibreView page, whatever it says (#123). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class UploadMarkTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `each cloud mark opens the LibreView page`() {
        val asked = mutableListOf<HomeAction>()
        val glucose = Samples.wearer()
        val look = homeLook(glucose, NOW, GlucoseUnit.MGDL)
        val state =
            HomeUiState(
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
                    ),
                undo = null,
                marks = mapOf("a" to UploadMark.Uploaded, "b" to UploadMark.Pending),
            )
        compose.setContent { SukraraTheme(dark = false, dynamic = false) { HomeContent(state, HomeCallbacks(onAction = { asked += it })) } }
        for (label in listOf(R.string.home_treatment_in_clinic, R.string.home_treatment_not_in_clinic)) {
            compose.onNodeWithContentDescription(context.getString(label)).performScrollTo().performClick()
        }
        assertEquals(listOf(HomeAction.OpenClinicUpload, HomeAction.OpenClinicUpload), asked)
    }
}
