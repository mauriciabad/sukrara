// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

/**
 * Intent actions that open a place of the app from outside a screen (notifications, widgets), so the modules that
 * post them don't depend on the module that shows them. Intents set the app's package explicitly.
 */
object NavTargets {
    /** The quick "Add treatment" dialog over any app (activity declares this action). */
    const val ACTION_QUICK_TREATMENT = "app.mauri.sukrara.action.QUICK_TREATMENT"

    /** Boolean extra of [ACTION_QUICK_TREATMENT]: open with Carbs focused (a low is going on). */
    const val EXTRA_FOCUS_CARBS = "app.mauri.sukrara.extra.FOCUS_CARBS"

    /** Boolean extra of [ACTION_QUICK_TREATMENT]: open with Long-acting focused (a rapid pen's dose was just read). */
    const val EXTRA_FOCUS_LONG = "app.mauri.sukrara.extra.FOCUS_LONG"

    /** Long extra of [ACTION_QUICK_TREATMENT]: *When* starts at this time (epoch ms; a pen read's injection), not now. */
    const val EXTRA_AT = "app.mauri.sukrara.extra.AT"

    /**
     * The new-sensor / sensor setup flow. Intents for it target [SENSOR_SETUP_ACTIVITY] explicitly (not exported:
     * only Sukrara's own PendingIntents reach it); the main activity ignores the action from any other app.
     */
    const val ACTION_SENSOR_SETUP = "app.mauri.sukrara.action.SENSOR_SETUP"

    /** The non-exported trampoline (`:app`) that opens the new-sensor flow: `Intent.setClassName(packageName, this)`. */
    const val SENSOR_SETUP_ACTIVITY = "app.mauri.sukrara.SensorSetupActivity"

    /** Boolean extra of [ACTION_SENSOR_SETUP]: a sensor was tapped (NFC) while Sukrara wasn't in front. */
    const val EXTRA_FROM_SENSOR_TAP = "app.mauri.sukrara.extra.FROM_SENSOR_TAP"

    /** Settings › Insulin pens › one pen ([EXTRA_PEN_SERIAL]); without the extra, the pens page. */
    const val ACTION_PEN_PAGE = "app.mauri.sukrara.action.PEN_PAGE"
    const val EXTRA_PEN_SERIAL = "app.mauri.sukrara.extra.PEN_SERIAL"

    /** Boolean extra of [ACTION_PEN_PAGE]: that pen's last big read in *Doses read* (*Review*, *Undo this read*). */
    const val EXTRA_PEN_REVIEW = "app.mauri.sukrara.extra.PEN_REVIEW"
}
