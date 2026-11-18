package org.opensleep.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.opensleep.app.data.local.db.DebtStatusLevel
import org.opensleep.app.data.local.db.SleepStage
import org.opensleep.app.data.local.db.entity.DailySleepDebtSnapshotEntity
import org.opensleep.app.data.local.db.entity.SleepEpochEntity
import org.opensleep.app.domain.model.CircadianForecast
import org.opensleep.app.domain.model.SleepDebtAnalysis
import kotlin.math.min

@Composable
fun SleepDebtDashboardScreen(
    uiState: DashboardUiState,
    liveWaveFlow: kotlinx.coroutines.flow.Flow<Float>,
    onStartNightSleep: () -> Unit,
    onStartPowerNap: () -> Unit,
    onStopTracking: () -> Unit,
    onRunCalibration: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "OpenSleep • 100% Ngoại tuyến",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // 1. Thẻ Nợ ngủ Hero Gauge
        uiState.analysis?.let { analysis ->
            SleepDebtHeroCard(analysis = analysis)
            CircadianEnergyCard(forecast = analysis.circadianForecast)
        }

        // 2. Biểu đồ xu hướng Nợ ngủ 14 ngày
        if (uiState.history14Days.isNotEmpty()) {
            SleepDebt14DayChartCard(snapshots = uiState.history14Days)
        }

        // 3. Nút Điều khiển Tracking
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (uiState.tracking.isTracking) {
                    Text(
                        text = "Đang theo dõi (${uiState.tracking.trackingMode.name}) • Giai đoạn: ${uiState.tracking.currentStage.name}",
                        fontWeight = FontWeight.SemiBold
                    )
                    uiState.tracking.latestBreathingBpm?.let { bpm ->
                        Text(text = "Nhịp thở Sonar: ${String.format("%.1f", bpm)} nhịp/phút")
                    }
                    Button(
                        onClick = onStopTracking,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Thức dậy & Tính Nợ ngủ") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onStartNightSleep, modifier = Modifier.weight(1f)) {
                            Text("Ngủ đêm")
                        }
                        OutlinedButton(onClick = onStartPowerNap, modifier = Modifier.weight(1f)) {
                            Text("Ngủ trưa (Nap)")
                        }
                    }
                }
            }
        }

        // 4. Sonar Calibration & Live Wave Debugger
        SonarCalibrationCard(
            calibration = uiState.calibration,
            isTracking = uiState.tracking.isTracking,
            liveWaveFlow = liveWaveFlow,
            onRunCalibration = onRunCalibration
        )
    }
}

