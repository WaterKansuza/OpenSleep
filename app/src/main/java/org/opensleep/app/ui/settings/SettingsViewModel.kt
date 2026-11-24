package org.opensleep.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.opensleep.app.core.util.AppUpdateInfo
import org.opensleep.app.core.util.DownloadUpdateState
import org.opensleep.app.core.util.GitHubUpdateManager
import org.opensleep.app.core.util.LocalBackupManager
import org.opensleep.app.data.local.db.dao.AlarmDao
import org.opensleep.app.data.local.db.dao.SleepDebtDao
import org.opensleep.app.data.local.db.dao.SleepSessionDao
import org.opensleep.app.data.local.db.entity.AlarmConfigEntity
import org.opensleep.app.data.local.db.entity.UserSleepProfileEntity
import org.opensleep.app.data.repository.SleepDebtRepository
import org.opensleep.app.data.repository.UserPreferencesRepository
import org.opensleep.app.data.repository.UserPreferencesState
import org.opensleep.app.domain.engine.WakeTimePlannerEngine
import org.opensleep.app.service.alarm.AlarmScheduler
import org.opensleep.app.service.alarm.BedtimeReminderScheduler
import org.opensleep.app.service.tracking.SonarNativeBridge
import java.time.LocalDate
import javax.inject.Inject

private data class SettingsExtraState(
    val isCalibrating: Boolean = false,
    val lastCalibSnrDb: Float? = null,
    val statusMessage: String? = null,
    val isCheckingUpdate: Boolean = false,
    val updateInfo: AppUpdateInfo? = null,
    val downloadState: DownloadUpdateState? = null
)

