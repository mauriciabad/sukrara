// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.setup

import android.content.Context
import app.mauri.sukrara.feature.sharing.R

/** The words of a Finish-setup row, for home's "Finish setup" banner (home shows the first one). */
fun FinishItem.label(context: Context): String =
    context.getString(
        when (this) {
            FinishItem.AllowAlerts -> R.string.sh_finish_allow
            FinishItem.ConnectSensor -> R.string.sh_finish_sensor
            FinishItem.AlertsNotConfirmed -> R.string.sh_finish_alerts
        },
    )

/** Home's "Finish setup" banner text ("Next: …"): a walk to resume names its step by the step's own title. */
fun SetupCard.label(context: Context): String =
    when (this) {
        is SetupCard.Finish -> item.label(context)
        is SetupCard.Resume -> context.getString(step.title)
    }

/** The step's own title (its heading in setup; home's *Next: …*, Settings' *Finish setup* list). */
val SetupStep.title: Int
    get() =
        when (this) {
            SetupStep.Welcome -> R.string.sh_setup_welcome_title
            SetupStep.Person -> R.string.sh_setup_person_title
            SetupStep.Source -> R.string.sh_setup_source_title
            SetupStep.Join -> R.string.sh_join_title
            SetupStep.Permissions -> R.string.sh_setup_permissions_title
            SetupStep.Sensor -> R.string.sh_setup_sensor_title
            SetupStep.Alerts -> R.string.sh_setup_alerts_title
            SetupStep.Ready -> R.string.sh_setup_ready_title
            SetupStep.Updates -> R.string.sh_setup_updates_title
            SetupStep.Root -> R.string.sh_setup_root_title
            SetupStep.Notification -> R.string.sh_setup_notification_title
            SetupStep.Identity -> R.string.sh_identity_title
            SetupStep.Pens -> R.string.sh_setup_pens_title
            SetupStep.Garmin -> R.string.sh_setup_garmin_title
            SetupStep.History -> R.string.sh_setup_history_title
        }

/** Settings' *Finish setup* summary: the optional steps still open, by title ("Your followers · Insulin pens"). */
fun List<SetupStep>.summary(context: Context): String = joinToString(" · ") { context.getString(it.title) }
