package org.opensleep.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.opensleep.app.core.util.LocalBackupManager
import org.opensleep.app.data.local.db.AgeGroup
import org.opensleep.app.data.local.db.dao.AlarmDao
import org.opensleep.app.data.local.db.dao.SleepDebtDao
import org.opensleep.app.data.local.db.entity.AlarmConfigEntity
import org.opensleep.app.data.local.db.entity.UserSleepProfileEntity
import org.opensleep.app.data.repository.SleepDebtRepository
import org.opensleep.app.service.alarm.AlarmScheduler
import javax.inject.Inject

data class SettingsUiState(
    val profile: UserSleepProfileEntity = UserSleepProfileEntity(),
    val alarms: List<AlarmConfigEntity> = emptyList(),
    val exportResult: String? = null,
    val importResult: String? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sleepDebtDao: SleepDebtDao,
    private val alarmDao: AlarmDao,
    private val sleepDebtRepository: SleepDebtRepository,
    private val backupManager: LocalBackupManager,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        sleepDebtRepository.observeProfile(),
        alarmDao.observeAllAlarms()
    ) { profile, alarms ->
        SettingsUiState(
            profile = profile ?: UserSleepProfileEntity(),
            alarms = alarms
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun clearSnackbar() { _snackbarMessage.value = null }

    fun updateSleepNeed(minutes: Int) {
        viewModelScope.launch {
            val current = sleepDebtDao.getProfile() ?: UserSleepProfileEntity()
            sleepDebtDao.upsertProfile(current.copy(targetSleepNeedMinutes = minutes))
        }
    }

    fun updateAgeGroup(ageGroup: AgeGroup) {
        viewModelScope.launch {
            val current = sleepDebtDao.getProfile() ?: UserSleepProfileEntity()
            val newNeed = ageGroup.defaultSleepNeedMinutes
            sleepDebtDao.upsertProfile(current.copy(ageGroup = ageGroup, targetSleepNeedMinutes = newNeed, calibratedSleepNeedMinutes = newNeed))
        }
    }

    fun setAutoCalibrate(enabled: Boolean) {
        viewModelScope.launch {
            val current = sleepDebtDao.getProfile() ?: UserSleepProfileEntity()
            sleepDebtDao.upsertProfile(current.copy(autoCalibrateEnabled = enabled))
        }
    }

    fun addOrUpdateAlarm(alarm: AlarmConfigEntity) {
        viewModelScope.launch {
            val id = alarmDao.upsertAlarm(alarm)
            val saved = alarm.copy(id = if (alarm.id == 0L) id else alarm.id)
            if (saved.isEnabled) alarmScheduler.scheduleNextAlarm(saved)
            else alarmScheduler.cancelAlarm(saved)
        }
    }

    fun deleteAlarm(alarm: AlarmConfigEntity) {
        viewModelScope.launch {
            alarmScheduler.cancelAlarm(alarm)
            alarmDao.deleteAlarm(alarm)
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            backupManager.exportToUri(uri).fold(
                onSuccess = { count -> _snackbarMessage.value = "Đã xuất $count phiên ngủ ✓" },
                onFailure = { _snackbarMessage.value = "Xuất thất bại: ${it.message}" }
            )
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            backupManager.importFromUri(uri).fold(
                onSuccess = { count -> _snackbarMessage.value = "Đã nhập $count phiên ngủ ✓" },
                onFailure = { _snackbarMessage.value = "Nhập thất bại: ${it.message}" }
            )
        }
    }
}
