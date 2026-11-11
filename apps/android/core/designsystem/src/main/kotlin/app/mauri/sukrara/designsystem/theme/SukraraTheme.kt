// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.theme

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.rememberReducedMotion

/**
 * The app's theme: the device's dynamic colours (Material You, the wallpaper's tint on the background and every
 * surface, as the PoC and the stock Settings app: the owner in #181) when [dynamic], else Sukrara's own palettes (its
 * dark one near black); the type scale on the device's fonts ([sukraraTypography]: the Settings app's round headline
 * face on a Pixel). Also provides [LocalReducedMotion] for every animation below it, and a configuration
 * whose night mode is [dark], so what follows the system's theme below it (the settings pages' SettingsTheme) follows
 * this one (the same in the app, where [dark] is the system's; a screenshot test's dark is dark everywhere).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SukraraTheme(
    dark: Boolean = isSystemInDarkTheme(),
    dynamic: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme =
        when {
            dynamic && dark -> dynamicDarkColorScheme(context)
            dynamic -> dynamicLightColorScheme(context)
            dark -> SukraraDark
            else -> SukraraLight
        }
    val config = LocalConfiguration.current
    val night = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    val themed =
        remember(config, night) {
            if (config.uiMode and Configuration.UI_MODE_NIGHT_MASK == night) {
                config
            } else {
                Configuration(config).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night }
            }
        }
    CompositionLocalProvider(LocalReducedMotion provides rememberReducedMotion(), LocalConfiguration provides themed) {
        val typography = remember(context) { sukraraTypography(context) }
        // Material 3 Expressive (the PoC's settings and Android 16's own apps): its spring motion for every component
        MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), typography = typography, content = content)
    }
}

private val NearBlack = Color(0xFF0B0C0E)

/** Sukrara's own dark palette (no dynamic colours): text on its surfaces ≥ 4.5:1 (ThemeContrastTest). */
internal val SukraraDark =
    darkColorScheme(
        primary = Color(0xFF8FCDFF),
        onPrimary = Color(0xFF00344F),
        primaryContainer = Color(0xFF004B70),
        onPrimaryContainer = Color(0xFFCAE6FF),
        secondary = Color(0xFFB7C9D9),
        onSecondary = Color(0xFF22323F),
        secondaryContainer = Color(0xFF384956),
        onSecondaryContainer = Color(0xFFD3E5F5),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = NearBlack,
        onBackground = Color(0xFFE2E2E5),
        surface = NearBlack,
        onSurface = Color(0xFFE2E2E5),
        surfaceVariant = Color(0xFF41474D),
        onSurfaceVariant = Color(0xFFC1C7CE),
        outline = Color(0xFF8B9198),
        outlineVariant = Color(0xFF41474D),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF15171A),
        surfaceContainer = Color(0xFF1A1C1F),
        surfaceContainerHigh = Color(0xFF24272A),
        surfaceContainerHighest = Color(0xFF2F3235),
        surfaceBright = Color(0xFF2C2F33),
    )

/** Sukrara's own light palette (used when the person picks light and dynamic colours are off). */
internal val SukraraLight =
    lightColorScheme(
        primary = Color(0xFF00629B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFCAE6FF),
        onPrimaryContainer = Color(0xFF001E31),
        secondary = Color(0xFF50606E),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD3E5F5),
        onSecondaryContainer = Color(0xFF0C1D29),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF8F9FC),
        onBackground = Color(0xFF191C1E),
        surface = Color(0xFFF8F9FC),
        onSurface = Color(0xFF191C1E),
        surfaceVariant = Color(0xFFDDE3EA),
        onSurfaceVariant = Color(0xFF41474D),
        outline = Color(0xFF71787E),
        surfaceBright = Color.White,
        surfaceContainerLow = Color(0xFFF2F3F6),
        surfaceContainer = Color(0xFFECEEF1),
        surfaceContainerHigh = Color(0xFFE6E8EB),
    )
