package org.opensleep.app.ui.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import org.opensleep.app.service.alarm.AlarmRingingService
import org.opensleep.app.service.alarm.AlarmScheduler
import org.opensleep.app.ui.theme.OpenSleepTheme
import org.opensleep.app.ui.theme.SleepPalette

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

        setContent {
            OpenSleepTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SleepPalette.BackgroundDark
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Báo thức OpenSleep",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = SleepPalette.OnBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Chạm để hoãn 9 phút hoặc tắt hẳn để lưu giấc ngủ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SleepPalette.OnBackgroundMuted
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = {
                                val snoozeIntent = Intent(this@AlarmRingingActivity, AlarmRingingService::class.java).apply {
                                    action = AlarmRingingService.ACTION_SNOOZE
                                    putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                                }
                                startService(snoozeIntent)
                                finish()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SleepPalette.SurfaceElevated,
                                contentColor = SleepPalette.OnBackground
                            ),
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Text("Hoãn 9 phút (Snooze)", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                val dismissIntent = Intent(this@AlarmRingingActivity, AlarmRingingService::class.java).apply {
                                    action = AlarmRingingService.ACTION_DISMISS
                                }
                                startService(dismissIntent)
                                finish()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SleepPalette.PrimaryBlue,
                                contentColor = SleepPalette.BackgroundDark
                            ),
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Text("Tắt báo thức & Dậy hẳn", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}