@Composable
fun SleepDebtHeroCard(analysis: SleepDebtAnalysis) {
    val statusColor = when (analysis.statusLevel) {
        DebtStatusLevel.OPTIMAL -> Color(0xFF2E7D32)
        DebtStatusLevel.MILD -> Color(0xFFF9A825)
        DebtStatusLevel.HIGH -> Color(0xFFEF6C00)
        DebtStatusLevel.CRITICAL -> Color(0xFFC62828)
    }
    val hours = analysis.cumulativeDebtMinutes / 60
    val mins = analysis.cumulativeDebtMinutes % 60
    val sweepProgress = (analysis.cumulativeDebtMinutes / 600f).coerceIn(0.05f, 1f)

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(Color.LightGray.copy(alpha = 0.25f), 0f, 360f, false, style = stroke)
                    drawArc(statusColor, -90f, 360f * sweepProgress, false, style = stroke)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${hours}h ${mins}p", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Nợ ngủ", style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Trạng thái: ${analysis.statusLevel.name}", color = statusColor, fontWeight = FontWeight.Bold)
                Text("Nhu cầu cá nhân: ${analysis.activeSleepNeedMinutes / 60}h ${analysis.activeSleepNeedMinutes % 60}p/ngày")
                if (analysis.recommendedNapMinutes > 0) {
                    Text(
                        text = "Gợi ý: Chợp mắt ${analysis.recommendedNapMinutes}p vào đầu giờ chiều để hồi phục.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun CircadianEnergyCard(forecast: CircadianForecast) {
    fun fmt(min: Int): String = String.format("%02d:%02d", min / 60, min % 60)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Dự báo Nhịp sinh học hôm nay", fontWeight = FontWeight.Bold)
            Text("Đỉnh tỉnh táo sáng: ${fmt(forecast.morningPeakStartMin)} – ${fmt(forecast.morningPeakEndMin)}")
            Text("Vùng trũng năng lượng (Giờ Nap): ${fmt(forecast.afternoonDipStartMin)} – ${fmt(forecast.afternoonDipEndMin)}")
            Text(
                text = "Giờ vàng đi ngủ (Melatonin): ${fmt(forecast.melatoninWindowStartMin)} – ${fmt(forecast.melatoninWindowEndMin)}",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun SleepDebt14DayChartCard(snapshots: List<DailySleepDebtSnapshotEntity>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Lịch sử Nợ ngủ 14 ngày", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val maxDebt = maxOf(480f, snapshots.maxOfOrNull { it.cumulativeDebtMinutes.toFloat() } ?: 480f)
                val barWidth = (size.width / maxOf(1, snapshots.size)) * 0.6f
                val stepX = size.width / maxOf(1, snapshots.size)

                snapshots.forEachIndexed { index, snap ->
                    val ratio = (snap.cumulativeDebtMinutes / maxDebt).coerceIn(0.02f, 1f)
                    val barHeight = size.height * ratio
                    val x = index * stepX + (stepX - barWidth) / 2f
                    val color = when (snap.debtStatusLevel) {
                        DebtStatusLevel.OPTIMAL -> Color(0xFF4CAF50)
                        DebtStatusLevel.MILD -> Color(0xFFFFC107)
                        DebtStatusLevel.HIGH -> Color(0xFFFF9800)
                        DebtStatusLevel.CRITICAL -> Color(0xFFF44336)
                    }
                    drawRect(color = color, topLeft = Offset(x, size.height - barHeight), size = Size(barWidth, barHeight))
                }
            }
        }
    }
}

@Composable
fun HypnogramCard(epochs: List<SleepEpochEntity>, modifier: Modifier = Modifier) {
    if (epochs.isEmpty()) return
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Biểu đồ Chu kỳ Giấc ngủ (Hypnogram)", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                val stepX = size.width / maxOf(1, epochs.size - 1)
                val path = Path()
                epochs.forEachIndexed { idx, epoch ->
                    val yLevel = when (epoch.stage) {
                        SleepStage.AWAKE -> 0.10f
                        SleepStage.REM -> 0.35f
                        SleepStage.LIGHT -> 0.65f
                        SleepStage.DEEP -> 0.92f
                    }
                    val pt = Offset(idx * stepX, size.height * yLevel)
                    if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                }
                drawPath(path = path, color = Color(0xFF5C6BC0), style = Stroke(width = 3.dp.toPx()))
            }
        }
    }
}

@Composable
fun SonarCalibrationCard(
    calibration: CalibrationState,
    isTracking: Boolean,
    liveWaveFlow: kotlinx.coroutines.flow.Flow<Float>,
    onRunCalibration: () -> Unit
) {
    val waveSamples = remember { mutableStateListOf<Float>() }
    LaunchedEffect(isTracking) {
        if (isTracking) {
            liveWaveFlow.collect { sample ->
                waveSamples.add(sample)
                if (waveSamples.size > 60) waveSamples.removeAt(0)
            }
        } else {
            waveSamples.clear()
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Chẩn đoán Phần cứng Sonar Siêu âm", fontWeight = FontWeight.Bold)
            Button(onClick = onRunCalibration, enabled = !calibration.isRunning && !isTracking) {
                Text(if (calibration.isRunning) "Đang phát thử 18.75 kHz..." else "Kiểm tra Loa & Mic (2.5s)")
            }
            calibration.lastSnrDb?.let { snr ->
                val verdict = if (calibration.isSupported == true) "ĐẠT CHUẨN SONAR" else "Dùng Passive Mode"
                Text("SNR đo được: ${String.format("%.1f", snr)} dB -> $verdict")
            }
            if (isTracking && waveSamples.size > 2) {
                Text("Sóng nhịp thở trực tiếp (Live Breath Wave):", style = MaterialTheme.typography.labelMedium)
                Canvas(modifier = Modifier.fillMaxWidth().height(70.dp)) {
                    val path = Path()
                    val maxAbs = maxOf(0.05f, waveSamples.maxOf { kotlin.math.abs(it) })
                    val step = size.width / (waveSamples.size - 1)
                    waveSamples.forEachIndexed { idx, v ->
                        val y = size.height / 2f - (v / maxAbs) * (size.height * 0.42f)
                        if (idx == 0) path.moveTo(0f, y) else path.lineTo(idx * step, y)
                    }
                    drawPath(path, color = Color(0xFF00ACC1), style = Stroke(width = 2.dp.toPx()))
                }
            }
        }
    }
}
