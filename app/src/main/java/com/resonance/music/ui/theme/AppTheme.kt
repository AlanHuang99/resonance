package com.resonance.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * All available themes in Resonance.
 * Each theme has a display name, a preview color, and dark/light color schemes.
 */
enum class AppTheme(
    val displayName: String,
    val description: String,
    val previewColor: Color
) {
    NEON_PULSE(
        displayName = "Neon Pulse",
        description = "Cyan & magenta cyberpunk",
        previewColor = NeonCyan
    ),
    AURORA(
        displayName = "Aurora",
        description = "Northern lights green & purple",
        previewColor = AuroraGreen
    ),
    MIDNIGHT_OCEAN(
        displayName = "Midnight Ocean",
        description = "Deep blue & teal depths",
        previewColor = OceanBlue
    ),
    SOLAR_FLARE(
        displayName = "Solar Flare",
        description = "Warm amber & orange energy",
        previewColor = SolarAmber
    ),
    VOID(
        displayName = "Void",
        description = "Ultra-minimal monochrome",
        previewColor = VoidWhite
    ),
    SAKURA_BLOOM(
        displayName = "Sakura Bloom",
        description = "Cherry blossom pink & rose",
        previewColor = SakuraPink
    ),
    EMERALD_CITY(
        displayName = "Emerald City",
        description = "Rich green & gold luxury",
        previewColor = EmeraldGreen
    ),
    CYBER_VIOLET(
        displayName = "Cyber Violet",
        description = "Electric purple & indigo",
        previewColor = CyberViolet
    ),
    ARCTIC_ICE(
        displayName = "Arctic Ice",
        description = "Frosty blue & crisp white",
        previewColor = ArcticBlue
    ),
    MATERIAL_YOU(
        displayName = "Material You",
        description = "Dynamic colors from your wallpaper",
        previewColor = Color(0xFF6750A4)
    );

    fun darkColorScheme(): ColorScheme = when (this) {
        NEON_PULSE -> darkColorScheme(
            primary = NeonCyan,
            onPrimary = Color.Black,
            primaryContainer = NeonCyanDim,
            onPrimaryContainer = Color.White,
            secondary = NeonMagenta,
            onSecondary = Color.Black,
            secondaryContainer = NeonMagentaDim,
            onSecondaryContainer = Color.White,
            tertiary = NeonMagenta,
            surface = NeonSurface,
            surfaceVariant = NeonSurfaceVariant,
            onSurface = NeonOnSurface,
            onSurfaceVariant = NeonOnSurfaceVariant,
            background = NeonBackground,
            onBackground = NeonOnSurface,
            error = NeonError,
            outline = NeonOnSurfaceVariant,
            outlineVariant = Color(0xFF2A3550)
        )

        AURORA -> darkColorScheme(
            primary = AuroraGreen,
            onPrimary = Color.Black,
            primaryContainer = AuroraGreenDim,
            onPrimaryContainer = Color.White,
            secondary = AuroraPurple,
            onSecondary = Color.Black,
            secondaryContainer = AuroraPurpleDim,
            onSecondaryContainer = Color.White,
            tertiary = AuroraPurple,
            surface = AuroraSurface,
            surfaceVariant = AuroraSurfaceVariant,
            onSurface = AuroraOnSurface,
            onSurfaceVariant = Color(0xFF7AA090),
            background = AuroraBackground,
            onBackground = AuroraOnSurface,
            error = Color(0xFFFF5252),
            outline = Color(0xFF4A6058),
            outlineVariant = Color(0xFF1E3028)
        )

        MIDNIGHT_OCEAN -> darkColorScheme(
            primary = OceanBlue,
            onPrimary = Color.White,
            primaryContainer = OceanBlueDim,
            onPrimaryContainer = Color.White,
            secondary = OceanTeal,
            onSecondary = Color.Black,
            secondaryContainer = OceanTealDim,
            onSecondaryContainer = Color.Black,
            tertiary = OceanTeal,
            surface = OceanSurface,
            surfaceVariant = OceanSurfaceVariant,
            onSurface = OceanOnSurface,
            onSurfaceVariant = Color(0xFF7090A8),
            background = OceanBackground,
            onBackground = OceanOnSurface,
            error = Color(0xFFFF5252),
            outline = Color(0xFF3A5068),
            outlineVariant = Color(0xFF162838)
        )

        SOLAR_FLARE -> darkColorScheme(
            primary = SolarAmber,
            onPrimary = Color.Black,
            primaryContainer = SolarAmberDim,
            onPrimaryContainer = Color.Black,
            secondary = SolarOrange,
            onSecondary = Color.Black,
            secondaryContainer = SolarOrangeDim,
            onSecondaryContainer = Color.White,
            tertiary = SolarOrange,
            surface = SolarSurface,
            surfaceVariant = SolarSurfaceVariant,
            onSurface = SolarOnSurface,
            onSurfaceVariant = Color(0xFFA89070),
            background = SolarBackground,
            onBackground = SolarOnSurface,
            error = Color(0xFFFF5252),
            outline = Color(0xFF685040),
            outlineVariant = Color(0xFF382818)
        )

        VOID -> darkColorScheme(
            primary = VoidWhite,
            onPrimary = Color.Black,
            primaryContainer = VoidGrey,
            onPrimaryContainer = Color.White,
            secondary = VoidAccent,
            onSecondary = Color.Black,
            secondaryContainer = Color(0xFF444444),
            onSecondaryContainer = Color.White,
            tertiary = VoidGrey,
            surface = VoidSurface,
            surfaceVariant = VoidSurfaceVariant,
            onSurface = VoidOnSurface,
            onSurfaceVariant = Color(0xFF808080),
            background = VoidBackground,
            onBackground = VoidOnSurface,
            error = Color(0xFFFF4444),
            outline = Color(0xFF404040),
            outlineVariant = Color(0xFF252525)
        )

        SAKURA_BLOOM -> darkColorScheme(
            primary = SakuraPink,
            onPrimary = Color.Black,
            primaryContainer = SakuraPinkDim,
            onPrimaryContainer = Color.White,
            secondary = SakuraRose,
            onSecondary = Color.Black,
            secondaryContainer = SakuraRoseDim,
            onSecondaryContainer = Color.White,
            tertiary = SakuraRose,
            surface = SakuraSurface,
            surfaceVariant = SakuraSurfaceVariant,
            onSurface = SakuraOnSurface,
            onSurfaceVariant = Color(0xFFA08090),
            background = SakuraBackground,
            onBackground = SakuraOnSurface,
            error = Color(0xFFFF5252),
            outline = Color(0xFF604050),
            outlineVariant = Color(0xFF302028)
        )

        EMERALD_CITY -> darkColorScheme(
            primary = EmeraldGreen,
            onPrimary = Color.Black,
            primaryContainer = EmeraldGreenDim,
            onPrimaryContainer = Color.White,
            secondary = EmeraldGold,
            onSecondary = Color.Black,
            secondaryContainer = EmeraldGoldDim,
            onSecondaryContainer = Color.Black,
            tertiary = EmeraldGold,
            surface = EmeraldSurface,
            surfaceVariant = EmeraldSurfaceVariant,
            onSurface = EmeraldOnSurface,
            onSurfaceVariant = Color(0xFF80A890),
            background = EmeraldBackground,
            onBackground = EmeraldOnSurface,
            error = Color(0xFFFF5252),
            outline = Color(0xFF406850),
            outlineVariant = Color(0xFF1A3020)
        )

        CYBER_VIOLET -> darkColorScheme(
            primary = CyberViolet,
            onPrimary = Color.Black,
            primaryContainer = CyberVioletDim,
            onPrimaryContainer = Color.White,
            secondary = CyberIndigo,
            onSecondary = Color.White,
            secondaryContainer = CyberIndigoDim,
            onSecondaryContainer = Color.White,
            tertiary = CyberIndigo,
            surface = CyberSurface,
            surfaceVariant = CyberSurfaceVariant,
            onSurface = CyberOnSurface,
            onSurfaceVariant = Color(0xFF9080B0),
            background = CyberBackground,
            onBackground = CyberOnSurface,
            error = Color(0xFFFF5252),
            outline = Color(0xFF504068),
            outlineVariant = Color(0xFF201838)
        )

        ARCTIC_ICE -> darkColorScheme(
            primary = ArcticBlue,
            onPrimary = Color.Black,
            primaryContainer = ArcticBlueDim,
            onPrimaryContainer = Color.Black,
            secondary = ArcticWhite,
            onSecondary = Color.Black,
            secondaryContainer = ArcticWhiteDim,
            onSecondaryContainer = Color.Black,
            tertiary = ArcticWhite,
            surface = ArcticSurface,
            surfaceVariant = ArcticSurfaceVariant,
            onSurface = ArcticOnSurface,
            onSurfaceVariant = Color(0xFF80A8B8),
            background = ArcticBackground,
            onBackground = ArcticOnSurface,
            error = Color(0xFFFF5252),
            outline = Color(0xFF385868),
            outlineVariant = Color(0xFF142830)
        )

        MATERIAL_YOU -> darkColorScheme() // Placeholder — replaced at runtime
    }
}
