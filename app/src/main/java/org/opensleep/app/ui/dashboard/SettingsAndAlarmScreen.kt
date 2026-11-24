package org.opensleep.app.ui.settings

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.opensleep.app.BuildConfig
import org.opensleep.app.core.util.BatteryOptimizationHelper
import org.opensleep.app.core.util.DownloadUpdateState
import org.opensleep.app.data.local.db.entity.AlarmConfigEntity
import org.opensleep.app.ui.theme.SleepPalette

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsAndAlarmScreen(
    uiState: SettingsUiState,
    onUpdateWeekdayWakeTime: (Int, Int) -> Unit,
    onUpdateWeekendSchedule: (Boolean, Int?, Int?) -> Unit,
    onToggleRestDay: (Int) -> Unit,
    onToggleOptionalCheckIn: (Boolean) -> Unit,
    onToggleBedtimeReminder: (Boolean) -> Unit,
    onToggleSoundClassification: (Boolean) -> Unit,
    onSelectSoundRetentionDays: (Int) -> Unit,
    onSelectFontKey: (String) -> Unit,
    onUpdateSleepNeed: (Int) -> Unit,
    onToggleAutoCalibrate: (Boolean) -> Unit,
    onSetPowerNapDuration: (Int) -> Unit = {},
    onSetAlarmRingtone: (String?, String?) -> Unit = { _, _ -> },
    onToggleAlarm: (AlarmConfigEntity, Boolean) -> Unit,
    onDeleteAlarm: (AlarmConfigEntity) -> Unit,
    onRunCalibration: () -> Unit,
    onCheckUpdates: () -> Unit,
    onDownloadAndInstallUpdate: (Context) -> Unit,
    onToggleAutoCheckUpdates: (Boolean) -> Unit,
    onExportBackup: (Uri, String?) -> Unit,
    onImportBackup: (Uri, String?) -> Unit,
    onCheckBackupEncrypted: suspend (Uri) -> Boolean,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingExportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var backupPassphraseInput by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            backupPassphraseInput = ""
            pendingExportUri = uri
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                if (onCheckBackupEncrypted(uri)) {
                    backupPassphraseInput = ""
                    pendingImportUri = uri
                } else {
                    onImportBackup(uri, null)
                }
            }
        }
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                val ringtone = RingtoneManager.getRingtone(context, uri)
                val title = ringtone?.getTitle(context) ?: "Chuông tùy chỉnh"
                onSetAlarmRingtone(uri.toString(), title)
            } else {
                onSetAlarmRingtone(null, "Mặc định hệ thống")
            }
        }
    }

    var pickWeekdayWake by remember { mutableStateOf(false) }
    var pickWeekendWake by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SleepPalette.BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Cài đặt & Báo thức",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = SleepPalette.OnBackground
        )

        uiState.statusMessage?.let { msg ->
            Card(colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceElevated)) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(msg, color = SleepPalette.OptimalGreen, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onClearMessage) { Text("Đóng") }
                }
            }
        }

        val prefs = uiState.preferences

        // 0. Cập nhật qua GitHub Releases (cài đè giữ 100% dữ liệu)
        Card(
            colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, SleepPalette.StageRem.copy(alpha = 0.2f))
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = SleepPalette.StageRem)
                        Column {
                            Text("Cập nhật GitHub Releases", color = SleepPalette.OnBackground, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Phiên bản hiện tại: v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})",
                                color = SleepPalette.OnBackgroundMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                Text(
                    text = "Cài đè trực tiếp APK mới cùng chữ ký Release — giữ nguyên toàn bộ Room DB & lịch sử ngủ (chỉ kết nối duy nhất GitHub WaterKansuza/OpenSleep).",
                    color = SleepPalette.OnBackgroundMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Tự kiểm tra bản mới khi mở app", color = SleepPalette.OnBackground, style = MaterialTheme.typography.bodySmall)
                    Switch(checked = prefs.autoCheckUpdatesEnabled, onCheckedChange = onToggleAutoCheckUpdates)
                }

                uiState.updateInfo?.let { upd ->
                    Surface(
                        color = SleepPalette.BackgroundCard,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (upd.isUpdateAvailable) {
                                    "Bản mới sẵn sàng: ${upd.latestTag}"
                                } else {
                                    "Đang ở bản mới nhất (${upd.latestTag})"
                                },
                                color = if (upd.isUpdateAvailable) SleepPalette.OptimalGreen else SleepPalette.OnBackground,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge
                            )
                            if (upd.releaseNotes.isNotBlank()) {
                                Text(upd.releaseNotes.take(220), color = SleepPalette.OnBackgroundMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            if (upd.isUpdateAvailable && !upd.apkDownloadUrl.isNullOrBlank()) {
                                val dl = uiState.downloadState
                                if (dl is DownloadUpdateState.Progress) {
                                    LinearProgressIndicator(
                                        progress = { dl.percent / 100f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = "Đang tải ${dl.percent}% (${String.format("%.1f", dl.downloadedMb)}/${String.format("%.1f", dl.totalMb)} MB)",
                                        color = SleepPalette.StageRem,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                } else {
                                    Button(
                                        onClick = { onDownloadAndInstallUpdate(context) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Tải & Cài đặt bản ${upd.latestTag}")
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onCheckUpdates,
                    enabled = !uiState.isCheckingUpdate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uiState.isCheckingUpdate) "Đang kiểm tra GitHub..." else "Kiểm tra bản cập nhật ngay")
                }
            }
        }

        // 1. Lịch thức dậy, Ngày nghỉ linh hoạt & Check-in sáng
        Card(
            colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Lịch thức dậy & Nhắc giờ ngủ", color = SleepPalette.OnBackground, fontWeight = FontWeight.Bold)
                Surface(
                    onClick = { pickWeekdayWake = true },
                    color = SleepPalette.BackgroundCard,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Ngày đi làm / đi học", color = SleepPalette.OnBackground)
                        Text(
                            text = String.format("%02d:%02d", prefs.weekdayWakeHour, prefs.weekdayWakeMinute),
                            color = SleepPalette.StageRem,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Giờ dậy riêng cho ngày nghỉ", color = SleepPalette.OnBackground)
                    Switch(
                        checked = prefs.weekendScheduleEnabled,
                        onCheckedChange = { onUpdateWeekendSchedule(it, null, null) }
                    )
                }
                if (prefs.weekendScheduleEnabled) {
                    Surface(
                        onClick = { pickWeekendWake = true },
                        color = SleepPalette.BackgroundCard,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Giờ dậy ngày nghỉ", color = SleepPalette.OnBackground)
                            Text(
                                text = String.format("%02d:%02d", prefs.weekendWakeHour, prefs.weekendWakeMinute),
                                color = SleepPalette.StarGold,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text("Chọn các ngày nghỉ trong tuần:", style = MaterialTheme.typography.labelSmall, color = SleepPalette.OnBackgroundMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN").forEachIndexed { idx, label ->
                            val isRest = (prefs.restDaysBitmask and (1 shl idx)) != 0
                            FilterChip(
                                selected = isRest,
                                onClick = { onToggleRestDay(idx) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Nhắc giờ đi ngủ (-30 phút)", color = SleepPalette.OnBackground)
                        Text(
                            text = "Chỉ hiện thông báo nhắc nhẹ, không bao giờ tự mở mic",
                            color = SleepPalette.OnBackgroundMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Switch(checked = prefs.bedtimeReminderEnabled, onCheckedChange = onToggleBedtimeReminder)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bật hộp thoại Check-in buổi sáng", color = SleepPalette.OnBackground)
                        Text(
                            text = "Tắt mặc định: chỉ hiện 2 nút chọn nhanh không ép buộc",
                            color = SleepPalette.OnBackgroundMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Switch(checked = prefs.optionalCheckInEnabled, onCheckedChange = onToggleOptionalCheckIn)
                }
            }
        }

        // 2. Phase 4 — Pin, AI YAMNet & Lưu trữ âm thanh
        Card(
            colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Phase 4: Pin, AI & Quyền riêng tư", color = SleepPalette.OnBackground, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Phân loại tiếng ngáy YAMNet AI", color = SleepPalette.OnBackground)
                        Text(
                            text = "Chỉ chạy TFLite khi tiếng vượt nền +12 dB; tắt để tiết kiệm pin tối đa",
                            color = SleepPalette.OnBackgroundMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Switch(checked = prefs.soundClassificationEnabled, onCheckedChange = onToggleSoundClassification)
                }
                Text("Tự dọn sự kiện âm thanh cũ (giữ nguyên lịch sử giờ ngủ):", color = SleepPalette.OnBackgroundMuted, style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7 to "7 ngày", 14 to "14 ngày", 30 to "30 ngày", 0 to "Giữ mãi").forEach { (days, lbl) ->
                        FilterChip(
                            selected = prefs.soundRetentionDays == days,
                            onClick = { onSelectSoundRetentionDays(days) },
                            label = { Text(lbl) }
                        )
                    }
                }
            }
        }

        // 3. Chọn kiểu chữ
        Card(
            colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Kiểu chữ hiển thị", color = SleepPalette.OnBackground, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "sans" to "Sans mặc định",
                        "be_vietnam_pro" to "Be Vietnam",
                        "nunito" to "Mềm mại (Light)",
                        "serif" to "Serif dịu mắt"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = prefs.fontKey == key,
                            onClick = { onSelectFontKey(key) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        // 4. Nhu cầu ngủ & Auto-Calibration
        Card(
            colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Nhu cầu ngủ cá nhân", color = SleepPalette.OnBackground, fontWeight = FontWeight.Bold)
                val needMin = uiState.profile.targetSleepNeedMinutes
                Text("Mục tiêu gốc: ${needMin / 60}h ${needMin % 60}p/đêm", color = SleepPalette.OnBackgroundMuted)
                Slider(
                    value = needMin.toFloat(),
                    onValueChange = { onUpdateSleepNeed(it.toInt()) },
                    valueRange = 360f..600f,
                    steps = 15
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Tự học nhu cầu thật (Auto-Calibrate)", color = SleepPalette.OnBackground)
                        Text(
                            text = "Đang hiệu chuẩn: ${uiState.profile.calibratedSleepNeedMinutes / 60}h ${uiState.profile.calibratedSleepNeedMinutes % 60}p",
                            color = SleepPalette.StageRem,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Switch(checked = uiState.profile.autoCalibrateEnabled, onCheckedChange = onToggleAutoCalibrate)
                }
            }
        }

        // 5. Danh sách Báo thức đã lưu
        Card(
            colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Lịch báo thức đã tạo", color = SleepPalette.OnBackground, fontWeight = FontWeight.Bold)
                if (uiState.alarms.isEmpty()) {
                    Text("Chưa có báo thức nào. Hãy bật ở màn hình Hôm nay.", color = SleepPalette.OnBackgroundMuted, style = MaterialTheme.typography.bodySmall)
                } else {
                    uiState.alarms.forEach { alarm ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = String.format("%02d:%02d", alarm.hour, alarm.minute),
                                    color = SleepPalette.OnBackground,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text("Smart Wake: ${alarm.smartWindowMinutes}p", color = SleepPalette.OnBackgroundMuted, style = MaterialTheme.typography.labelSmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = alarm.isEnabled, onCheckedChange = { onToggleAlarm(alarm, it) })
                                IconButton(onClick = { onDeleteAlarm(alarm) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = SleepPalette.OnBackgroundMuted)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5b. Tùy chỉnh Chuông báo thức & Thời lượng Power Nap
        Card(
            colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Chuông báo thức & Chợp mắt",
                    color = SleepPalette.OnBackground,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                // 1. Chọn chuông báo thức
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Âm thanh chuông báo thức:",
                        color = SleepPalette.OnBackgroundMuted,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = SleepPalette.StageRem)
                            Text(
                                text = uiState.preferences.alarmRingtoneTitle ?: "Mặc định hệ thống",
                                color = SleepPalette.OnBackground,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Chọn chuông báo thức")
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    uiState.preferences.alarmRingtoneUri?.let {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
                                    }
                                }
                                ringtoneLauncher.launch(intent)
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Đổi chuông")
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // 2. Thời lượng Power Nap
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Thời lượng Power Nap trưa (Chuẩn: 20p):",
                        color = SleepPalette.OnBackgroundMuted,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15 to "15p", 20 to "20p (chuẩn)", 25 to "25p", 30 to "30p").forEach { (duration, label) ->
                            val isSelected = uiState.preferences.powerNapDurationMinutes == duration
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSetPowerNapDuration(duration) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 6. Sonar Hardware Self-Test & Chống giết ngầm Pin OEM
        Card(
            colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Phần cứng Sonar & Chạy nền", color = SleepPalette.OnBackground, fontWeight = FontWeight.Bold)
                Button(onClick = onRunCalibration, enabled = !uiState.isCalibrating, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Sensors, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (uiState.isCalibrating) "Đang đo sóng 18.75 kHz..." else "Kiểm tra Loa & Mic (2.5 giây)")
                }
                uiState.lastCalibSnrDb?.let { snr ->
                    Text("SNR: ${String.format("%.1f", snr)} dB", color = SleepPalette.StageRem)
                }
                val ignoringBattery = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
                if (!ignoringBattery) {
                    OutlinedButton(
                        onClick = { context.startActivity(BatteryOptimizationHelper.buildRequestIgnoreBatteryIntent(context)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cho phép chạy xuyên đêm (Tắt tối ưu pin)")
                    }
                    Text(BatteryOptimizationHelper.getManufacturerGuidance(), color = SleepPalette.OnBackgroundMuted, style = MaterialTheme.typography.labelSmall)
                } else {
                    Text("Đã cấp quyền chạy xuyên đêm an toàn.", color = SleepPalette.OptimalGreen, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // 7. Sao lưu & Khôi phục Cục bộ (JSON / AES-256-GCM)
        Card(
            colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Security, contentDescription = null, tint = SleepPalette.OptimalGreen)
                    Text("Sao lưu Cục bộ (JSON / AES-256-GCM)", color = SleepPalette.OnBackground, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "Dữ liệu giấc ngủ chỉ nằm trên máy bạn. Có thể đặt mật khẩu để mã hóa AES-256-GCM hoặc giữ JSON thường khi chuyển từ bản cũ.",
                    color = SleepPalette.OnBackgroundMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("opensleep_backup.json") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Xuất Backup")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Nhập Backup")
                    }
                }
            }
        }
    }

    pendingExportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingExportUri = null },
            title = { Text("Mã hóa bản sao lưu (Tùy chọn)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nhập mật khẩu để mã hóa AES-256-GCM, hoặc để trống để xuất JSON thường:")
                    OutlinedTextField(
                        value = backupPassphraseInput,
                        onValueChange = { backupPassphraseInput = it },
                        label = { Text("Mật khẩu (tùy chọn)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val pw = backupPassphraseInput.takeIf { it.isNotBlank() }
                    pendingExportUri = null
                    onExportBackup(uri, pw)
                }) { Text("Xuất tệp") }
            },
            dismissButton = {
                TextButton(onClick = { pendingExportUri = null }) { Text("Hủy") }
            }
        )
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Bản sao lưu đã mã hóa") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tệp này được bảo vệ bằng AES-256-GCM. Nhập mật khẩu để giải mã và khôi phục:")
                    OutlinedTextField(
                        value = backupPassphraseInput,
                        onValueChange = { backupPassphraseInput = it },
                        label = { Text("Mật khẩu giải mã") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = backupPassphraseInput.isNotBlank(),
                    onClick = {
                        val pw = backupPassphraseInput
                        pendingImportUri = null
                        onImportBackup(uri, pw)
                    }
                ) { Text("Khôi phục") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("Hủy") }
            }
        )
    }

    if (pickWeekdayWake) {
        val state = rememberTimePickerState(
            initialHour = uiState.preferences.weekdayWakeHour,
            initialMinute = uiState.preferences.weekdayWakeMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { pickWeekdayWake = false },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateWeekdayWakeTime(state.hour, state.minute)
                    pickWeekdayWake = false
                }) { Text("Lưu") }
            },
            dismissButton = { TextButton(onClick = { pickWeekdayWake = false }) { Text("Hủy") } },
            text = { TimePicker(state = state) }
        )
    }

    if (pickWeekendWake) {
        val state = rememberTimePickerState(
            initialHour = uiState.preferences.weekendWakeHour,
            initialMinute = uiState.preferences.weekendWakeMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { pickWeekendWake = false },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateWeekendSchedule(true, state.hour, state.minute)
                    pickWeekendWake = false
                }) { Text("Lưu") }
            },
            dismissButton = { TextButton(onClick = { pickWeekendWake = false }) { Text("Hủy") } },
            text = { TimePicker(state = state) }
        )
    }
}