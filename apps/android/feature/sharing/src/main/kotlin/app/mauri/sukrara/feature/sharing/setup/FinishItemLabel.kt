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
            FinishItem.RestoreFollowers -> R.string.sh_finish_identity
        },
    )

/** Home's "Finish setup" banner text ("Next: …"): a walk to resume names its step by the step's own title. */
fun SetupCard.label(context: Context): String =
    when (this) {
        is SetupCard.Finish -> {
            item.label(context)
        }

        is SetupCard.Resume -> {
            context.getString(
                when (step) {
                    SetupStep.Welcome -> R.string.sh_setup_welcome_title
                    SetupStep.Role -> R.string.sh_setup_role_title
                    SetupStep.Join -> R.string.sh_join_title
                    SetupStep.Permissions -> R.string.sh_setup_permissions_title
                    SetupStep.Updates -> R.string.sh_setup_updates_title
                    SetupStep.Sensor -> R.string.sh_setup_sensor_title
                    SetupStep.Alerts -> R.string.sh_setup_alerts_title
                    SetupStep.Identity -> R.string.sh_identity_title
                    SetupStep.History -> R.string.sh_setup_history_title
                },
            )
        }
    }
