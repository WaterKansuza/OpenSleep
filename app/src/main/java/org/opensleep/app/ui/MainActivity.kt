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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
            OpenSleepRootHost()
        }
    }
}

private enum class AppTab(val label: String) {
    TODAY("Hôm nay"),
    HISTORY("Chi tiết"),
    SETTINGS("Cài đặt")
}

@Composable
fun OpenSleepRootHost() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
    ) { }

    LaunchedEffect(Unit) {
        val missing = requiredPermissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    var didRunStartupUpdateCheck by remember { mutableStateOf(false) }
    LaunchedEffect(settingsState.preferences.autoCheckUpdatesEnabled) {
        if (!didRunStartupUpdateCheck && settingsState.preferences.autoCheckUpdatesEnabled) {
            didRunStartupUpdateCheck = true
            settingsVm.checkForUpdates(silent = true)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                todayVm.refreshTodayIfNeeded()
                historyVm.refreshTodayIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val visibleUpdateBanner = settingsState.updateInfo?.takeIf { info ->
        info.isUpdateAvailable && info.latestTag != settingsState.preferences.dismissedUpdateTag
    }

    OpenSleepTheme(fontKey = settingsState.preferences.fontKey) {
        Scaffold(
            containerColor = SleepPalette.BackgroundDark,
            bottomBar = {
                if (!todayState.tracking.isTracking) {
                    NavigationBar(containerColor = SleepPalette.SurfaceCard) {
                        NavigationBarItem(
                            selected = currentTab == AppTab.TODAY,
                            onClick = {
                                todayVm.refreshTodayIfNeeded()
                                currentTab = AppTab.TODAY
                            },
                            icon = { Icon(Icons.Rounded.Bedtime, contentDescription = null) },
                            label = { Text(AppTab.TODAY.label) }
                        )
                        NavigationBarItem(
                            selected = currentTab == AppTab.HISTORY,
                            onClick = {
                                historyVm.refreshTodayIfNeeded()
                                currentTab = AppTab.HISTORY
                            },
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
                    onSelectSleepMode = todayVm::selectSleepMode,
                    onUpdateBedtime = todayVm::updateCustomBedtime,
                    onResetBedtime = todayVm::resetBedtimeToRecommended,
                    onUpdateWakeTime = todayVm::updateActiveWakeTime,
                    onToggleSmartWake = todayVm::setSmartWakeEnabled,
                    onStartSleep = todayVm::startSelectedSleep,
                    onStopTracking = todayVm::stopTracking,
                    onStartPowerNap = todayVm::startQuickPowerNap,
                    onQuickFeeling = todayVm::submitQuickFeeling,
                    onSubmitCheckIn = todayVm::submitMorningCheckIn,
                    onDismissCheckIn = todayVm::dismissMorningCheckIn,
                    availableUpdate = visibleUpdateBanner,
                    onOpenUpdateSettings = { currentTab = AppTab.SETTINGS },
                    onDismissUpdate = settingsVm::dismissUpdateBanner,
                    modifier = mod
                )
                AppTab.HISTORY -> HistoryDetailRoute(
                    uiState = historyState,
                    onSelectDay = historyVm::selectDay,
                    onLogManualSleep = historyVm::logManualSleepForSelectedDay,
                    onDeleteSession = historyVm::deleteSession,
                    onSelectReportRange = historyVm::selectReportRange,
                    onDismissWarning = historyVm::dismissWarning,
                    onExportCsv = historyVm::exportCurrentReportCsv,
                    onExportPdf = historyVm::exportCurrentReportPdf,
                    onClearExportMessage = historyVm::clearExportMessage,
                    modifier = mod
                )
                AppTab.SETTINGS -> SettingsAndAlarmScreen(
                    uiState = settingsState,
                    onUpdateWeekdayWakeTime = settingsVm::updateWeekdayWakeTime,
                    onUpdateWeekendSchedule = settingsVm::updateWeekendSchedule,
                    onToggleRestDay = settingsVm::toggleRestDay,
                    onToggleOptionalCheckIn = settingsVm::setOptionalCheckInEnabled,
                    onToggleBedtimeReminder = settingsVm::setBedtimeReminderEnabled,
                    onToggleSoundClassification = settingsVm::setSoundClassificationEnabled,
                    onSelectSoundRetentionDays = settingsVm::setSoundRetentionDays,
                    onSelectFontKey = settingsVm::setFontKey,
                    onUpdateSleepNeed = settingsVm::updateTargetSleepNeed,
                    onToggleAutoCalibrate = settingsVm::toggleAutoCalibrate,
                    onSetPowerNapDuration = settingsVm::setPowerNapDuration,
                    onSetAlarmRingtone = settingsVm::setAlarmRingtone,
                    onToggleAlarm = settingsVm::toggleAlarm,
                    onDeleteAlarm = settingsVm::deleteAlarm,
                    onRunCalibration = settingsVm::runSonarCalibration,
                    onCheckUpdates = { settingsVm.checkForUpdates(silent = false) },
                    onDownloadAndInstallUpdate = settingsVm::downloadAndInstallUpdate,
                    onToggleAutoCheckUpdates = settingsVm::setAutoCheckUpdatesEnabled,
                    onExportBackup = settingsVm::exportBackup,
                    onImportBackup = settingsVm::importBackup,
                    onCheckBackupEncrypted = settingsVm::isBackupEncrypted,
                    onClearMessage = settingsVm::clearMessage,
                    modifier = mod
                )
            }
        }
    }
}