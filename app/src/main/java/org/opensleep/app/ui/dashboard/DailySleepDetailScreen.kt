package org.opensleep.app.ui.history

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.WarningAmber
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
import org.opensleep.app.domain.model.SleepHealthReport
import org.opensleep.app.domain.model.SleepHealthWarning
import org.opensleep.app.domain.model.SleepQualityMetrics
import org.opensleep.app.domain.model.WarningSeverity
import org.opensleep.app.ui.theme.SleepPalette
import org.opensleep.app.ui.theme.displayLabel
import org.opensleep.app.ui.theme.stageColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

data class DaySleepStripItem(
    val epochDay: Long,
    val date: LocalDate,
    val totalSleepMinutes: Int,
    val targetNeedMinutes: Int,
    val isDemoData: Boolean = false
)

@Composable
fun HistoryDetailRoute(
    uiState: HistoryUiState,
    onSelectDay: (Long) -> Unit,
    onLogManualSleep: (Int, WakeMode, Boolean) -> Unit,
    onSelectReportRange: (ReportRangeOption) -> Unit,
    onDismissWarning: (String) -> Unit,
    onExportCsv: (Uri) -> Unit,
    onExportPdf: (Uri) -> Unit,
    onClearExportMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var subTab by remember { mutableStateOf(0) } // 0 = Từng đêm, 1 = Báo cáo & Đều đặn
    var showManualDialog by remember { mutableStateOf(false) }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let(onExportCsv) }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let(onExportPdf) }

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
                text = "Chi tiết & Báo cáo",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SleepPalette.OnBackground
            )
            if (subTab == 0) {
                FilledTonalButton(onClick = { showManualDialog = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ghi bù")
                }
            }
        }

        // Chuyển giữa Từng đêm và Báo cáo & Đều đặn
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                label = { Text("Từng đêm") }
            )
            FilterChip(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                label = { Text("Báo cáo & Đều đặn") }
            )
        }

        uiState.exportStatusMessage?.let { msg ->
            Card(colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceElevated)) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(msg, color = SleepPalette.OptimalGreen, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onClearExportMessage) { Text("Đóng") }
                }
            }
        }

        if (subTab == 0) {
            DailyHoursStrip(
                days = uiState.daysStrip,
                selectedEpochDay = uiState.selectedEpochDay,
                onSelectDay = onSelectDay
            )

            val session = uiState.selectedSession
            val metrics = uiState.selectedQualityMetrics
            if (session == null || metrics == null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Chưa có bản ghi giấc ngủ cho ngày này.", color = SleepPalette.OnBackgroundMuted)
                        TextButton(onClick = { showManualDialog = true }) {
                            Text("+ Nhập thủ công giấc ngủ")
                        }
                    }
                }
            } else {
                SessionSummaryWithQualityCard(session = session, metrics = metrics)
                if (uiState.selectedEpochs.isNotEmpty()) {
                    SleepStages4TierCard(epochs = uiState.selectedEpochs, isDemo = uiState.selectedSessionIsDemo)
                }
                StageBreakdownCard(session = session)
                if (uiState.selectedSoundEvents.isNotEmpty()) {
                    SoundEventsCard(events = uiState.selectedSoundEvents)
                }
            }

            if (uiState.debtSnapshots14Days.isNotEmpty()) {
                DebtTrendAndSimulatorCard(snapshots = uiState.debtSnapshots14Days)
            }
        } else {
            ReportAndRegularityTab(
                uiState = uiState,
                onSelectReportRange = onSelectReportRange,
                onDismissWarning = onDismissWarning,
                onLaunchCsvExport = {
                    csvLauncher.launch("opensleep_report_${LocalDate.now()}.csv")
                },
                onLaunchPdfExport = {
                    pdfLauncher.launch("opensleep_report_${LocalDate.now()}.pdf")
                }
            )
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
private fun DailyHoursStrip(
    days: List<DaySleepStripItem>,
    selectedEpochDay: Long,
    onSelectDay: (Long) -> Unit
) {
    val dowFmt = remember { DateTimeFormatter.ofPattern("EE") }
    val listState = rememberLazyListState()
    LaunchedEffect(days.size, selectedEpochDay) {
        val targetIdx = days.indexOfFirst { it.epochDay == selectedEpochDay }
        if (targetIdx >= 0) {
            listState.animateScrollToItem(index = maxOf(0, targetIdx - 2))
        }
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(days) { item ->
            val isSelected = item.epochDay == selectedEpochDay
            val hours = item.totalSleepMinutes / 60f
            val metTarget = item.totalSleepMinutes >= item.targetNeedMinutes
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) SleepPalette.PrimaryBlueDim else SleepPalette.SurfaceCard,
                border = if (isSelected) BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)) else null,
                modifier = Modifier
                    .width(70.dp)
                    .clickable { onSelectDay(item.epochDay) }
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.date.format(dowFmt),
                        style = MaterialTheme.typography.labelSmall,
                        color = SleepPalette.OnBackgroundMuted
                    )
                    Text(
                        text = item.date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SleepPalette.OnBackground
                    )
                    Text(
                        text = when {
                            item.totalSleepMinutes <= 0 -> "--"
                            item.isDemoData -> String.format("%.1fh*", hours)
                            else -> String.format("%.1fh", hours)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            item.isDemoData -> SleepPalette.StageRem
                            item.totalSleepMinutes <= 0 -> SleepPalette.OnBackgroundDim
                            metTarget -> SleepPalette.OptimalGreen
                            else -> SleepPalette.StarGold
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionSummaryWithQualityCard(
    session: SleepSessionEntity,
    metrics: SleepQualityMetrics
) {
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val zone = remember { ZoneId.systemDefault() }
    val asleepStr = Instant.ofEpochMilli(session.asleepTimeMs).atZone(zone).toLocalTime().format(fmt)
    val wakeStr = Instant.ofEpochMilli(session.wakeUpTimeMs).atZone(zone).toLocalTime().format(fmt)

    Card(
        colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (metrics.isDemoData) "Thời gian ngủ [DEMO]" else "Tổng thời gian ngủ (TST)",
                    color = if (metrics.isDemoData) SleepPalette.StageRem else SleepPalette.OnBackgroundMuted,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "${metrics.provenance.labelVi}",
                    color = SleepPalette.StageRem,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Text(
                text = "${metrics.tstMinutes / 60} giờ ${metrics.tstMinutes % 60} phút",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = SleepPalette.OnBackground
            )

            Text(
                text = "Thiếp đi $asleepStr → Thức dậy $wakeStr · Độ phủ ${metrics.coveragePct}%",
                color = SleepPalette.OnBackgroundMuted,
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricBox(
                    label = "Hiệu suất (SE)",
                    value = metrics.sleepEfficiencyPct?.let { "${it}%" } ?: "--",
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = "Vào giấc (SOL)",
                    value = metrics.solMinutes?.let { "${it}p" } ?: "--",
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = "Thức đêm (WASO)",
                    value = metrics.wasoMinutes?.let { "${it}p" } ?: "--",
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = "Trên giường",
                    value = "${metrics.tibMinutes / 60}h${metrics.tibMinutes % 60}p",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = SleepPalette.BackgroundCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = modifier
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = SleepPalette.OnBackgroundMuted, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = SleepPalette.OnBackground)
        }
    }
}

@Composable
private fun SleepStages4TierCard(epochs: List<SleepEpochEntity>, isDemo: Boolean) {
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val zone = remember { ZoneId.systemDefault() }
    var inspectedIndex by remember { mutableStateOf<Int?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = if (isDemo) "Biểu đồ mẫu [DEMO]" else "Các khoảng Awake · REM · Core · Deep",
                    color = SleepPalette.OnBackground,
                    fontWeight = FontWeight.Bold
                )
                inspectedIndex?.let { idx ->
                    val ep = epochs.getOrNull(idx)
                    if (ep != null) {
                        val t = Instant.ofEpochMilli(ep.timestampMs).atZone(zone).toLocalTime().format(timeFmt)
                        Text("$t · ${ep.stage.displayLabel()}", color = ep.stage.stageColor(), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Row(Modifier.fillMaxWidth().height(160.dp)) {
                Column(
                    Modifier.width(48.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceAround
                ) {
                    Text("Awake", color = SleepPalette.StageAwake, style = MaterialTheme.typography.labelSmall)
                    Text("REM", color = SleepPalette.StageRem, style = MaterialTheme.typography.labelSmall)
                    Text("Core", color = SleepPalette.StageCore, style = MaterialTheme.typography.labelSmall)
                    Text("Deep", color = SleepPalette.StageDeep, style = MaterialTheme.typography.labelSmall)
                }

                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(epochs) {
                            detectTapGestures { pos ->
                                inspectedIndex = ((pos.x / size.width) * epochs.size).toInt().coerceIn(0, epochs.lastIndex)
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

@Composable
private fun StageBreakdownCard(session: SleepSessionEntity) {
    val totalInBedMs = (session.awakeDurationMs + session.sleepDurationMs).coerceAtLeast(1L)
    val rows = listOf(
        SleepStage.AWAKE to session.awakeDurationMs,
        SleepStage.REM to session.remSleepMs,
        SleepStage.LIGHT to session.lightSleepMs,
        SleepStage.DEEP to session.deepSleepMs
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Phân bổ chi tiết", color = SleepPalette.OnBackground, fontWeight = FontWeight.Bold)
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
                        Text(stage.displayLabel(), color = SleepPalette.OnBackground, fontWeight = FontWeight.Medium)
                    }
                    Text("${mins / 60}h ${mins % 60}p ($pct%)", color = SleepPalette.OnBackgroundMuted, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SoundEventsCard(events: List<SoundEventEntity>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Âm thanh ban đêm (YAMNet AI)", color = SleepPalette.OnBackground, fontWeight = FontWeight.Bold)
            events.groupBy { it.category }.forEach { (cat, list) ->
                Text("• ${cat.name}: ${list.size} lần ghi nhận", color = SleepPalette.OnBackgroundMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DebtTrendAndSimulatorCard(snapshots: List<DailySleepDebtSnapshotEntity>) {
    val latestDebt = snapshots.lastOrNull()?.cumulativeDebtMinutes ?: 0
    var extraSleepTonightMin by remember { mutableStateOf(30f) }
    val simulatedRecovery = (extraSleepTonightMin * 0.85f).roundToInt().coerceAtMost(90)
    val projectedDebt = max(0, latestDebt - simulatedRecovery)

    Card(
        colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Nợ ngủ 14 ngày", color = SleepPalette.OnBackground, fontWeight = FontWeight.Bold)
                Text("${latestDebt / 60}h ${latestDebt % 60}p", color = SleepPalette.StarGold, fontWeight = FontWeight.Bold)
            }

            Canvas(modifier = Modifier.fillMaxWidth().height(96.dp)) {
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

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Text(
                text = "Mô phỏng hồi phục: Ngủ thêm +${extraSleepTonightMin.toInt()}p → Nợ còn ${projectedDebt / 60}h ${projectedDebt % 60}p",
                color = SleepPalette.OptimalGreen,
                style = MaterialTheme.typography.labelMedium
            )
            Slider(
                value = extraSleepTonightMin,
                onValueChange = { extraSleepTonightMin = it },
                valueRange = 0f..90f,
                steps = 5
            )
        }
    }
}

@Composable
private fun ReportAndRegularityTab(
    uiState: HistoryUiState,
    onSelectReportRange: (ReportRangeOption) -> Unit,
    onDismissWarning: (String) -> Unit,
    onLaunchCsvExport: () -> Unit,
    onLaunchPdfExport: () -> Unit
) {
    val report = uiState.healthReport ?: return

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = uiState.reportRange == ReportRangeOption.LAST_7_DAYS,
            onClick = { onSelectReportRange(ReportRangeOption.LAST_7_DAYS) },
            label = { Text("7 ngày") }
        )
        FilterChip(
            selected = uiState.reportRange == ReportRangeOption.LAST_30_DAYS,
            onClick = { onSelectReportRange(ReportRangeOption.LAST_30_DAYS) },
            label = { Text("30 ngày") }
        )
    }

    // Warnings (non-diagnostic)
    uiState.warnings.forEach { warning ->
        WarningCard(warning = warning, onDismiss = { onDismissWarning(warning.type.name) })
    }

    // Report Overview
    ReportOverviewCard(report = report)

    // Regularity Card
    RegularitySummaryCard(report = report)

    // Export Buttons (SAF Offline)
    Card(
        colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Xuất báo cáo ngoại tuyến", color = SleepPalette.OnBackground, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onLaunchCsvExport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Xuất CSV")
                }
                OutlinedButton(onClick = onLaunchPdfExport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Xuất PDF")
                }
            }
        }
    }
}

@Composable
private fun WarningCard(warning: SleepHealthWarning, onDismiss: () -> Unit) {
    val accent = if (warning.severity == WarningSeverity.ATTENTION) Color(0xFFFB923C) else SleepPalette.StarGold
    Card(
        colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Text(warning.title, color = SleepPalette.OnBackground, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDismiss) {
                    Text("Ẩn 3 ngày", color = SleepPalette.OnBackgroundMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(warning.message, color = SleepPalette.OnBackground, style = MaterialTheme.typography.bodySmall)
            Text(
                text = "${warning.evidenceSummary} · Độ tin cậy ${warning.confidencePct}%",
                color = SleepPalette.OnBackgroundMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun ReportOverviewCard(report: SleepHealthReport) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Báo cáo: ${report.rangeLabel}", color = SleepPalette.OnBackground, fontWeight = FontWeight.Bold)
            Text(
                text = "Độ phủ dữ liệu thật: ${report.validNightsCount}/${report.totalDays} đêm (${report.coveragePct}%)",
                color = SleepPalette.OnBackgroundMuted,
                style = MaterialTheme.typography.bodySmall
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricBox(
                    label = "TB / đêm",
                    value = "${report.avgSleepMinutes / 60}h ${report.avgSleepMinutes % 60}p",
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = "Trung vị",
                    value = "${report.medianSleepMinutes / 60}h ${report.medianSleepMinutes % 60}p",
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = "Hiệu suất TB",
                    value = report.avgEfficiencyPct?.let { "${it}%" } ?: "--",
                    modifier = Modifier.weight(1f)
                )
            }
            report.observations.forEach { obs ->
                Text("• $obs", color = SleepPalette.OnBackground, style = MaterialTheme.typography.bodySmall)
            }
            if (report.excludedDemoCount > 0) {
                Text(
                    text = "• Đã tự động tách ${report.excludedDemoCount} bản ghi [DEMO] khỏi báo cáo.",
                    color = SleepPalette.StageRem,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun RegularitySummaryCard(report: SleepHealthReport) {
    val reg = report.regularity
    Card(
        colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Độ đều đặn nhịp ngủ", color = SleepPalette.OnBackground, fontWeight = FontWeight.Bold)
            if (!reg.hasSufficientData) {
                Text(
                    text = "Cần tối thiểu ${reg.requiredNights} đêm dữ liệu thật để kết luận xu hướng.",
                    color = SleepPalette.StarGold,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricBox(
                    label = "Giờ ngủ",
                    value = "${reg.typicalBedtimeLabel} (±${reg.bedtimeVariabilityMin}p)",
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = "Giờ dậy",
                    value = "${reg.typicalWakeLabel} (±${reg.wakeVariabilityMin}p)",
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = "Điểm giữa",
                    value = reg.sleepMidpointLabel,
                    modifier = Modifier.weight(1f)
                )
            }
            reg.weekendShiftMin?.let { shift ->
                Text(
                    text = "Chênh lệch cuối tuần so với ngày thường: ${shift} phút",
                    color = SleepPalette.OnBackgroundMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ManualSleepEntryDialog(
    onSave: (Int, WakeMode, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var hours by remember { mutableStateOf(7.5f) }
    var isNatural by remember { mutableStateOf(true) }
    var withDemoStages by remember { mutableStateOf(false) }

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
                    Column(Modifier.weight(1f)) {
                        Text("Tạo chu kỳ mẫu [DEMO]")
                        Text("Sẽ tách khỏi báo cáo thật", style = MaterialTheme.typography.labelSmall, color = SleepPalette.StageRem)
                    }
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