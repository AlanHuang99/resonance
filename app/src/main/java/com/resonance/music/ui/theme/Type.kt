package com.resonance.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Space Grotesk for display/headings/titles (the brand voice); Inter for body and
// labels (dense, readable). Both are bundled OFL fonts — see ui/theme/Fonts.kt.
val ResonanceTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 52.sp, lineHeight = 60.sp, letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 42.sp, lineHeight = 50.sp, letterSpacing = (-0.25).sp
    ),
    displaySmall = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp, lineHeight = 42.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp, lineHeight = 38.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp, lineHeight = 34.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 22.sp, lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp
    )
)
