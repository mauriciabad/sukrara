// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.model.PenNearEntry
import app.mauri.sukrara.designsystem.theme.SukraraTheme
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
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone

/**
 * Goldens of the Add treatment dialog and its lock-screen version (#181: the owner's "huge step backwards" from the
 * PoC): the title with the *When* chip, the kind markers in the labels, the labels on the outline, the units, the
 * quiet line and the action row (*Open Sukrara* / *Unlock* at the start, the countdown ring on *Cancel*). Synthetic
 * entries; the dialog surface only. Recorded on CI by .github/workflows/goldens.yml (only this class: the module's
 * other screenshots are review galleries), compared by checks.yml with `-Proborazzi.test.verify=true`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi", application = TestTreatmentsApp::class)
class TreatmentDialogGoldenTest {
    @get:Rule val compose = createComposeRule()

    private val savedZone = TimeZone.getDefault()
    private val savedLocale = Locale.getDefault()
    private val now = Instant.parse("2026-06-15T12:30:00Z")

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

    private fun shot(
        name: String,
        dark: Boolean,
        form: TreatmentFormState,
        lock: LockInfo? = null,
    ) {
        compose.setContent {
            SukraraTheme(dark = dark, dynamic = false) {
                Box(Modifier.width(411.dp)) {
                    TreatmentDialogContent(
                        form = form,
                        focusCarbs = false,
                        zone = ZoneOffset.UTC,
                        now = { now },
                        onSave = { _, _ -> },
                        onCancel = {},
                        onOpenApp = if (lock == null) ({}) else null,
                        lock = lock,
                        quiet = QuietRules(afterInsulinMin = 60, afterCarbsMin = 30),
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("treatment-dialog").captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun filled() = TreatmentFormState(rapid = "4.5", carbs = "45", note = "Pasta", at = now.minusSeconds(1200))

    private fun lock() = LockInfo(12, onUnlock = {}, spokenSeconds = 15, fraction = { 0.6f })

    @Test fun filledLight() = shot("treatment-dialog-light", dark = false, filled())

    @Test fun emptyDark() = shot("treatment-dialog-empty-dark", dark = true, TreatmentFormState())

    @Test fun lockLight() = shot("treatment-dialog-lock-light", dark = false, TreatmentFormState(carbs = "15"), lock())

    @Test fun lockDark() = shot("treatment-dialog-lock-dark", dark = true, TreatmentFormState(carbs = "15"), lock())

    /**
     * *Already logged by your pen?* over the dialog (the owner's 0.14.3 report: it sat flat on the card, no scrim): the
     * whole screen as the quick dialog's window shows it, its own scrim behind the card and the question's over both.
     */
    private fun penQuestion(
        name: String,
        dark: Boolean,
    ) {
        compose.setContent {
            SukraraTheme(dark = dark, dynamic = false) {
                Box(
                    // the app's background behind the quick dialog's scrim
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = DIALOG_DIM)),
                    contentAlignment = Alignment.Center,
                ) {
                    TreatmentDialogContent(
                        form = TreatmentFormState(rapid = "5.5"),
                        focusCarbs = false,
                        zone = ZoneOffset.UTC,
                        now = { now },
                        onSave = { _, _ -> },
                        onCancel = {},
                        onOpenApp = {},
                        quiet = QuietRules(afterInsulinMin = 60, afterCarbsMin = 30),
                        modifier = Modifier.padding(16.dp),
                    )
                    PenDuplicateDialog(PenNearEntry("p1", 5.5, now.minusSeconds(1080).epochSecond), onCancel = {}, onLog = {})
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/$name.png")
    }

    @Test fun penQuestionLight() = penQuestion("treatment-pen-question-light", dark = false)

    @Test fun penQuestionDark() = penQuestion("treatment-pen-question-dark", dark = true)
}
