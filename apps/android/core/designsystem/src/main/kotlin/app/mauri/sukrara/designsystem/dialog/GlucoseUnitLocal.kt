// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.dialog

import androidx.compose.runtime.staticCompositionLocalOf
import app.mauri.sukrara.core.model.GlucoseUnit

/**
 * The glucose unit the screens show and take numbers in (`general.unit`; contracts/settings/units.json): the app's root
 * provides the person's setting. Every [NumberDialog] of a glucose format and every settings label follows it (the
 * Mac's `glucoseUnit` environment value is the same).
 */
val LocalGlucoseUnit = staticCompositionLocalOf { GlucoseUnit.MGDL }
