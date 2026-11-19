package org.opensleep.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.opensleep.app.data.local.db.DebtStatusLevel
import org.opensleep.app.data.local.db.SleepStage
import org.opensleep.app.data.local.db.WakeMode
import org.opensleep.app.data.local.db.entity.DailySleepDebtSnapshotEntity
import org.opensleep.app.data.local.db.entity.SleepEpochEntity
import org.opensleep.app.data.local.db.entity.SleepSessionEntity
import org.opensleep.app.data.local.db.entity.SoundEventEntity
import org.opensleep.app.ui.theme.SleepPalette
import org.opensleep.app.ui.theme.displayLabel
import org.opensleep.app.ui.theme.stageColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.lazy.rememberLazyListState

data class DaySleepStripItem(
    val epochDay: Long,
    val date: LocalDate,
    val totalSleepMinutes: Int,
    val targetNeedMinutes: Int
)

@Composable
fun HistoryDetailRoute(
    uiState: HistoryUiState,
    onSelectDay: (Long) -> Unit,
    onLogManualSleep: (Int, WakeMode, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showManualDialog by remember { mutableStateOf(false) }

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
            Text(
                text = "Lịch sử từng ngày",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            FilledTonalButton(onClick = { showManualDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Ghi bù")
            }
        }

        // Dải chọn từng ngày: hiển thị mỗi ngày ngủ mấy tiếng
        DailyHoursStrip(
            days = uiState.daysStrip,
            selectedEpochDay = uiState.selectedEpochDay,
            onSelectDay = onSelectDay
        )

        val session = uiState.selectedSession
        if (session == null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Chưa có bản ghi giấc ngủ cho ngày này.", color = Color.LightGray)
                    TextButton(onClick = { showManualDialog = true }) {
                        Text("+ Nhập thủ công hoặc tạo dữ liệu mẫu để xem biểu đồ")
                    }
                }
            }
        } else {
            SessionSummaryCard(session = session)
            SleepStages4TierCard(epochs = uiState.selectedEpochs)
            StageBreakdownCard(session = session)
            if (uiState.selectedSoundEvents.isNotEmpty()) {
                SoundEventsCard(events = uiState.selectedSoundEvents)
            }
        }

        if (uiState.debtSnapshots14Days.isNotEmpty()) {
            DebtTrend14DayCard(snapshots = uiState.debtSnapshots14Days)
        }
    }

    if (showManualDialog) {
        ManualSleepEntryDialog(
            onSave = { mins, mode, demo ->
                onLogManualSleep(mins, mode, demo)
                showManualDialog = false
            },
            onDismiss = { showManualDialog = false }
        )
    }
}

@Composable
fun DailyHoursStrip(
    days: List<DaySleepStripItem>,
    selectedEpochDay: Long,
    onSelectDay: (Long) -> Unit
) {
    val dowFmt = remember { DateTimeFormatter.ofPattern("EE") }
    val listState = rememberLazyListState()

    // Tự động cuộn để focus ngày đang chọn (mặc định là Hôm nay ở cuối dải)
    LaunchedEffect(days.size, selectedEpochDay) {
        val targetIdx = days.indexOfFirst { it.epochDay == selectedEpochDay }
        if (targetIdx >= 0) {
            listState.animateScrollToItem(index = maxOf(0, targetIdx - 2))
        }
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(days, key = { it.epochDay }) { item ->
            val isSelected = item.epochDay == selectedEpochDay
            val met = item.totalSleepMinutes >= item.targetNeedMinutes
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) Color(0xFF27272A) else SleepPalette.SurfaceCard,
                border = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE4E4E7).copy(alpha = 0.45f))
                } else null,
                modifier = Modifier
                    .width(68.dp)
                    .clickable { onSelectDay(item.epochDay) }
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(item.date.format(dowFmt), style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                    Text("${item.date.dayOfMonth}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        text = if (item.totalSleepMinutes > 0) String.format("%.1fh", item.totalSleepMinutes / 60f) else "--",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.totalSleepMinutes == 0) Color.Gray else if (met) SleepPalette.OptimalGreen else SleepPalette.StarGold
                    )
                }
            }
        }
    }
}

