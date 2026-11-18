// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.source

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.mauri.sukrara.core.designsystem.R
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.designsystem.icons.Service
import app.mauri.sukrara.designsystem.icons.ServiceIcon
import app.mauri.sukrara.designsystem.rows.RowIcon

/**
 * THE words and icons of a source (docs/44 "Naming"), the same in setup's picker and Settings › Source: a source is
 * named by what happens (*Sukrara reads your sensor*), a stream by whose phone it is.
 */
@StringRes
fun SourceSpec.titleRes(): Int =
    when (kind) {
        SourceKind.Sensor -> R.string.ds_source_sensor
        SourceKind.PhoneApp -> R.string.ds_source_phone_app
        SourceKind.Sukrara -> if (person == Person.Yours) R.string.ds_source_sukrara_yours else R.string.ds_source_sukrara_theirs
    }

/** What the source brings and how: its delay, what it needs (with whether it works offline: [sourceMeans]). */
@StringRes
fun SourceSpec.meansRes(): Int =
    when (kind) {
        SourceKind.Sensor -> {
            R.string.ds_source_sensor_means
        }

        SourceKind.PhoneApp -> {
            if (person == Person.Yours) R.string.ds_source_phone_app_means else R.string.ds_source_phone_app_means_theirs
        }

        SourceKind.Sukrara -> {
            if (person ==
                Person.Yours
            ) {
                R.string.ds_source_sukrara_yours_means
            } else {
                R.string.ds_source_sukrara_theirs_means
            }
        }
    }

/**
 * What [spec] brings, in words: [meansRes], plus *works offline* when it does and no group title above it says so
 * ([grouped]: setup's picker groups the offline sources under *On this phone · works offline*).
 */
@Composable
fun sourceMeans(
    spec: SourceSpec,
    grouped: Boolean = false,
): String {
    val means = stringResource(spec.meansRes())
    return if (spec.kind.offline && !grouped) stringResource(R.string.ds_source_means_offline, means) else means
}

/** *Yours* / *Someone else's*. */
@StringRes
fun Person.labelRes(): Int = if (this == Person.Yours) R.string.ds_person_yours else R.string.ds_person_theirs

/** The source in one word, for summaries (*sensor*, *Juggluco*, *Sukrara*). */
@StringRes
fun SourceKind.shortRes(): Int =
    when (this) {
        SourceKind.Sensor -> R.string.ds_source_short_sensor
        SourceKind.PhoneApp -> R.string.ds_source_short_phone_app
        SourceKind.Sukrara -> R.string.ds_source_short_sukrara
    }

/** The Settings overview's *Source* summary: whose (the person's [name] when known) · where from. */
@Composable
fun sourceSummary(
    spec: SourceSpec,
    name: String? = null,
): String {
    val whose = name?.takeIf { spec.person == Person.Theirs && it.isNotBlank() } ?: stringResource(spec.person.labelRes())
    return stringResource(R.string.ds_source_summary, whose, stringResource(spec.kind.shortRes()))
}

/** The source's icon as a monochrome glyph (rows that tint it). */
@DrawableRes
fun SourceKind.iconRes(): Int =
    when (this) {
        SourceKind.Sensor -> R.drawable.ds_ic_dia_sensor
        SourceKind.PhoneApp -> R.drawable.ds_ic_smartphone
        SourceKind.Sukrara -> R.drawable.ds_ic_status_sukrara
    }

/** The source's icon at a row's start: an app source shows the app's own icon, the others the tinted glyph. */
@Composable
fun SourceIcon(
    kind: SourceKind,
    modifier: Modifier = Modifier,
) {
    if (kind == SourceKind.PhoneApp) ServiceIcon(Service.Juggluco, modifier) else RowIcon(kind.iconRes())
}
