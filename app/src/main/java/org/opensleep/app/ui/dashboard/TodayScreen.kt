package org.opensleep.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import org.opensleep.app.core.util.AppUpdateInfo
import org.opensleep.app.data.local.db.entity.SleepNoteEntity
import org.opensleep.app.ui.theme.SleepPalette
import org.opensleep.app.ui.theme.displayLabel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    uiState: TodayUiState,
    liveWaveFlow: Flow<Float>,
    onSelectSleepMode: (TodaySleepModeOption) -> Unit,
    onUpdateBedtime: (Int, Int) -> Unit,
    onResetBedtime: () -> Unit,
    onUpdateWakeTime: (Int, Int) -> Unit,
    onToggleSmartWake: (Boolean) -> Unit,
    onStartSleep: () -> Unit,
    onStopTracking: () -> Unit,
    onQuickFeeling: (Long, Int) -> Unit,
    onSubmitCheckIn: (Long, Int, Set<Long>, String) -> Unit,
    onDismissCheckIn: () -> Unit,
    availableUpdate: AppUpdateInfo? = null,
    onOpenUpdateSettings: () -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (uiState.tracking.isTracking) {
        NightstandTrackingOverlay(
            uiState = uiState,
            liveWaveFlow = liveWaveFlow,
            onStopTracking = onStopTracking
        )
        return
    }

    var showWakePicker by remember { mutableStateOf(false) }
    var showBedtimePicker by remember { mutableStateOf(false) }

    val rec = uiState.recommendation
    val bedTotalMin = rec?.recommendedBedtimeMinutesOfDay ?: (22 * 60 + 30)
    val bedHour = bedTotalMin / 60
    val bedMinute = bedTotalMin % 60
    val wakeTotalMin = rec?.targetWakeMinutesOfDay ?: (6 * 60 + 45)
    val wakeHour = wakeTotalMin / 60
    val wakeMinute = wakeTotalMin % 60

    fun shiftClock(totalMin: Int, delta: Int, apply: (Int, Int) -> Unit) {
        val wrapped = ((totalMin + delta) % 1440 + 1440) % 1440
        apply(wrapped / 60, wrapped % 60)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SleepPalette.BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header tối giản
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "OpenSleep",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = SleepPalette.OnBackground
                )
                Text(
                    text = "Chuẩn bị giấc ngủ",
                    style = MaterialTheme.typography.labelMedium,
                    color = SleepPalette.OnBackgroundMuted
                )
            }
            Icon(
                imageVector = Icons.Rounded.NightsStay,
                contentDescription = null,
                tint = SleepPalette.StageRem,
                modifier = Modifier.size(26.dp)
            )
        }

        // Banner bản cập nhật GitHub Release (chỉ hiện khi có bản mới và chưa ẩn)
        availableUpdate?.let { upd ->
            Surface(
                color = SleepPalette.SurfaceElevated,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, SleepPalette.StageRem.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = SleepPalette.StageRem)
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Có bản cập nhật ${upd.latestTag}",
                            color = SleepPalette.OnBackground,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Cập nhật trực tiếp không mất dữ liệu",
                            color = SleepPalette.OnBackgroundMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    TextButton(onClick = onOpenUpdateSettings) {
                        Text("Cài đặt", color = SleepPalette.StageRem)
                    }
                    TextButton(onClick = onDismissUpdate) {
                        Text("Ẩn", color = SleepPalette.OnBackgroundMuted)
                    }
                }
            }
        }

        // 1. Giờ đi ngủ & Giờ thức dậy (chỉnh tự do từng 1 phút)
        Card(
            colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            uiState.selectedSleepMode == TodaySleepModeOption.NAP -> "Gợi ý nghỉ ngắn"
                            rec?.isCustomBedtimeActive == true -> "Giờ đi ngủ đã chọn"
                            else -> "Giờ đi ngủ đề xuất"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = SleepPalette.OnBackgroundMuted
                    )
                    if (uiState.selectedSleepMode != TodaySleepModeOption.NAP && rec?.isCustomBedtimeActive == true) {
                        TextButton(
                            onClick = onResetBedtime,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Về đề xuất", style = MaterialTheme.typography.labelSmall, color = SleepPalette.StageRem)
                        }
                    }
                }

                if (uiState.selectedSleepMode == TodaySleepModeOption.NAP) {
                    Text(
                        text = "25 phút",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SleepPalette.OnBackground
                    )
                    Text(
                        text = "Power Nap ngắn giúp giảm mệt mỏi mà không vào sâu chu kỳ ngủ.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SleepPalette.OnBackgroundMuted
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showBedtimePicker = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = String.format("%02d:%02d", bedHour, bedMinute),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SleepPalette.OnBackground
                        )
                        Text(
                            text = "Chạm đặt phút",
                            style = MaterialTheme.typography.labelMedium,
                            color = SleepPalette.StageRem,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }

                    // Nút tăng giảm chính xác từng 1 phút hoặc 15 phút cho Giờ đi ngủ
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(-15 to "-15p", -1 to "-1p", 1 to "+1p", 15 to "+15p").forEach { (delta, label) ->
                            OutlinedButton(
                                onClick = { shiftClock(bedTotalMin, delta, onUpdateBedtime) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(34.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = SleepPalette.OnBackground)
                            }
                        }
                    }

                    val plannedMin = rec?.plannedSleepMinutes ?: 480
                    val needMin = rec?.personalSleepNeedMinutes ?: 480
                    val latencyMin = rec?.estimatedLatencyMinutes ?: 15
                    Text(
                        text = "Dự kiến ngủ ${plannedMin / 60}h ${plannedMin % 60}p · Mục tiêu ${needMin / 60}h ${needMin % 60}p · Độ trễ ~${latencyMin}p",
                        style = MaterialTheme.typography.bodySmall,
                        color = SleepPalette.OnBackgroundMuted
                    )

                    rec?.debtRecoverySuggestion?.let { suggestion ->
                        Surface(
                            color = SleepPalette.SurfaceElevated,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, SleepPalette.StarGold.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = suggestion,
                                color = SleepPalette.StarGold,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }

                    // Giờ thức dậy mục tiêu + chỉnh từng phút
                    Surface(
                        color = SleepPalette.BackgroundCard,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showWakePicker = true },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (rec?.isWeekendScheduleApplied == true) {
                                            "Giờ thức dậy (Ngày nghỉ)"
                                        } else {
                                            "Giờ thức dậy mục tiêu"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SleepPalette.OnBackgroundMuted
                                    )
                                    Text(
                                        text = String.format("%02d:%02d", wakeHour, wakeMinute),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = SleepPalette.OnBackground
                                    )
                                }
                                Text(
                                    text = "Chạm đặt phút",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SleepPalette.StageRem
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(-15 to "-15p", -1 to "-1p", 1 to "+1p", 15 to "+15p").forEach { (delta, label) ->
                                    OutlinedButton(
                                        onClick = { shiftClock(wakeTotalMin, delta, onUpdateWakeTime) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).height(32.dp)
                                    ) {
                                        Text(label, style = MaterialTheme.typography.labelSmall, color = SleepPalette.OnBackgroundMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Chọn chế độ ngủ & Nút Bắt đầu ngủ
        Card(
            colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Chọn chế độ ngủ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SleepPalette.OnBackground
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SleepModeOptionTile(
                        title = "Có báo thức",
                        subtitle = if (uiState.smartWakeEnabled) "Smart Wake" else "Đúng giờ",
                        icon = Icons.Rounded.Alarm,
                        accent = SleepPalette.StageRem,
                        selected = uiState.selectedSleepMode == TodaySleepModeOption.ALARM,
                        onClick = { onSelectSleepMode(TodaySleepModeOption.ALARM) },
                        modifier = Modifier.weight(1f)
                    )
                    SleepModeOptionTile(
                        title = "Không chuông",
                        subtitle = "Dậy tự nhiên",
                        icon = Icons.Rounded.NotificationsOff,
                        accent = SleepPalette.OptimalGreen,
                        selected = uiState.selectedSleepMode == TodaySleepModeOption.NO_ALARM,
                        onClick = { onSelectSleepMode(TodaySleepModeOption.NO_ALARM) },
                        modifier = Modifier.weight(1f)
                    )
                    SleepModeOptionTile(
                        title = "Ngủ trưa",
                        subtitle = "Nap 25p",
                        icon = Icons.Rounded.Coffee,
                        accent = SleepPalette.StarGold,
                        selected = uiState.selectedSleepMode == TodaySleepModeOption.NAP,
                        onClick = { onSelectSleepMode(TodaySleepModeOption.NAP) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (uiState.selectedSleepMode == TodaySleepModeOption.ALARM) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Smart Wake", color = SleepPalette.OnBackground, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Đánh thức nhẹ trong cửa sổ ${uiState.effectiveSmartWindowMin}p",
                                color = SleepPalette.OnBackgroundMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Switch(checked = uiState.smartWakeEnabled, onCheckedChange = onToggleSmartWake)
                    }
                }

                OutlinedButton(
                    onClick = onStartSleep,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SleepPalette.SurfaceElevated,
                        contentColor = SleepPalette.OnBackground
                    )
                ) {
                    Text(
                        text = when (uiState.selectedSleepMode) {
                            TodaySleepModeOption.NAP -> "Bắt đầu ngủ trưa"
                            TodaySleepModeOption.ALARM -> "Bắt đầu ngủ · Hẹn " + String.format("%02d:%02d", wakeHour, wakeMinute)
                            TodaySleepModeOption.NO_ALARM -> "Bắt đầu ngủ · Không chuông"
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        // 3. Sau khi dậy: mặc định tắt popup bắt buộc, chỉ hiện thanh chọn nhanh tùy chọn
        uiState.pendingCheckInSessionId?.let { sessionId ->
            if (!uiState.optionalCheckInEnabled) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Phiên ngủ vừa được lưu",
                            color = SleepPalette.OnBackground,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onQuickFeeling(sessionId, 5) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Tôi thấy khỏe", color = SleepPalette.OptimalGreen)
                            }
                            OutlinedButton(
                                onClick = { onQuickFeeling(sessionId, 2) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Tôi vẫn mệt", color = SleepPalette.StarGold)
                            }
                        }
                        TextButton(
                            onClick = onDismissCheckIn,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Bỏ qua", color = SleepPalette.OnBackgroundMuted)
                        }
                    }
                }
            } else {
                MorningCheckInDialog(
                    notes = uiState.availableNotes,
                    onSubmit = { score, noteIds, dream -> onSubmitCheckIn(sessionId, score, noteIds, dream) },
                    onDismiss = onDismissCheckIn
                )
            }
        }
    }

    if (showBedtimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = bedHour,
            initialMinute = bedMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showBedtimePicker = false },
            title = { Text("Chọn giờ đi ngủ (từng phút)") },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateBedtime(pickerState.hour, pickerState.minute)
                    showBedtimePicker = false
                }) { Text("Xác nhận") }
            },
            dismissButton = {
                TextButton(onClick = { showBedtimePicker = false }) { Text("Hủy") }
            },
            text = { TimePicker(state = pickerState) }
        )
    }

    if (showWakePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = wakeHour,
            initialMinute = wakeMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showWakePicker = false },
            title = { Text("Chọn giờ thức dậy (từng phút)") },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateWakeTime(pickerState.hour, pickerState.minute)
                    showWakePicker = false
                }) { Text("Xác nhận") }
            },
            dismissButton = {
                TextButton(onClick = { showWakePicker = false }) { Text("Hủy") }
            },
            text = { TimePicker(state = pickerState) }
        )
    }
}

