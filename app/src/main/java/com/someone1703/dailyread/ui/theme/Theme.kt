package com.someone1703.dailyread.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Mint,
    onPrimary = OnMint,
    primaryContainer = MintContainer,
    onPrimaryContainer = OnMintContainer,
    secondary = Sage,
    onSecondary = OnMint,
    secondaryContainer = SageContainer,
    onSecondaryContainer = OnSageContainer,
    tertiary = Honey,
    onTertiary = OnAmberContainer,
    tertiaryContainer = HoneyContainer,
    onTertiaryContainer = OnHoneyContainer,
    background = Night,
    onBackground = Parchment,
    surface = Night,
    onSurface = Parchment,
    surfaceVariant = NightVariant,
    onSurfaceVariant = ParchmentMuted,
    surfaceDim = Night,
    surfaceBright = NightBright,
    surfaceContainerLowest = NightLowest,
    surfaceContainerLow = NightLow,
    surfaceContainer = NightMid,
    surfaceContainerHigh = NightHigh,
    surfaceContainerHighest = NightHighest,
    outline = NightRule,
    outlineVariant = NightRuleSoft,
)

private val LightColorScheme = lightColorScheme(
    primary = InkGreen,
    onPrimary = OnInkGreen,
    primaryContainer = InkGreenContainer,
    onPrimaryContainer = OnInkGreenContainer,
    secondary = Moss,
    onSecondary = OnInkGreen,
    secondaryContainer = MossContainer,
    onSecondaryContainer = OnMossContainer,
    tertiary = Amber,
    onTertiary = OnInkGreen,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperVariant,
    onSurfaceVariant = InkMuted,
    surfaceDim = PaperDim,
    surfaceBright = Paper,
    surfaceContainerLowest = PaperBright,
    surfaceContainerLow = PaperLow,
    surfaceContainer = PaperMid,
    surfaceContainerHigh = PaperHigh,
    surfaceContainerHighest = PaperHighest,
    outline = Rule,
    outlineVariant = RuleSoft,
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

// Dynamic color is left out on purpose: the paper-and-ink palette is the app's identity.
@Composable
fun DailyReadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
