package org.opensleep.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.opensleep.app.data.local.db.SleepStage

// OLED Dark Palette (Hợp nhất cả biến cũ lẫn biến mới)
object SleepPalette {
    // --- Biến cũ (giữ nguyên để AlarmRingingActivity & các file cũ không lỗi) ---
    val BackgroundDeep    = Color(0xFF000000) // Pure OLED black
    val BackgroundCard    = Color(0xFF070B19)
    val BackgroundElevated = Color(0xFF0D1428)
    val PrimaryBlue       = Color(0xFF4FC3F7)
    val PrimaryBlueDim    = Color(0xFF1565C0)
    val SecondaryGold     = Color(0xFFFFB300)
    val SecondaryGoldDim  = Color(0xFF7C5900)
    val OnBackground      = Color(0xFFE8EAF6)
    val OnBackgroundMuted = Color(0xFF7986CB)
    val OnBackgroundDim   = Color(0xFF3D4B7A)
    val ErrorRed          = Color(0xFFEF5350)
    val SuccessGreen      = Color(0xFF66BB6A)
    val WarningAmber      = Color(0xFFFFA726)

    // --- Biến bổ sung cho MainActivity, TodayScreen, DailySleepDetailScreen, Settings ---
    val BackgroundDark    = Color(0xFF070B19)
    val SurfaceCard       = Color(0xFF11182F)
    val SurfaceElevated   = Color(0xFF1A2342)
    val OledPureBlack     = BackgroundDeep
    val StarGold          = Color(0xFFFDE68A)
    val MoonSilver        = Color(0xFFE0E7FF)
    val OptimalGreen      = Color(0xFF4ADE80)

    // Sleep stage colors (Hypnogram)
    val StageAwake  = Color(0xFFFF6B57)
    val StageREM    = Color(0xFF38BDF8)
    val StageRem    = StageREM // Alias khớp cả chữ hoa lẫn thường
    val StageCore   = Color(0xFF3B82F6)
    val StageDeep   = Color(0xFF6366F1)

    // Debt gradient
    val DebtOptimal  = Color(0xFF66BB6A)
    val DebtMild     = Color(0xFFFFA726)
    val DebtHigh     = Color(0xFFEF6C00)
    val DebtCritical = Color(0xFFB71C1C)
}

val OpenSleepDarkColorScheme = darkColorScheme(
    primary            = SleepPalette.PrimaryBlue,
    onPrimary          = Color(0xFF000000),
    primaryContainer   = SleepPalette.PrimaryBlueDim,
    secondary          = SleepPalette.SecondaryGold,
    onSecondary        = Color(0xFF000000),
    secondaryContainer = SleepPalette.SecondaryGoldDim,
    background         = SleepPalette.BackgroundDark,
    onBackground       = SleepPalette.OnBackground,
    surface            = SleepPalette.SurfaceCard,
    onSurface          = SleepPalette.OnBackground,
    surfaceVariant     = SleepPalette.SurfaceElevated,
    onSurfaceVariant   = SleepPalette.OnBackgroundMuted,
    error              = SleepPalette.ErrorRed,
    onError            = Color.White
)

@Composable
fun OpenSleepTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OpenSleepDarkColorScheme,
        content = content
    )
}

// Đổi nhãn hiển thị sang Awake / REM / Core / Deep
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