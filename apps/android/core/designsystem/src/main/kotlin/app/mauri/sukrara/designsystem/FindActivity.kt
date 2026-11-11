// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * The activity behind a context, also through the wrappers Compose and the theme put around it (a composable's
 * `LocalContext` is often one): what a system sheet or dialog shows over (Credential Manager, the launcher's add-widget
 * dialog). Null for an application or service context.
 */
tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
