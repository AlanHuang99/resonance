package com.resonance.music.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun ResonanceTheme(
    themeRepository: ThemeRepository? = null,
    content: @Composable () -> Unit
) {
    val appTheme by (themeRepository?.currentTheme?.collectAsState(initial = AppTheme.NEON_PULSE)
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(AppTheme.NEON_PULSE) })

    val context = LocalContext.current
    val isDarkSystem = isSystemInDarkTheme()

    val colorScheme = when (appTheme) {
        AppTheme.MATERIAL_YOU -> {
            if (isDarkSystem) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        else -> appTheme.darkColorScheme() // All custom themes are dark-first/futuristic
    }

    val isLightTheme = appTheme == AppTheme.MATERIAL_YOU && !isDarkSystem
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLightTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ResonanceTypography,
        content = content
    )
}
