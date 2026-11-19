package org.opensleep.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.NotificationsOff
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
import org.opensleep.app.ui.theme.SleepPalette
import org.opensleep.app.ui.theme.displayLabel
import org.opensleep.app.data.local.db.entity.SleepNoteEntity
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    uiState: TodayUiState,
    liveWaveFlow: Flow<Float>,
    onSelectSleepMode: (TodaySleepModeOption) -> Unit,
    onUpdateWakeTime: (Int, Int) -> Unit,
    onToggleSmartWake: (Boolean) -> Unit,
    onStartSleep: () -> Unit,
    onStopTracking: () -> Unit,
    onQuickFeeling: (Long, Int) -> Unit,
    onSubmitCheckIn: (Long, Int, Set<Long>, String) -> Unit,
    onDismissCheckIn: () -> Unit,
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

    var showTimePicker by remember { mutableStateOf(false) }
    val rec = uiState.recommendation
    val wakeHour = (rec?.targetWakeMinutesOfDay ?: 405) / 60
    val wakeMinute = (rec?.targetWakeMinutesOfDay ?: 405) % 60

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

        // 1. Giờ ngủ đề xuất & Giờ thức dậy mục tiêu
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
                Text(
                    text = if (uiState.selectedSleepMode == TodaySleepModeOption.NAP) {
                        "Gợi ý nghỉ ngắn"
                    } else {
                        "Giờ ngủ đề xuất"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = SleepPalette.OnBackgroundMuted
                )

                Text(
                    text = if (uiState.selectedSleepMode == TodaySleepModeOption.NAP) {
                        "25 phút"
                    } else {
                        rec?.recommendedBedtimeLabel ?: "22:30"
                    },
                    fontSize = 48.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SleepPalette.OnBackground
                )

                val needMin = rec?.personalSleepNeedMinutes ?: 480
                val latencyMin = rec?.estimatedLatencyMinutes ?: 15
                val latencySource = if (rec?.isLatencyEstimatedDefault != false) "Ước tính" else "Lịch sử thật"
                Text(
                    text = if (uiState.selectedSleepMode == TodaySleepModeOption.NAP) {
                        "Power Nap ngắn giúp giảm mệt mỏi mà không vào sâu chu kỳ ngủ."
                    } else {
                        "Mục tiêu ${needMin / 60}h ${needMin % 60}p · Độ trễ ~${latencyMin}p (${latencySource})"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SleepPalette.OnBackgroundMuted
                )

                rec?.debtRecoverySuggestion?.let { suggestion ->
                    if (uiState.selectedSleepMode != TodaySleepModeOption.NAP) {
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
                }

                if (uiState.selectedSleepMode != TodaySleepModeOption.NAP) {
                    Surface(
                        onClick = { showTimePicker = true },
                        color = SleepPalette.BackgroundCard,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (rec?.isWeekendScheduleApplied == true) {
                                        "Giờ thức dậy (Cuối tuần)"
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
                                text = "Chạm để đổi",
                                style = MaterialTheme.typography.labelMedium,
                                color = SleepPalette.StageRem
                            )
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

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = wakeHour,
            initialMinute = wakeMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateWakeTime(pickerState.hour, pickerState.minute)
                    showTimePicker = false
                }) { Text("Xác nhận") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Hủy") }
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