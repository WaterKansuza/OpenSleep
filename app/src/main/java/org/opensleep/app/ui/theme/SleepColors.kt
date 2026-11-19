package org.opensleep.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.opensleep.app.data.local.db.SleepStage

object SleepPalette {
    val BackgroundDeep     = Color(0xFF000000) // Pure OLED black
    val BackgroundCard     = Color(0xFF0A0A0A)
    val BackgroundElevated = Color(0xFF141414)
    val PrimaryBlue        = Color(0xFFE4E4E7) // Trung tính trắng bạc (thay xanh dương)
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

    // Màu các pha giấc ngủ (không dùng xanh dương)
    val StageAwake  = Color(0xFFFB7185)
    val StageREM    = Color(0xFFC084FC)
    val StageRem    = StageREM
    val StageCore   = Color(0xFFA1A1AA)
    val StageDeep   = Color(0xFFE4E4E7)

    // Debt gradient
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

@Composable
fun OpenSleepTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OpenSleepDarkColorScheme,
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