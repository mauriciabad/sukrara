// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.theme

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * M3's type scale on the device's own fonts, the ones the stock Settings app (SPA's SettingsTheme) uses: the headline
 * family (`config_headlineFontFamily`: Google Sans on a Pixel, the round face the owner liked in the PoC's settings,
 * #181) for display, headline and title styles, the body family (`config_bodyFontFamily`) for body and label. A device
 * without them keeps the system sans. The person's font size applies (sizes are `sp`). Titles a step bolder so headers
 * read at a glance.
 */
internal fun sukraraTypography(context: Context): Typography {
    val brand = context.deviceFamily("config_headlineFontFamily", "config_headlineFontFamilyMedium")
    val plain = context.deviceFamily("config_bodyFontFamily", "config_bodyFontFamilyMedium")
    return Typography().run {
        copy(
            displayLarge = displayLarge.on(brand),
            displayMedium = displayMedium.on(brand),
            displaySmall = displaySmall.on(brand),
            headlineLarge = headlineLarge.on(brand),
            headlineMedium = headlineMedium.on(brand),
            headlineSmall = headlineSmall.on(brand),
            titleLarge = titleLarge.on(brand).copy(fontWeight = FontWeight.SemiBold),
            titleMedium = titleMedium.on(brand).copy(fontWeight = FontWeight.SemiBold),
            titleSmall = titleSmall.on(brand),
            bodyLarge = bodyLarge.on(plain),
            bodyMedium = bodyMedium.on(plain),
            bodySmall = bodySmall.on(plain),
            labelLarge = labelLarge.on(plain).copy(fontWeight = FontWeight.SemiBold),
            labelMedium = labelMedium.on(plain),
            labelSmall = labelSmall.on(plain),
        )
    }
}

private fun TextStyle.on(family: FontFamily) = copy(fontFamily = family)

/** A device font family named by the framework's [normal] / [medium] config strings (SettingsFontFamily's rule). */
@OptIn(ExperimentalTextApi::class)
private fun Context.deviceFamily(
    normal: String,
    medium: String,
): FontFamily {
    val n = androidConfig(normal)
    val m = androidConfig(medium)
    if (n.isEmpty() || m.isEmpty()) return FontFamily.Default
    return FontFamily(
        Font(DeviceFontFamilyName(n), FontWeight.Normal),
        Font(DeviceFontFamilyName(m), FontWeight.Medium),
    )
}

@SuppressLint("DiscouragedApi") // the framework's own config strings, read as SPA's SettingsFontFamily does
private fun Context.androidConfig(name: String): String {
    val id = resources.getIdentifier(name, "string", "android")
    return if (id == 0) "" else runCatching { resources.getString(id) }.getOrDefault("")
}
