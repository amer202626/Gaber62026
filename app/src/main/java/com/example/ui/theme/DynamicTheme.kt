package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.AppThemeType

// Cosmic Silver Colors
val CosmicBackground = Color(0xFF0F1016)
val CosmicSurface = Color(0xFF1E2230)
val CosmicPrimary = Color(0xFFE2E8F0) // Bright Silver
val CosmicSecondary = Color(0xFF38BDF8) // Glowing Sky Blue
val CosmicTertiary = Color(0xFF818CF8) // Indigo Accent

// Gold Luxury Colors
val GoldBackground = Color(0xFF12110F)
val GoldSurface = Color(0xFF1E1C18)
val GoldPrimary = Color(0xFFFFD700) // Pure Gold
val GoldSecondary = Color(0xFFD4AF37) // Metallic Amber
val GoldTertiary = Color(0xFFFFEFA6) // Pale Gold Cream

// Emerald Classic Colors
val EmeraldBackground = Color(0xFF0B1B15)
val EmeraldSurface = Color(0xFF132F25)
val EmeraldPrimary = Color(0xFF10B981) // Pure Emerald Green
val EmeraldSecondary = Color(0xFF34D399) // Mint Accent
val EmeraldTertiary = Color(0xFF6EE7B7) // Bright Aquamarine

@Composable
fun InteractiveYemenTheme(
    themeType: AppThemeType,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeType) {
        AppThemeType.COSMIC_SILVER -> darkColorScheme(
            background = CosmicBackground,
            surface = CosmicSurface,
            primary = CosmicPrimary,
            secondary = CosmicSecondary,
            tertiary = CosmicTertiary,
            onBackground = Color.White,
            onSurface = Color.White,
            onPrimary = Color(0xFF0F1016),
            onSecondary = Color.White,
            onTertiary = Color.White
        )
        AppThemeType.GOLD_LUXURY -> darkColorScheme(
            background = GoldBackground,
            surface = GoldSurface,
            primary = GoldPrimary,
            secondary = GoldSecondary,
            tertiary = GoldTertiary,
            onBackground = Color(0xFFFBF8F3),
            onSurface = Color(0xFFFBF8F3),
            onPrimary = Color(0xFF12110F),
            onSecondary = Color(0xFF12110F),
            onTertiary = Color(0xFF12110F)
        )
        AppThemeType.EMERALD_CLASSIC -> darkColorScheme(
            background = EmeraldBackground,
            surface = EmeraldSurface,
            primary = EmeraldPrimary,
            secondary = EmeraldSecondary,
            tertiary = EmeraldTertiary,
            onBackground = Color.White,
            onSurface = Color.White,
            onPrimary = Color(0xFF002211),
            onSecondary = Color(0xFF002211),
            onTertiary = Color.White
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
