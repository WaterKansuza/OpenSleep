package org.opensleep.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import org.opensleep.app.data.local.db.DebtStatusLevel
import org.opensleep.app.data.local.db.TrackingMode
import org.opensleep.app.data.local.db.entity.SleepNoteEntity
import org.opensleep.app.domain.model.CircadianForecast
import org.opensleep.app.domain.model.SleepDebtAnalysis
import org.opensleep.app.ui.theme.SleepPalette
import org.opensleep.app.ui.theme.displayLabel
import org.opensleep.app.ui.theme.stageColor
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    uiState: TodayUiState,
    liveWaveFlow: Flow<Float>,
    onToggleAlarmMode: (Boolean) -> Unit,
    onSetAlarmTime: (Int, Int) -> Unit,
    onToggleSmartWake: (Boolean) -> Unit,
    onStartNightSleep: () -> Unit,
    onStartPowerNap: () -> Unit,
    onStopTracking: () -> Unit,
    onSubmitCheckIn: (Long, Int, Set<Long>, String) -> Unit,
    onDismissCheckIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Nếu đang theo dõi giấc ngủ ban đêm -> Hiện màn hình Nightstand OLED siêu tối
    if (uiState.tracking.isTracking) {
        NightstandTrackingOverlay(
            uiState = uiState,
            liveWaveFlow = liveWaveFlow,
            onStopTracking = onStopTracking
        )
        return
    }

    var showTimePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SleepPalette.BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                    color = Color.White
                )
                Text(
                    text = "100% Miễn phí • Zero-Internet",
                    style = MaterialTheme.typography.labelSmall,
                    color = SleepPalette.OptimalGreen
                )
            }
            Icon(
                imageVector = Icons.Rounded.NightsStay,
                contentDescription = null,
                tint = SleepPalette.StarGold,
                modifier = Modifier.size(28.dp)
            )
        }

        uiState.analysis?.let { analysis ->
            // 1. Thẻ Nợ ngủ & Thanh mô phỏng What-if
            SleepDebtHeroWithSimulatorCard(analysis = analysis)

            // 2. Dự báo Nhịp sinh học Process C
            CircadianTimelineCard(forecast = analysis.circadianForecast)
        }

        // 3. Thẻ chọn 2 chế độ: Có Báo thức vs Không Báo thức
        BedtimeSelectorCard(
            bedtime = uiState.bedtime,
            effectiveSmartWindowMin = uiState.effectiveSmartWindowMin,
            onToggleAlarmMode = onToggleAlarmMode,
            onOpenTimePicker = { showTimePicker = true },
            onToggleSmartWake = onToggleSmartWake,
            onStartNightSleep = onStartNightSleep,
            onStartPowerNap = onStartPowerNap
        )
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = uiState.bedtime.alarmHour,
            initialMinute = uiState.bedtime.alarmMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onSetAlarmTime(pickerState.hour, pickerState.minute)
                    showTimePicker = false
                }) { Text("Xác nhận") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Hủy") }
            },
            text = { TimePicker(state = pickerState) }
        )
    }

    // Hộp thoại Chào buổi sáng 5 giây (tự mở khi vừa tắt tracking)
    uiState.pendingCheckInSessionId?.let { sessionId ->
        MorningCheckInDialog(
            notes = uiState.availableNotes,
            onSubmit = { score, noteIds, dream -> onSubmitCheckIn(sessionId, score, noteIds, dream) },
            onDismiss = onDismissCheckIn
        )
    }
}

