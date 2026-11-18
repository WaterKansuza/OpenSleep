package org.opensleep.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.opensleep.app.data.local.db.SleepStage

// OLED Dark Palette
object SleepPalette {
    val BackgroundDeep   = Color(0xFF000000)   // Pure OLED black
    val BackgroundCard   = Color(0xFF070B19)   // Slightly lighter for cards
    val BackgroundElevated = Color(0xFF0D1428) // For elevated surfaces
    val PrimaryBlue      = Color(0xFF4FC3F7)   // Ice blue - main accent
    val PrimaryBlueDim   = Color(0xFF1565C0)
    val SecondaryGold    = Color(0xFFFFB300)   // Gold for NATURAL_NO_ALARM (quality)
    val SecondaryGoldDim = Color(0xFF7C5900)
    val OnBackground     = Color(0xFFE8EAF6)   // Near-white text
    val OnBackgroundMuted= Color(0xFF7986CB)   // Muted text
    val OnBackgroundDim  = Color(0xFF3D4B7A)   // Very dim text
    val ErrorRed         = Color(0xFFEF5350)
    val SuccessGreen     = Color(0xFF66BB6A)
    val WarningAmber     = Color(0xFFFFA726)

    // Sleep stage colors (Hypnogram)
    val StageAwake  = Color(0xFFEF5350)   // Red
    val StageREM    = Color(0xFF42A5F5)   // Blue
    val StageCore   = Color(0xFF5C6BC0)   // Indigo (LIGHT sleep)
    val StageDeep   = Color(0xFF7E57C2)   // Purple (DEEP sleep)

    // Debt gradient
    val DebtOptimal  = Color(0xFF66BB6A)
    val DebtMild     = Color(0xFFFFA726)
    val DebtHigh     = Color(0xFFEF6C00)
    val DebtCritical = Color(0xFFB71C1C)
}

val OpenSleepDarkColorScheme = darkColorScheme(
    primary          = SleepPalette.PrimaryBlue,
    onPrimary        = Color(0xFF000000),
    primaryContainer = SleepPalette.PrimaryBlueDim,
    secondary        = SleepPalette.SecondaryGold,
    onSecondary      = Color(0xFF000000),
    secondaryContainer = SleepPalette.SecondaryGoldDim,
    background       = SleepPalette.BackgroundDeep,
    onBackground     = SleepPalette.OnBackground,
    surface          = SleepPalette.BackgroundCard,
    onSurface        = SleepPalette.OnBackground,
    surfaceVariant   = SleepPalette.BackgroundElevated,
    onSurfaceVariant = SleepPalette.OnBackgroundMuted,
    error            = SleepPalette.ErrorRed,
    onError          = Color.White
)

@Composable
fun OpenSleepTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OpenSleepDarkColorScheme,
        content = content
    )
}

fun SleepStage.displayLabel(): String = when (this) {
    SleepStage.AWAKE -> "Thức"
    SleepStage.LIGHT -> "Ngủ nhẹ"
    SleepStage.DEEP  -> "Ngủ sâu"
    SleepStage.REM   -> "REM"
}

fun SleepStage.stageColor(): Color = when (this) {
    SleepStage.AWAKE -> SleepPalette.StageAwake
    SleepStage.LIGHT -> SleepPalette.StageCore
    SleepStage.DEEP  -> SleepPalette.StageDeep
    SleepStage.REM   -> SleepPalette.StageREM
}
