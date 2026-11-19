package org.opensleep.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import org.opensleep.app.core.util.BatteryOptimizationHelper
import org.opensleep.app.data.local.db.entity.AlarmConfigEntity
import org.opensleep.app.ui.theme.SleepPalette

@Composable
fun SettingsAndAlarmScreen(
    uiState: SettingsUiState,
    onUpdateSleepNeed: (Int) -> Unit,
    onToggleAutoCalibrate: (Boolean) -> Unit,
    onToggleAlarm: (AlarmConfigEntity, Boolean) -> Unit,
    onDeleteAlarm: (AlarmConfigEntity) -> Unit,
    onRunCalibration: () -> Unit,
    onExportBackup: (Uri) -> Unit,
    onImportBackup: (Uri) -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(onExportBackup) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onImportBackup) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SleepPalette.BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Cài đặt • Báo thức & Riêng tư",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        uiState.statusMessage?.let { msg ->
            Card(colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceElevated)) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(msg, color = SleepPalette.OptimalGreen, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClearMessage) { Text("Đóng") }
                }
            }
        }

        // 1. Cấu hình Nhu cầu ngủ & Auto-Calibration
        Card(
            colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Nhu cầu ngủ cá nhân", color = Color.White, fontWeight = FontWeight.Bold)
                val needMin = uiState.profile.targetSleepNeedMinutes
                Text("Mục tiêu gốc: ${needMin / 60}h ${needMin % 60}p/đêm", color = Color.LightGray)
                Slider(
                    value = needMin.toFloat(),
                    onValueChange = { onUpdateSleepNeed(it.toInt()) },
                    valueRange = 390f..600f,
                    steps = 13
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Tự học nhu cầu thật (Auto-Calibrate)", color = Color.White)
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

        // 2. Danh sách Báo thức đã lưu
        Card(
            colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Lịch báo thức đã tạo", color = Color.White, fontWeight = FontWeight.Bold)
                if (uiState.alarms.isEmpty()) {
                    Text("Chưa có báo thức nào. Hãy tạo ở màn hình Ngủ đêm.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
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
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text("Smart Wake: ${alarm.smartWindowMinutes}p", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = alarm.isEnabled, onCheckedChange = { onToggleAlarm(alarm, it) })
                                IconButton(onClick = { onDeleteAlarm(alarm) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Sonar Hardware Self-Test & Chống giết ngầm Pin OEM
        Card(
            colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Phần cứng Sonar & Chạy nền", color = Color.White, fontWeight = FontWeight.Bold)
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
                    Text(BatteryOptimizationHelper.getManufacturerGuidance(), color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                } else {
                    Text("Đã cấp quyền chạy xuyên đêm an toàn.", color = SleepPalette.OptimalGreen, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // 4. Sao lưu & Khôi phục Cục bộ (JSON) + Cam kết Riêng tư
        Card(
            colors = CardDefaults.cardColors(containerColor = SleepPalette.SurfaceCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Security, contentDescription = null, tint = SleepPalette.OptimalGreen)
                    Text("Dữ liệu Cục bộ 100%", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "Ứng dụng không khai báo quyền INTERNET, không tài khoản, không passkey. Bạn tự giữ dữ liệu qua tệp JSON.",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("opensleep_backup.json") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Xuất JSON")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Nhập JSON")
                    }
                }
            }
        }
    }
}