@Composable
fun SessionSummaryCard(session: SleepSessionEntity) {
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val zone = ZoneId.systemDefault()
    val startStr = Instant.ofEpochMilli(session.asleepTimeMs).atZone(zone).format(timeFmt)
    val endStr = Instant.ofEpochMilli(session.wakeUpTimeMs).atZone(zone).format(timeFmt)
    val totalMin = (session.sleepDurationMs / 60_000L).toInt()
    val wakeBadge = when (session.wakeMode) {
        WakeMode.NATURAL_NO_ALARM -> "Không báo thức (Tự nhiên)"
        WakeMode.SMART_ALARM -> "Báo thức thông minh"
        WakeMode.HARD_ALARM -> "Có báo thức"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Tổng thời gian ngủ", color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
                Text(wakeBadge, color = SleepPalette.StageRem, style = MaterialTheme.typography.labelSmall)
            }
            Text(
                text = "${totalMin / 60} giờ ${totalMin % 60} phút",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Từ $startStr đến $endStr • Thức vi mô: ${session.microAwakeningsCount} lần",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun SleepStages4TierCard(epochs: List<SleepEpochEntity>) {
    var inspectedIndex by remember(epochs) { mutableStateOf<Int?>(null) }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }

    Card(
        colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Các khoảng Awake • REM • Core • Deep",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            // Hàng riêng cố định hiển thị 03:42 • Awake • 15 bpm, không bị ép dạt sang mép phải
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SleepPalette.SurfaceElevated, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                val inspected = inspectedIndex?.let { epochs.getOrNull(it) }
                if (inspected != null) {
                    val t = Instant.ofEpochMilli(inspected.timestampMs).atZone(ZoneId.systemDefault()).format(timeFmt)
                    val br = inspected.breathingRateBpm?.let { " • ${String.format("%.0f", it)} bpm" } ?: ""
                    Text(
                        text = "$t • ${inspected.stage.displayLabel()}$br",
                        color = inspected.stage.stageColor(),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                } else {
                    Text(
                        text = "Chạm hoặc kéo ngang trên biểu đồ để xem từng mốc 30 giây",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }

            if (epochs.isEmpty()) {
                Text("Phiên nhập tay không có chi tiết từng 30 giây.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            } else {
                Row(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                    Column(
                        modifier = Modifier.width(48.dp).fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceAround
                    ) {
                        listOf(SleepStage.AWAKE, SleepStage.REM, SleepStage.LIGHT, SleepStage.DEEP).forEach { st ->
                            Text(
                                text = st.displayLabel(),
                                color = st.stageColor(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(epochs) {
                                detectTapGestures { offset ->
                                    inspectedIndex = ((offset.x / size.width) * epochs.size).toInt().coerceIn(0, epochs.lastIndex)
                                }
                            }
                            .pointerInput(epochs) {
                                detectHorizontalDragGestures { change, _ ->
                                    change.consume()
                                    inspectedIndex = ((change.position.x / size.width) * epochs.size).toInt().coerceIn(0, epochs.lastIndex)
                                }
                            }
                    ) {
                        val rowHeight = size.height / 4f
                        val blockHeight = rowHeight * 0.62f
                        val epochWidth = (size.width / epochs.size).coerceAtLeast(2f)

                        for (row in 0..3) {
                            val yCenter = row * rowHeight + rowHeight / 2f
                            drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, yCenter), Offset(size.width, yCenter), strokeWidth = 1.dp.toPx())
                        }

                        epochs.forEachIndexed { idx, epoch ->
                            val rowIdx = when (epoch.stage) {
                                SleepStage.AWAKE -> 0
                                SleepStage.REM -> 1
                                SleepStage.LIGHT -> 2
                                SleepStage.DEEP -> 3
                            }
                            val topY = rowIdx * rowHeight + (rowHeight - blockHeight) / 2f
                            val leftX = idx * (size.width / epochs.size)
                            drawRoundRect(
                                color = epoch.stage.stageColor(),
                                topLeft = Offset(leftX, topY),
                                size = Size(epochWidth + 0.8f, blockHeight),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }

                        inspectedIndex?.let { idx ->
                            val cursorX = idx * (size.width / epochs.size)
                            drawLine(Color.White.copy(alpha = 0.7f), Offset(cursorX, 0f), Offset(cursorX, size.height), strokeWidth = 1.5.dp.toPx())
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StageBreakdownCard(session: SleepSessionEntity) {
    val totalInBedMs = (session.awakeDurationMs + session.sleepDurationMs).coerceAtLeast(1L)
    val rows = listOf(
        SleepStage.AWAKE to session.awakeDurationMs,
        SleepStage.REM to session.remSleepMs,
        SleepStage.LIGHT to session.lightSleepMs,
        SleepStage.DEEP to session.deepSleepMs
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Phân bổ chi tiết", color = Color.White, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
                rows.forEach { (stage, ms) ->
                    val f = (ms.toFloat() / totalInBedMs.toFloat()).coerceAtLeast(0f)
                    if (f > 0.01f) Box(Modifier.weight(f).fillMaxHeight().background(stage.stageColor()))
                }
            }
            rows.forEach { (stage, ms) ->
                val mins = (ms / 60_000L).toInt()
                val pct = ((ms * 100f) / totalInBedMs.toFloat()).toInt()
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(stage.stageColor()))
                        Text(stage.displayLabel(), color = Color.White, fontWeight = FontWeight.Medium)
                    }
                    Text("${mins / 60}h ${mins % 60}p ($pct%)", color = Color.LightGray, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun SoundEventsCard(events: List<SoundEventEntity>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Âm thanh ban đêm (YAMNet AI)", color = Color.White, fontWeight = FontWeight.Bold)
            events.groupBy { it.category }.forEach { (cat, list) ->
                Text("• ${cat.name}: ${list.size} lần ghi nhận", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun DebtTrend14DayCard(snapshots: List<DailySleepDebtSnapshotEntity>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Xu hướng Nợ ngủ 14 ngày", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
                val maxDebt = maxOf(480f, snapshots.maxOfOrNull { it.cumulativeDebtMinutes.toFloat() } ?: 480f)
                val stepX = size.width / maxOf(1, snapshots.size)
                val barW = stepX * 0.6f
                snapshots.forEachIndexed { idx, snap ->
                    val ratio = (snap.cumulativeDebtMinutes / maxDebt).coerceIn(0.03f, 1f)
                    val barH = size.height * ratio
                    val x = idx * stepX + (stepX - barW) / 2f
                    val color = when (snap.debtStatusLevel) {
                        DebtStatusLevel.OPTIMAL -> SleepPalette.OptimalGreen
                        DebtStatusLevel.MILD -> SleepPalette.StarGold
                        DebtStatusLevel.HIGH -> Color(0xFFFB923C)
                        DebtStatusLevel.CRITICAL -> SleepPalette.StageAwake
                    }
                    drawRoundRect(color, Offset(x, size.height - barH), Size(barW, barH), CornerRadius(6f, 6f))
                }
            }
        }
    }
}

@Composable
fun ManualSleepEntryDialog(
    onSave: (Int, WakeMode, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var hours by remember { mutableStateOf(7.5f) }
    var isNatural by remember { mutableStateOf(true) }
    var withDemoStages by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ghi nhận giấc ngủ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Thời lượng ngủ: ${String.format("%.1f", hours)} tiếng")
                Slider(value = hours, onValueChange = { hours = it }, valueRange = 2f..12f, steps = 19)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Không báo thức (Tự nhiên)", modifier = Modifier.weight(1f))
                    Switch(checked = isNatural, onCheckedChange = { isNatural = it })
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tạo chu kỳ mẫu Awake/REM/Core/Deep", modifier = Modifier.weight(1f))
                    Switch(checked = withDemoStages, onCheckedChange = { withDemoStages = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val mode = if (isNatural) WakeMode.NATURAL_NO_ALARM else WakeMode.HARD_ALARM
                onSave((hours * 60).toInt(), mode, withDemoStages)
            }) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}