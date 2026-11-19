package org.opensleep.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import org.opensleep.app.ui.dashboard.TodayScreen
import org.opensleep.app.ui.dashboard.TodayViewModel
import org.opensleep.app.ui.history.HistoryDetailRoute
import org.opensleep.app.ui.history.HistoryViewModel
import org.opensleep.app.ui.settings.SettingsAndAlarmScreen
import org.opensleep.app.ui.settings.SettingsViewModel
import org.opensleep.app.ui.theme.OpenSleepTheme
import org.opensleep.app.ui.theme.SleepPalette

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenSleepTheme {
                OpenSleepRootScaffold()
            }
        }
    }
}

private enum class AppTab(val label: String) {
    TODAY("Hôm nay"),
    HISTORY("Chi tiết"),
    SETTINGS("Cài đặt")
}

@Composable
fun OpenSleepRootScaffold() {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(AppTab.TODAY) }

    val todayVm: TodayViewModel = hiltViewModel()
    val historyVm: HistoryViewModel = hiltViewModel()
    val settingsVm: SettingsViewModel = hiltViewModel()

    val todayState by todayVm.uiState.collectAsState()
    val historyState by historyVm.uiState.collectAsState()
    val settingsState by settingsVm.uiState.collectAsState()

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Đã xử lý fallback sang Gia tốc kế nếu user từ chối Mic */ }

    LaunchedEffect(Unit) {
        val missing = requiredPermissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    Scaffold(
        containerColor = SleepPalette.BackgroundDark,
        bottomBar = {
            if (!todayState.tracking.isTracking) {
                NavigationBar(containerColor = SleepPalette.SurfaceCard) {
                    NavigationBarItem(
                        selected = currentTab == AppTab.TODAY,
                        onClick = { currentTab = AppTab.TODAY },
                        icon = { Icon(Icons.Rounded.Bedtime, contentDescription = null) },
                        label = { Text(AppTab.TODAY.label) }
                    )
                    NavigationBarItem(
                        selected = currentTab == AppTab.HISTORY,
                        onClick = { currentTab = AppTab.HISTORY },
                        icon = { Icon(Icons.Rounded.BarChart, contentDescription = null) },
                        label = { Text(AppTab.HISTORY.label) }
                    )
                    NavigationBarItem(
                        selected = currentTab == AppTab.SETTINGS,
                        onClick = { currentTab = AppTab.SETTINGS },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        label = { Text(AppTab.SETTINGS.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        val mod = Modifier.padding(innerPadding)
        when (currentTab) {
            AppTab.TODAY -> TodayScreen(
                uiState = todayState,
                liveWaveFlow = todayVm.liveWaveFlow,
                onToggleAlarmMode = todayVm::setUseAlarm,
                onSetAlarmTime = todayVm::setAlarmTime,
                onToggleSmartWake = todayVm::setSmartWakeEnabled,
                onStartNightSleep = todayVm::startNightSleep,
                onStartPowerNap = todayVm::startPowerNap,
                onStopTracking = todayVm::stopTracking,
                onSubmitCheckIn = todayVm::submitMorningCheckIn,
                onDismissCheckIn = todayVm::dismissMorningCheckIn,
                modifier = mod
            )
            AppTab.HISTORY -> HistoryDetailRoute(
                uiState = historyState,
                onSelectDay = historyVm::selectDay,
                onLogManualSleep = historyVm::logManualSleepForSelectedDay,
                modifier = mod
            )
            AppTab.SETTINGS -> SettingsAndAlarmScreen(
                uiState = settingsState,
                onUpdateSleepNeed = settingsVm::updateTargetSleepNeed,
                onToggleAutoCalibrate = settingsVm::toggleAutoCalibrate,
                onToggleAlarm = settingsVm::toggleAlarm,
                onDeleteAlarm = settingsVm::deleteAlarm,
                onRunCalibration = settingsVm::runSonarCalibration,
                onExportBackup = settingsVm::exportBackup,
                onImportBackup = settingsVm::importBackup,
                onClearMessage = settingsVm::clearMessage,
                modifier = mod
            )
        }
    }
}