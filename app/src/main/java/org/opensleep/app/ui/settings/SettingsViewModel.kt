package org.opensleep.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.opensleep.app.core.util.LocalBackupManager
import org.opensleep.app.data.local.db.dao.AlarmDao
import org.opensleep.app.data.local.db.dao.SleepDebtDao
import org.opensleep.app.data.local.db.entity.AlarmConfigEntity
import org.opensleep.app.data.local.db.entity.UserSleepProfileEntity
import org.opensleep.app.data.repository.SleepDebtRepository
import org.opensleep.app.data.repository.UserPreferencesRepository
import org.opensleep.app.data.repository.UserPreferencesState
import org.opensleep.app.service.alarm.AlarmScheduler
import org.opensleep.app.service.tracking.SonarNativeBridge
import java.time.LocalDate
import javax.inject.Inject

private data class SettingsExtraState(
    val isCalibrating: Boolean = false,
    val lastCalibSnrDb: Float? = null,
    val statusMessage: String? = null
)

data class SettingsUiState(
    val profile: UserSleepProfileEntity = UserSleepProfileEntity(),
    val preferences: UserPreferencesState = UserPreferencesState(),
    val alarms: List<AlarmConfigEntity> = emptyList(),
    val isCalibrating: Boolean = false,
    val lastCalibSnrDb: Float? = null,
    val statusMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sleepDebtDao: SleepDebtDao,
    private val sleepDebtRepository: SleepDebtRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler,
    private val sonarBridge: SonarNativeBridge,
    private val backupManager: LocalBackupManager
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
            statusMessage = extra.statusMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun updateWeekdayWakeTime(hour: Int, minute: Int) {
        viewModelScope.launch { userPreferencesRepository.setWeekdayWakeTime(hour, minute) }
    }

    fun updateWeekendSchedule(enabled: Boolean, hour: Int? = null, minute: Int? = null) {
        viewModelScope.launch { userPreferencesRepository.setWeekendSchedule(enabled, hour, minute) }
    }

    fun setOptionalCheckInEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setOptionalCheckInEnabled(enabled) }
    }

    fun setFontKey(fontKey: String) {
        viewModelScope.launch { userPreferencesRepository.setFontKey(fontKey) }
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
            extraState.value = SettingsExtraState(isCalibrating = true)
            val (supported, snr) = sonarBridge.runHardwareCalibrationTest()
            val cur = sleepDebtDao.getProfile() ?: UserSleepProfileEntity()
            sleepDebtDao.upsertProfile(cur.copy(sonarHardwareSupported = supported))
            val msg = if (supported) {
                "Đạt chuẩn Sonar siêu âm!"
            } else {
                "Phần cứng hạn chế >18kHz, tự dùng Passive Mode."
            }
            extraState.value = SettingsExtraState(
                isCalibrating = false,
                lastCalibSnrDb = snr,
                statusMessage = msg
            )
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val res = backupManager.exportToUri(uri)
            val msg = res.fold(
                onSuccess = { "Đã xuất $it phiên ngủ ra tệp JSON cục bộ." },
                onFailure = { "Lỗi xuất dữ liệu: ${it.message}" }
            )
            extraState.update { it.copy(statusMessage = msg) }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val res = backupManager.importFromUri(uri)
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