package org.opensleep.app.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import org.opensleep.app.data.local.db.SleepStage

object SleepPalette {
    val BackgroundDeep     = Color(0xFF000000)
    val BackgroundCard     = Color(0xFF0A0A0A)
    val BackgroundElevated = Color(0xFF141414)
    val PrimaryBlue        = Color(0xFFE4E4E7)
    val PrimaryBlueDim     = Color(0xFF27272A)
    val SecondaryGold      = Color(0xFFF59E0B)
    val SecondaryGoldDim   = Color(0xFF451A03)
    val OnBackground       = Color(0xFFF4F4F5)
    val OnBackgroundMuted  = Color(0xFFA1A1AA)
    val OnBackgroundDim    = Color(0xFF52525B)
    val ErrorRed           = Color(0xFFFB7185)
    val SuccessGreen       = Color(0xFF4ADE80)
    val WarningAmber       = Color(0xFFFBBF24)

    val BackgroundDark     = Color(0xFF0A0A0A)
    val SurfaceCard        = Color(0xFF141414)
    val SurfaceElevated    = Color(0xFF1E1E1E)
    val OledPureBlack      = BackgroundDeep
    val StarGold           = Color(0xFFFDE68A)
    val MoonSilver         = Color(0xFFE4E4E7)
    val OptimalGreen       = Color(0xFF4ADE80)

    val StageAwake  = Color(0xFFFB7185)
    val StageREM    = Color(0xFFC084FC)
    val StageRem    = StageREM
    val StageCore   = Color(0xFFA1A1AA)
    val StageDeep   = Color(0xFFE4E4E7)

    val DebtOptimal  = Color(0xFF4ADE80)
    val DebtMild     = Color(0xFFFBBF24)
    val DebtHigh     = Color(0xFFF97316)
    val DebtCritical = Color(0xFFFB7185)
}

val OpenSleepDarkColorScheme = darkColorScheme(
    primary            = SleepPalette.PrimaryBlue,
    onPrimary          = Color(0xFF0A0A0A),
    primaryContainer   = SleepPalette.PrimaryBlueDim,
    secondary          = SleepPalette.SecondaryGold,
    onSecondary        = Color(0xFF0A0A0A),
    secondaryContainer = SleepPalette.SecondaryGoldDim,
    background         = SleepPalette.BackgroundDark,
    onBackground       = SleepPalette.OnBackground,
    surface            = SleepPalette.SurfaceCard,
    onSurface          = SleepPalette.OnBackground,
    surfaceVariant     = SleepPalette.SurfaceElevated,
    onSurfaceVariant   = SleepPalette.OnBackgroundMuted,
    error              = SleepPalette.ErrorRed,
    onError            = Color(0xFF0A0A0A)
)

fun resolveFontFamily(fontKey: String): FontFamily = when (fontKey) {
    "be_vietnam_pro" -> FontFamily(Typeface.create("sans-serif", Typeface.NORMAL))
    "nunito"         -> FontFamily(Typeface.create("sans-serif-light", Typeface.NORMAL))
    "serif"          -> FontFamily.Serif
    else             -> FontFamily.SansSerif
}

fun buildOpenSleepTypography(fontKey: String): Typography {
    val family = resolveFontFamily(fontKey)
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family)
    )
}

@Composable
fun OpenSleepTheme(
    fontKey: String = "sans",
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OpenSleepDarkColorScheme,
        typography = buildOpenSleepTypography(fontKey),
        content = content
    )
}

fun SleepStage.displayLabel(): String = when (this) {
    SleepStage.AWAKE -> "Awake"
    SleepStage.REM   -> "REM"
    SleepStage.LIGHT -> "Core"
    SleepStage.DEEP  -> "Deep"
}

fun SleepStage.stageColor(): Color = when (this) {
    SleepStage.AWAKE -> SleepPalette.StageAwake
    SleepStage.REM   -> SleepPalette.StageRem
    SleepStage.LIGHT -> SleepPalette.StageCore
    SleepStage.DEEP  -> SleepPalette.StageDeep
}