@Composable
private fun SleepModeOptionTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) SleepPalette.SurfaceElevated else SleepPalette.BackgroundCard,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) accent.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) accent else SleepPalette.OnBackgroundMuted,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                color = SleepPalette.OnBackground,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = subtitle,
                color = SleepPalette.OnBackgroundMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@Composable
fun NightstandTrackingOverlay(
    uiState: TodayUiState,
    liveWaveFlow: Flow<Float>,
    onStopTracking: () -> Unit
) {
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    val waveSamples = remember { mutableStateListOf<Float>() }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(1000L)
        }
    }

    LaunchedEffect(uiState.tracking.isTracking) {
        liveWaveFlow.collect { s ->
            waveSamples.add(s)
            if (waveSamples.size > 60) waveSamples.removeAt(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SleepPalette.OledPureBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${uiState.tracking.trackingMode.name} · ${uiState.tracking.wakeMode.name}",
            color = SleepPalette.OnBackgroundDim,
            style = MaterialTheme.typography.labelSmall
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = currentTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                fontSize = 68.sp,
                fontWeight = FontWeight.Thin,
                color = SleepPalette.OnBackgroundMuted
            )
            Text(
                text = "Giai đoạn: ${uiState.tracking.currentStage.displayLabel()}",
                color = SleepPalette.OnBackgroundMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            uiState.tracking.latestBreathingBpm?.let { bpm ->
                Text(
                    text = "Nhịp thở: ${String.format("%.1f", bpm)} lần/phút",
                    color = SleepPalette.StageRem,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (waveSamples.size > 4) {
                Canvas(modifier = Modifier.fillMaxWidth(0.7f).height(44.dp)) {
                    val path = Path()
                    val maxAbs = maxOf(0.05f, waveSamples.maxOf { abs(it) })
                    val step = size.width / (waveSamples.size - 1)
                    waveSamples.forEachIndexed { idx, v ->
                        val y = size.height / 2f - (v / maxAbs) * (size.height * 0.42f)
                        if (idx == 0) path.moveTo(0f, y) else path.lineTo(idx * step, y)
                    }
                    drawPath(
                        path = path,
                        color = SleepPalette.StageRem.copy(alpha = 0.5f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onStopTracking,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, SleepPalette.OnBackgroundDim),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SleepPalette.OnBackgroundMuted)
        ) {
            Text("Thức dậy & Kết thúc theo dõi", fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MorningCheckInDialog(
    notes: List<SleepNoteEntity>,
    onSubmit: (Int, Set<Long>, String) -> Unit,
    onDismiss: () -> Unit
) {
    var energyScore by remember { mutableStateOf(4) }
    val selectedNotes = remember { mutableStateListOf<Long>() }
    var dreamText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chào buổi sáng! Bạn thấy thế nào?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mức độ tỉnh táo: $energyScore/5")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    (1..5).forEach { score ->
                        FilterChip(
                            selected = energyScore == score,
                            onClick = { energyScore = score },
                            label = { Text("$score") }
                        )
                    }
                }
                Text("Thói quen tối qua:", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    notes.forEach { note ->
                        val checked = note.id in selectedNotes
                        FilterChip(
                            selected = checked,
                            onClick = {
                                if (checked) selectedNotes.remove(note.id) else selectedNotes.add(note.id)
                            },
                            label = { Text(note.name, maxLines = 1) }
                        )
                    }
                }
                OutlinedTextField(
                    value = dreamText,
                    onValueChange = { dreamText = it },
                    label = { Text("Ghi nhanh giấc mơ (tùy chọn)") },
                    singleLine = false,
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(energyScore, selectedNotes.toSet(), dreamText) }) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Bỏ qua") }
        }
    )
}