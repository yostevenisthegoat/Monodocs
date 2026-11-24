package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Mono Ecosystem Monochrome Palette
val MonoBlack = Color(0xFF09090B)
val MonoObsidian = Color(0xFF0E0E10)
val MonoSurfaceDark = Color(0xFF17171A)
val MonoSurfaceElevatedDark = Color(0xFF202024)
val MonoBorderDark = Color(0xFF2C2C32)
val MonoPillDark = Color(0xFF4A4A50)

val MonoWhite = Color(0xFFFFFFFF)
val MonoOffWhite = Color(0xFFF6F6F8)
val MonoSurfaceLight = Color(0xFFFFFFFF)
val MonoSurfaceElevatedLight = Color(0xFFEEEEF2)
val MonoBorderLight = Color(0xFFE0E0E6)
val MonoPillLight = Color(0xFFDADAE0)

val MonoTextPrimaryDark = Color(0xFFF5F5F7)
val MonoTextSecondaryDark = Color(0xFFA0A0A8)
val MonoTextMutedDark = Color(0xFF6E6E76)

val MonoTextPrimaryLight = Color(0xFF111114)
val MonoTextSecondaryLight = Color(0xFF5A5A64)
val MonoTextMutedLight = Color(0xFF8A8A94)

// Subtle semantic badges matching the reference screenshot (e.g. PDF red icon badge)
val BadgePdfRed = Color(0xFFD93025)
val BadgeDocBlue = Color(0xFF2B6CB0)
val BadgeSheetGreen = Color(0xFF25855A)
val BadgeSlideAmber = Color(0xFFD97706)
val BadgeImageViolet = Color(0xFF6B46C1)
val BadgeVideoSlate = Color(0xFF334155)
val BadgeAudioTeal = Color(0xFF0D9488)
val BadgeArchiveBronze = Color(0xFF92400E)
val BadgeEbookIndigo = Color(0xFF4338CA)
val BadgeCodeZinc = Color(0xFF3F3F46)
val Badge3DCyan = Color(0xFF0284C7)
val BadgeFontCharcoal = Color(0xFF27272A)

val MonoSuccess = Color(0xFF22C55E)
val MonoWarning = Color(0xFFF59E0B)
val MonoError = Color(0xFFEF4444)

@Immutable
data class MonoExtendedColors(
    val isDark: Boolean,
    val background: Color,
    val wavePrimary: Color,
    val waveSecondary: Color,
    val cardSurface: Color,
    val cardSurfaceElevated: Color,
    val cardBorder: Color,
    val navBarSurface: Color,
    val pillButton: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val iconTint: Color,
)

val DarkMonoExtendedColors = MonoExtendedColors(
    isDark = true,
    background = MonoBlack,
    wavePrimary = Color(0xFF18181C),
    waveSecondary = Color(0xFF121215),
    cardSurface = MonoSurfaceDark,
    cardSurfaceElevated = MonoSurfaceElevatedDark,
    cardBorder = MonoBorderDark,
    navBarSurface = Color(0xFF141417),
    pillButton = MonoPillDark,
    textPrimary = MonoTextPrimaryDark,
    textSecondary = MonoTextSecondaryDark,
    textMuted = MonoTextMutedDark,
    iconTint = MonoWhite,
)

val LightMonoExtendedColors = MonoExtendedColors(
    isDark = false,
    background = MonoOffWhite,
    wavePrimary = Color(0xFFEBEBF0),
    waveSecondary = Color(0xFFF1F1F5),
    cardSurface = MonoSurfaceLight,
    cardSurfaceElevated = MonoSurfaceElevatedLight,
    cardBorder = MonoBorderLight,
    navBarSurface = Color(0xFFFFFFFF),
    pillButton = MonoPillLight,
    textPrimary = MonoTextPrimaryLight,
    textSecondary = MonoTextSecondaryLight,
    textMuted = MonoTextMutedLight,
    iconTint = MonoTextPrimaryLight,
)

val LocalMonoColors = staticCompositionLocalOf { DarkMonoExtendedColors }
