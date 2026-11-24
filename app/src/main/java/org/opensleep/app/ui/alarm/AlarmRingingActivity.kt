package org.opensleep.app.ui.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import org.opensleep.app.service.alarm.AlarmRingingService
import org.opensleep.app.service.alarm.AlarmScheduler
import org.opensleep.app.ui.theme.OpenSleepTheme
import org.opensleep.app.ui.theme.SleepPalette
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class AlarmRingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val alarmLabel = intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_LABEL) ?: "Đã đến giờ thức dậy"

        setContent {
            OpenSleepTheme {
                var currentTime by remember { mutableStateOf(LocalTime.now()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        currentTime = LocalTime.now()
                        delay(1000L)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SleepPalette.BackgroundDark
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 40.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header & Icon chuông
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(top = 20.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = SleepPalette.StageRem.copy(alpha = 0.15f),
                                modifier = Modifier.size(80.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.NotificationsActive,
                                        contentDescription = null,
                                        tint = SleepPalette.StageRem,
                                        modifier = Modifier.size(42.dp)
                                    )
                                }
                            }

                            Text(
                                text = alarmLabel,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SleepPalette.OnBackground,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                                fontSize = 54.sp,
                                fontWeight = FontWeight.Light,
                                color = SleepPalette.OnBackground
                            )

                            Text(
                                text = "Phiên đo vẫn đang tiếp tục. Nhấn nút bên dưới để xác nhận bạn đã tỉnh dậy và hoàn tất phiên ngủ.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SleepPalette.OnBackgroundMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        // Các nút hành động: Tắt báo thức & Dậy hẳn (chính) + Hoãn (phụ)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Button(
                                onClick = {
                                    val dismissIntent = Intent(this@AlarmRingingActivity, AlarmRingingService::class.java).apply {
                                        action = AlarmRingingService.ACTION_DISMISS
                                    }
                                    startService(dismissIntent)
                                    finish()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SleepPalette.OptimalGreen,
                                    contentColor = SleepPalette.BackgroundDark
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth().height(60.dp)
                            ) {
                                Icon(Icons.Rounded.Alarm, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Tôi đã thức dậy · Tắt báo thức",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val snoozeIntent = Intent(this@AlarmRingingActivity, AlarmRingingService::class.java).apply {
                                        action = AlarmRingingService.ACTION_SNOOZE
                                        putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                                    }
                                    startService(snoozeIntent)
                                    finish()
                                },
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = SleepPalette.OnBackgroundMuted
                                )
                            ) {
                                Text("Hoãn báo thức 9 phút (Snooze)", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}