@Composable
fun SleepDebtHeroWithSimulatorCard(analysis: SleepDebtAnalysis) {
    val statusColor = when (analysis.statusLevel) {
        DebtStatusLevel.OPTIMAL -> SleepPalette.OptimalGreen
        DebtStatusLevel.MILD -> SleepPalette.StarGold
        DebtStatusLevel.HIGH -> Color(0xFFFB923C)
        DebtStatusLevel.CRITICAL -> SleepPalette.StageAwake
    }
    val hours = analysis.cumulativeDebtMinutes / 60
    val mins = analysis.cumulativeDebtMinutes % 60
    val sweep = (analysis.cumulativeDebtMinutes / 600f).coerceIn(0.06f, 1f)

    var extraSleepTonightMin by remember { mutableStateOf(30f) }
    val napTomorrowMin = if (analysis.recommendedNapMinutes > 0) 20f else 0f

    val simulatedRecovery = ((extraSleepTonightMin * 0.85f) + (napTomorrowMin * 0.85f))
        .roundToInt()
        .coerceAtMost(90)
    val projectedDebt = max(0, analysis.cumulativeDebtMinutes - simulatedRecovery)

    Card(
        colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(112.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                        drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, style = stroke)
                        drawArc(statusColor, -90f, 360f * sweep, false, style = stroke)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${hours}h ${mins}p",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text("Nợ ngủ", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Mức: ${analysis.statusLevel.name}", color = statusColor, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Nhu cầu cơ thể: ${analysis.activeSleepNeedMinutes / 60}h ${analysis.activeSleepNeedMinutes % 60}p/đêm",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (analysis.recommendedNapMinutes > 0) {
                        Text(
                            text = "Nên ngủ trưa ${analysis.recommendedNapMinutes}p đầu giờ chiều",
                            color = SleepPalette.StageRem,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            if (analysis.cumulativeDebtMinutes > 0) {
                Surface(
                    color = SleepPalette.SurfaceElevated,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Mô phỏng trả nợ: Tối nay ngủ dư +${extraSleepTonightMin.toInt()}p & Nap ${napTomorrowMin.toInt()}p",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Slider(
                            value = extraSleepTonightMin,
                            onValueChange = { extraSleepTonightMin = it },
                            valueRange = 0f..90f,
                            steps = 5
                        )
                        Text(
                            text = "Dự kiến nợ ngủ còn lại: ${projectedDebt / 60}h ${projectedDebt % 60}p",
                            color = SleepPalette.OptimalGreen,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CircadianTimelineCard(forecast: CircadianForecast) {
    fun fmt(m: Int): String = String.format("%02d:%02d", m / 60, m % 60)
    Card(
        colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Nhịp sinh học hôm nay (Process C)", color = Color.White, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Đỉnh tỉnh táo sáng", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "${fmt(forecast.morningPeakStartMin)} – ${fmt(forecast.morningPeakEndMin)}",
                    color = SleepPalette.OptimalGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Vùng trũng (Giờ vàng Nap)", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "${fmt(forecast.afternoonDipStartMin)} – ${fmt(forecast.afternoonDipEndMin)}",
                    color = SleepPalette.StarGold,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cửa sổ đi ngủ (Melatonin)", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "${fmt(forecast.melatoninWindowStartMin)} – ${fmt(forecast.melatoninWindowEndMin)}",
                    color = SleepPalette.StageRem,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun BedtimeSelectorCard(
    bedtime: BedtimeConfigState,
    effectiveSmartWindowMin: Int,
    onToggleAlarmMode: (Boolean) -> Unit,
    onOpenTimePicker: () -> Unit,
    onToggleSmartWake: (Boolean) -> Unit,
    onStartNightSleep: () -> Unit,
    onStartPowerNap: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "Chọn chế độ ngủ đêm nay",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val alarmSelected = bedtime.useAlarm
                Surface(
                    onClick = { onToggleAlarmMode(true) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = if (alarmSelected) SleepPalette.SurfaceElevated else Color.Transparent,
                    border = BorderStroke(1.dp, if (alarmSelected) SleepPalette.StageRem else Color.White.copy(alpha = 0.15f))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Có báo thức", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Hẹn giờ + Smart Wake", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Surface(
                    onClick = { onToggleAlarmMode(false) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = if (!alarmSelected) SleepPalette.SurfaceElevated else Color.Transparent,
                    border = BorderStroke(1.dp, if (!alarmSelected) SleepPalette.OptimalGreen else Color.White.copy(alpha = 0.15f))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Không báo thức", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Đo nợ ngủ chuẩn nhất", color = SleepPalette.OptimalGreen, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (bedtime.useAlarm) {
                val now = LocalTime.now()
                val alarmTime = LocalTime.of(bedtime.alarmHour, bedtime.alarmMinute)
                val diffMin = ((alarmTime.hour * 60 + alarmTime.minute) - (now.hour * 60 + now.minute)).let {
                    if (it > 0) it else it + 1440
                }
                Surface(
                    onClick = onOpenTimePicker,
                    color = SleepPalette.SurfaceElevated,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Nhấn để đổi giờ thức", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = alarmTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = SleepPalette.StarGold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${diffMin / 60}h ${diffMin % 60}p ngủ", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = String.format("~%.1f chu kỳ 90p", diffMin / 90f),
                                color = SleepPalette.StageRem,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Smart Wake", color = Color.White)
                        Text(
                            text = "Cửa sổ tự co theo nợ ngủ: ${effectiveSmartWindowMin}p",
                            color = Color.Gray,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Switch(checked = bedtime.smartWakeEnabled, onCheckedChange = onToggleSmartWake)
                }
            } else {
                Text(
                    text = "Cơ thể tự thức dậy tự nhiên giúp hệ thống đo chính xác nhất nhu cầu ngủ cá nhân (trọng số học x2.5).",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onStartNightSleep,
                    modifier = Modifier.weight(1.4f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SleepPalette.StageDeep)
                ) {
                    Text(if (bedtime.useAlarm) "Ngủ • Có chuông" else "Ngủ • Không chuông", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onStartPowerNap,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Ngủ trưa (Nap)", color = SleepPalette.StageRem)
                }
            }
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
            text = "Chế độ: ${uiState.tracking.trackingMode.name} • ${uiState.tracking.wakeMode.name}",
            color = Color(0xFF4B5563),
            style = MaterialTheme.typography.labelSmall
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = currentTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                fontSize = 68.sp,
                fontWeight = FontWeight.Thin,
                color = Color(0xFF9CA3AF)
            )
            Text(
                text = "Giai đoạn hiện tại: ${uiState.tracking.currentStage.displayLabel()}",
                color = uiState.tracking.currentStage.stageColor().copy(alpha = 0.8f)
            )
            uiState.tracking.latestBreathingBpm?.let { bpm ->
                Text("Nhịp thở Sonar: ${String.format("%.1f", bpm)} nhịp/phút", color = SleepPalette.StageRem)
            }
            if (uiState.tracking.trackingMode == TrackingMode.SONAR && waveSamples.size > 2) {
                Canvas(modifier = Modifier.fillMaxWidth().height(72.dp)) {
                    val path = Path()
                    val maxAbs = maxOf(0.05f, waveSamples.maxOf { abs(it) })
                    val step = size.width / (waveSamples.size - 1)
                    waveSamples.forEachIndexed { idx, v ->
                        val y = size.height / 2f - (v / maxAbs) * (size.height * 0.4f)
                        if (idx == 0) path.moveTo(0f, y) else path.lineTo(idx * step, y)
                    }
                    drawPath(path, color = Color(0xFF71717A), style = Stroke(width = 2.dp.toPx()))
                }
            }
        }

        Button(
            onClick = onStopTracking,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1C1F)),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("Thức dậy & Lưu giấc ngủ", color = Color(0xFFE5E7EB), fontWeight = FontWeight.SemiBold)
        }
    }
}

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
                Text("Mức độ tỉnh táo (1 = Rất mệt, 5 = Rất khỏe): $energyScore/5")
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    notes.take(3).forEach { note ->
                        val checked = note.id in selectedNotes
                        FilterChip(
                            selected = checked,
                            onClick = { if (checked) selectedNotes.remove(note.id) else selectedNotes.add(note.id) },
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
            Button(onClick = { onSubmit(energyScore, selectedNotes.toSet(), dreamText) }) { Text("Lưu") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Bỏ qua") }
        }
    )
}