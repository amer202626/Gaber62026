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

// Smoky Black Colors
val SmokyBackground = Color(0xFF121212)
val SmokySurface = Color(0xFF1E1E1E)
val SmokyPrimary = Color(0xFFE0E0E0)
val SmokySecondary = Color(0xFF9E9E9E)
val SmokyTertiary = Color(0xFF616161)

// Light Pink Colors
val PinkBackground = Color(0xFF221115)
val PinkSurface = Color(0xFF351B22)
val PinkPrimary = Color(0xFFFFB7C5)
val PinkSecondary = Color(0xFFF472B6)
val PinkTertiary = Color(0xFFFDA4AF)

// Golden White Colors
val GoldenWhiteBackground = Color(0xFF1E1E24)
val GoldenWhiteSurface = Color(0xFF2A2A32)
val GoldenWhitePrimary = Color(0xFFFDFDFD)
val GoldenWhiteSecondary = Color(0xFFFFD700)
val GoldenWhiteTertiary = Color(0xFFF1E4C3)

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
        AppThemeType.SMOKY_BLACK -> darkColorScheme(
            background = SmokyBackground,
            surface = SmokySurface,
            primary = SmokyPrimary,
            secondary = SmokySecondary,
            tertiary = SmokyTertiary,
            onBackground = Color.White,
            onSurface = Color.White,
            onPrimary = Color(0xFF121212),
            onSecondary = Color.White,
            onTertiary = Color.White
        )
        AppThemeType.LIGHT_PINK -> darkColorScheme(
            background = PinkBackground,
            surface = PinkSurface,
            primary = PinkPrimary,
            secondary = PinkSecondary,
            tertiary = PinkTertiary,
            onBackground = Color.White,
            onSurface = Color.White,
            onPrimary = Color(0xFF221115),
            onSecondary = Color.White,
            onTertiary = Color.White
        )
        AppThemeType.GOLDEN_WHITE -> darkColorScheme(
            background = GoldenWhiteBackground,
            surface = GoldenWhiteSurface,
            primary = GoldenWhitePrimary,
            secondary = GoldenWhiteSecondary,
            tertiary = GoldenWhiteTertiary,
            onBackground = Color.White,
            onSurface = Color.White,
            onPrimary = Color(0xFF1E1E24),
            onSecondary = Color(0xFF1E1E24),
            onTertiary = Color.White
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
