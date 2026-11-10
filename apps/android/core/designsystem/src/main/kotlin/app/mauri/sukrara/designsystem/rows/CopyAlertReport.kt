// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.rows

import android.content.ClipData
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import kotlinx.coroutines.launch

/**
 * *Copy report* of the alert checklist ([AlertReadiness.report]: the failed alert steps and the missing items, the
 * app's version and Android's; nothing personal), the one way out of [app.mauri.sukrara.core.model.AlertRequirement.AlertDelivery]
 * on home and in Settings › Alert reliability. Android confirms the copy itself; a haptic confirms the tap.
 */
@Composable
fun rememberCopyAlertReport(readiness: AlertReadiness): () -> Unit {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    return {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        val text = readiness.report(version ?: "?", Build.VERSION.SDK_INT)
        scope.launch {
            clipboard.setClipEntry(ClipData.newPlainText("Sukrara", text).toClipEntry())
            haptics.confirm()
        }
    }
}
