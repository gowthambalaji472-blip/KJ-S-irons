package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = LeafGreenPrimary,
    onPrimary = Color.White,
    primaryContainer = MintWashContainer,
    onPrimaryContainer = DeepPine,
    secondary = SageSecondary,
    onSecondary = Color.White,
    secondaryContainer = SageContainer,
    onSecondaryContainer = DarkSage,
    tertiary = RoseMintTertiary,
    onTertiary = Color.White,
    tertiaryContainer = RoseTintContainer,
    onTertiaryContainer = Color(0xFF3E1020),
    background = PorcelainBackground,
    onBackground = DeepPine,
    surface = PureSurface,
    onSurface = DeepPine,
    surfaceVariant = SoftMintSurface,
    onSurfaceVariant = MutedText,
    outline = HairlineBorder,
    outlineVariant = HairlineBorder,
    error = StatusRejectedFg,
    onError = Color.White,
    errorContainer = StatusRejectedBg,
    onErrorContainer = StatusRejectedFg,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkLeafPrimary,
    onPrimary = DeepPine,
    primaryContainer = DarkLeafContainer,
    onPrimaryContainer = MintWashContainer,
    secondary = LeafGreenLight,
    onSecondary = DeepPine,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = MintWashContainer,
    tertiary = Color(0xFFF4A8BE),
    onTertiary = Color(0xFF4A1828),
    tertiaryContainer = Color(0xFF5D2638),
    onTertiaryContainer = RoseTintContainer,
    background = DarkBg,
    onBackground = Color(0xFFE6F2E7),
    surface = DarkSurface,
    onSurface = Color(0xFFE6F2E7),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFAFC4B1),
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF5C0B0B),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
)

val KjIronsShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun KjIronsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = KjIronsTypography,
        shapes = KjIronsShapes,
        content = content,
    )
}