data class SettingsUiState(
    val profile: UserSleepProfileEntity = UserSleepProfileEntity(),
    val preferences: UserPreferencesState = UserPreferencesState(),
    val alarms: List<AlarmConfigEntity> = emptyList(),
    val isCalibrating: Boolean = false,
    val lastCalibSnrDb: Float? = null,
    val statusMessage: String? = null,
    val isCheckingUpdate: Boolean = false,
    val updateInfo: AppUpdateInfo? = null,
    val downloadState: DownloadUpdateState? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sleepDebtDao: SleepDebtDao,
    private val sleepSessionDao: SleepSessionDao,
    private val sleepDebtRepository: SleepDebtRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val wakeTimePlannerEngine: WakeTimePlannerEngine,
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler,
    private val bedtimeReminderScheduler: BedtimeReminderScheduler,
    private val sonarBridge: SonarNativeBridge,
    private val backupManager: LocalBackupManager,
    private val gitHubUpdateManager: GitHubUpdateManager
) : ViewModel() {

    private val extraState = MutableStateFlow(SettingsExtraState())

    val uiState: StateFlow<SettingsUiState> = combine(
        sleepDebtDao.observeProfile(),
        userPreferencesRepository.preferencesFlow,
        alarmDao.observeAlarms(),
        extraState
    ) { profile, prefs, alarms, extra ->
        SettingsUiState(
            profile = profile ?: UserSleepProfileEntity(),
            preferences = prefs,
            alarms = alarms,
            isCalibrating = extra.isCalibrating,
            lastCalibSnrDb = extra.lastCalibSnrDb,
            statusMessage = extra.statusMessage,
            isCheckingUpdate = extra.isCheckingUpdate,
            updateInfo = extra.updateInfo,
            downloadState = extra.downloadState
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun updateWeekdayWakeTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setWeekdayWakeTime(hour, minute)
            syncBedtimeReminder()
        }
    }

    fun updateWeekendSchedule(enabled: Boolean, hour: Int? = null, minute: Int? = null) {
        viewModelScope.launch {
            userPreferencesRepository.setWeekendSchedule(enabled, hour, minute)
            syncBedtimeReminder()
        }
    }

    fun toggleRestDay(dayIndex: Int) {
        viewModelScope.launch {
            val currentMask = uiState.value.preferences.restDaysBitmask
            val updated = currentMask xor (1 shl dayIndex)
            userPreferencesRepository.setRestDaysBitmask(updated)
            syncBedtimeReminder()
        }
    }

    fun setOptionalCheckInEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setOptionalCheckInEnabled(enabled) }
    }

    fun setFontKey(fontKey: String) {
        viewModelScope.launch { userPreferencesRepository.setFontKey(fontKey) }
    }

    fun setSoundClassificationEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setSoundClassificationEnabled(enabled) }
    }

    fun setSoundRetentionDays(days: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setSoundRetentionDays(days)
            if (days > 0) {
                val cutoffDay = LocalDate.now().toEpochDay() - days
                val deleted = sleepSessionDao.deleteSoundEventsBeforeDay(cutoffDay)
                if (deleted > 0) {
                    extraState.update { it.copy(statusMessage = "Đã dọn $deleted sự kiện âm thanh cũ hơn $days ngày.") }
                }
            }
        }
    }

    fun setBedtimeReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBedtimeReminderEnabled(enabled)
            syncBedtimeReminder()
        }
    }

    private suspend fun syncBedtimeReminder() {
        val prefs = userPreferencesRepository.getSnapshot()
        val profile = sleepDebtDao.getProfile() ?: UserSleepProfileEntity()
        val today = LocalDate.now()
        val todayEpoch = today.toEpochDay()
        val rec = wakeTimePlannerEngine.computeRecommendation(
            prefs = prefs,
            personalSleepNeedMinutes = if (profile.autoCalibrateEnabled) profile.calibratedSleepNeedMinutes else profile.targetSleepNeedMinutes,
            recentSessions = sleepSessionDao.getSessionsInRange(todayEpoch - 21, todayEpoch),
            estimatedDebtMinutes = 0,
            today = today
        )
        bedtimeReminderScheduler.syncReminder(prefs.bedtimeReminderEnabled, rec.recommendedBedtimeMinutesOfDay)
    }

    fun setPowerNapDuration(minutes: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setPowerNapDuration(minutes)
        }
    }

    fun setAlarmRingtone(uri: String?, title: String?) {
        viewModelScope.launch {
            userPreferencesRepository.setAlarmRingtone(uri, title)
        }
    }

    fun setAutoCheckUpdatesEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setAutoCheckUpdatesEnabled(enabled) }
    }

    fun checkForUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            if (extraState.value.isCheckingUpdate) return@launch
            extraState.update { it.copy(isCheckingUpdate = true) }
            val result = gitHubUpdateManager.checkLatestRelease()
            result.fold(
                onSuccess = { info ->
                    extraState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            updateInfo = info,
                            statusMessage = if (silent) it.statusMessage else {
                                if (info.isUpdateAvailable) {
                                    "Phát hiện bản mới ${info.latestTag} trên GitHub!"
                                } else {
                                    "Bạn đang dùng phiên bản mới nhất (${info.currentVersion})."
                                }
                            }
                        )
                    }
                },
                onFailure = { err ->
                    extraState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            statusMessage = if (silent) it.statusMessage else "Không thể kiểm tra GitHub: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    fun dismissUpdateBanner() {
        viewModelScope.launch {
            val tag = extraState.value.updateInfo?.latestTag
            userPreferencesRepository.setDismissedUpdateTag(tag)
        }
    }

    fun downloadAndInstallUpdate(context: Context) {
        val info = extraState.value.updateInfo ?: return
        val apkUrl = info.apkDownloadUrl
        if (apkUrl.isNullOrBlank()) {
            extraState.update { it.copy(statusMessage = "Release ${info.latestTag} chưa có tệp .apk đính kèm.") }
            return
        }
        if (!gitHubUpdateManager.canRequestPackageInstalls()) {
            context.startActivity(gitHubUpdateManager.buildUnknownSourcePermissionIntent())
            extraState.update {
                it.copy(statusMessage = "Hãy bật 'Cho phép từ nguồn này' cho OpenSleep rồi bấm Cài đặt lại.")
            }
            return
        }
        viewModelScope.launch {
            gitHubUpdateManager.downloadApk(apkUrl, info.latestTag).collect { state ->
                extraState.update { it.copy(downloadState = state) }
                if (state is DownloadUpdateState.ReadyToInstall) {
                    runCatching {
                        context.startActivity(gitHubUpdateManager.buildInstallApkIntent(state.apkFile))
                    }.onFailure { err ->
                        extraState.update { it.copy(statusMessage = "Không mở được trình cài đặt APK: ${err.message}") }
                    }
                } else if (state is DownloadUpdateState.Error) {
                    extraState.update { it.copy(statusMessage = state.message) }
                }
            }
        }
    }

    fun updateTargetSleepNeed(minutes: Int) {
        viewModelScope.launch {
            val cur = sleepDebtDao.getProfile() ?: UserSleepProfileEntity()
            sleepDebtDao.upsertProfile(cur.copy(targetSleepNeedMinutes = minutes))
            sleepDebtRepository.recalculateAfterSessionChange(LocalDate.now().toEpochDay())
        }
    }

    fun toggleAutoCalibrate(enabled: Boolean) {
        viewModelScope.launch {
            val cur = sleepDebtDao.getProfile() ?: UserSleepProfileEntity()
            sleepDebtDao.upsertProfile(cur.copy(autoCalibrateEnabled = enabled))
            sleepDebtRepository.recalculateAfterSessionChange(LocalDate.now().toEpochDay())
        }
    }

    fun toggleAlarm(alarm: AlarmConfigEntity, enabled: Boolean) {
        viewModelScope.launch {
            val updated = alarm.copy(isEnabled = enabled)
            alarmDao.upsertAlarm(updated)
            alarmScheduler.scheduleAlarm(updated)
        }
    }

    fun deleteAlarm(alarm: AlarmConfigEntity) {
        viewModelScope.launch {
            alarmScheduler.cancelAlarm(alarm.id)
            alarmDao.deleteAlarm(alarm)
        }
    }

    fun runSonarCalibration() {
        viewModelScope.launch {
            extraState.update { it.copy(isCalibrating = true) }
            val (supported, snr) = sonarBridge.runHardwareCalibrationTest()
            val cur = sleepDebtDao.getProfile() ?: UserSleepProfileEntity()
            sleepDebtDao.upsertProfile(cur.copy(sonarHardwareSupported = supported))
            val msg = if (supported) {
                "Đạt chuẩn Sonar siêu âm!"
            } else {
                "Phần cứng hạn chế >18kHz, tự dùng Passive Mode."
            }
            extraState.update {
                it.copy(
                    isCalibrating = false,
                    lastCalibSnrDb = snr,
                    statusMessage = msg
                )
            }
        }
    }

    fun exportBackup(uri: Uri, passphrase: String? = null) {
        viewModelScope.launch {
            val res = backupManager.exportToUri(uri, passphrase)
            val msg = res.fold(
                onSuccess = {
                    val encLabel = if (!passphrase.isNullOrBlank()) " (đã mã hóa AES-256-GCM)" else ""
                    "Đã xuất $it phiên ngủ ra tệp JSON$encLabel."
                },
                onFailure = { "Lỗi xuất dữ liệu: ${it.message}" }
            )
            extraState.update { it.copy(statusMessage = msg) }
        }
    }

    suspend fun isBackupEncrypted(uri: Uri): Boolean = backupManager.isEncryptedBackup(uri)

    fun importBackup(uri: Uri, passphrase: String? = null) {
        viewModelScope.launch {
            val res = backupManager.importFromUri(uri, passphrase)
            val msg = res.fold(
                onSuccess = { "Đã khôi phục $it phiên ngủ thành công." },
                onFailure = { "Lỗi nhập dữ liệu: ${it.message}" }
            )
            extraState.update { it.copy(statusMessage = msg) }
        }
    }

    fun clearMessage() {
        extraState.update { it.copy(statusMessage = null) }
    }
}