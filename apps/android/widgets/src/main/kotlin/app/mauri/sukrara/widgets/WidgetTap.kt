// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.widgets

import android.content.Context
import android.content.Intent
import app.mauri.sukrara.core.model.NavTargets
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.feature.treatments.QuickSource
import app.mauri.sukrara.feature.treatments.QuickTreatmentIntents
import app.mauri.sukrara.graph.GlucoseLook
import app.mauri.sukrara.graph.stats.Bands

/**
 * What a tap on a widget does ("The pill is an action", FEATURES.md): with a value on screen, quick Add treatment
 * (Carbs first while a low is going on); the wearer's ended sensor: the new-sensor flow; without a value otherwise (no
 * reading yet, nothing for 20 min, sensor warming up, a follower's wearer's sensor ended, setup not done) the app, where
 * the reason and its fix are.
 */
sealed interface WidgetTap {
    data object OpenApp : WidgetTap

    /** The wearer's sensor has ended: straight to pairing the next one. */
    data object NewSensor : WidgetTap

    data class QuickTreatment(
        val focusCarbs: Boolean,
    ) : WidgetTap

    companion object {
        private val LOW_KINDS = setOf("low", "very_low")

        /**
         * [look] = the renderer's decision about the value on the widget; [firingKinds] = the alert kinds firing;
         * [mgdl] the shown value, [bands] the user's ranges (low = at or below the low limit, [Bands.of]). A follower logs
         * on the wearer's behalf as in the app.
         */
        fun of(
            role: Role?,
            look: GlucoseLook,
            mgdl: Int?,
            bands: Bands,
            firingKinds: Set<String>,
        ): WidgetTap {
            // the card says *Sensor ended* also when the source sent no dates (cardSensor)
            if (role == Role.Wearer && look.state == GlucoseLook.State.ENDED) return NewSensor
            if (role == null || look.state != GlucoseLook.State.VALUE) return OpenApp
            val low = firingKinds.any { it in LOW_KINDS } || (mgdl != null && bands.sane().of(mgdl).group < 0)
            return QuickTreatment(focusCarbs = low)
        }

        /** The activity intent for [tap]: quick Add treatment over the home screen, or the app. */
        fun intent(
            context: Context,
            tap: WidgetTap,
        ): Intent =
            when (tap) {
                is QuickTreatment -> {
                    QuickTreatmentIntents.open(context, QuickSource.Widget, tap.focusCarbs)
                }

                OpenApp -> {
                    launch(context)
                }

                // the app's non-exported trampoline for the new-sensor flow: no other app can open it
                NewSensor -> {
                    Intent(NavTargets.ACTION_SENSOR_SETUP)
                        .setClassName(context.packageName, NavTargets.SENSOR_SETUP_ACTIVITY)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

        private fun launch(context: Context): Intent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(Intent.ACTION_MAIN).setPackage(context.packageName)
    }
}
