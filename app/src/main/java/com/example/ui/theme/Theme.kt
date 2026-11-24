package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val ColorBorderDarkVariant = androidx.compose.ui.graphics.Color(0xFF232328)

private val MonoDarkColorScheme = darkColorScheme(
    primary = MonoWhite,
    onPrimary = MonoBlack,
    primaryContainer = MonoSurfaceElevatedDark,
    onPrimaryContainer = MonoWhite,
    secondary = MonoTextSecondaryDark,
    onSecondary = MonoBlack,
    secondaryContainer = MonoSurfaceDark,
    onSecondaryContainer = MonoTextPrimaryDark,
    tertiary = MonoPillDark,
    onTertiary = MonoWhite,
    background = MonoBlack,
    onBackground = MonoTextPrimaryDark,
    surface = MonoSurfaceDark,
    onSurface = MonoTextPrimaryDark,
    surfaceVariant = MonoSurfaceElevatedDark,
    onSurfaceVariant = MonoTextSecondaryDark,
    outline = MonoBorderDark,
    outlineVariant = ColorBorderDarkVariant,
    error = MonoError,
    onError = MonoWhite,
)

private val MonoLightColorScheme = lightColorScheme(
    primary = MonoTextPrimaryLight,
    onPrimary = MonoWhite,
    primaryContainer = MonoSurfaceElevatedLight,
    onPrimaryContainer = MonoTextPrimaryLight,
    secondary = MonoTextSecondaryLight,
    onSecondary = MonoWhite,
    secondaryContainer = MonoSurfaceLight,
    onSecondaryContainer = MonoTextPrimaryLight,
    tertiary = MonoPillLight,
    onTertiary = MonoTextPrimaryLight,
    background = MonoOffWhite,
    onBackground = MonoTextPrimaryLight,
    surface = MonoSurfaceLight,
    onSurface = MonoTextPrimaryLight,
    surfaceVariant = MonoSurfaceElevatedLight,
    onSurfaceVariant = MonoTextSecondaryLight,
    outline = MonoBorderLight,
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFD2D2DA),
    error = MonoError,
    onError = MonoWhite,
)

val MonoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun MonoDocsTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) MonoDarkColorScheme else MonoLightColorScheme
    val extendedColors = if (darkTheme) DarkMonoExtendedColors else LightMonoExtendedColors

    CompositionLocalProvider(LocalMonoColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MonoTypography,
            shapes = MonoShapes,
            content = content,
        )
    }
}
