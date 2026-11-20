package com.personalkanban.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.personalkanban.app.data.local.SettingsDataStore
import com.personalkanban.app.data.remote.GitHubUpdateService
import com.personalkanban.app.data.remote.GoogleDriveService
import com.personalkanban.app.data.remote.UpdateDownloadState
import com.personalkanban.app.data.repository.SyncRepository
import com.personalkanban.app.data.repository.TaskRepository
import com.personalkanban.app.domain.model.AppSettings
import com.personalkanban.app.domain.model.BackupSnapshotInfo
import com.personalkanban.app.domain.model.ReleaseInfo
import com.personalkanban.app.domain.model.ThemeMode
import com.personalkanban.app.notification.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val checkingUpdate: Boolean = false,
    val availableRelease: ReleaseInfo? = null,
    val updateStatusMessage: String? = null,
    val downloadProgressPercent: Int? = null,
    val downloadedApkFile: File? = null,
    val snapshots: List<BackupSnapshotInfo> = emptyList(),
    val pendingRestoreFileId: String? = null,
    val showOverwriteConfirmDialog: Boolean = false
)

sealed class SettingsEvent {
    data class ShowMessage(val message: String) : SettingsEvent()
    data class ShareFile(val file: File, val mimeType: String, val title: String) : SettingsEvent()
    data class InstallApk(val file: File) : SettingsEvent()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val taskRepository: TaskRepository,
    private val syncRepository: SyncRepository,
    private val googleDriveService: GoogleDriveService,
    private val gitHubUpdateService: GitHubUpdateService,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private val checkingUpdateFlow = MutableStateFlow(false)
    private val availableReleaseFlow = MutableStateFlow<ReleaseInfo?>(null)
    private val updateStatusFlow = MutableStateFlow<String?>(null)
    private val downloadProgressFlow = MutableStateFlow<Int?>(null)
    private val downloadedApkFlow = MutableStateFlow<File?>(null)
    private val snapshotsFlow = MutableStateFlow<List<BackupSnapshotInfo>>(emptyList())
    private val pendingRestoreFileIdFlow = MutableStateFlow<String?>(null)
    private val showOverwriteConfirmFlow = MutableStateFlow(false)

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events = _events.asSharedFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsDataStore.settingsFlow,
        checkingUpdateFlow,
        availableReleaseFlow,
        updateStatusFlow,
        downloadProgressFlow,
        downloadedApkFlow,
        snapshotsFlow,
        pendingRestoreFileIdFlow,
        showOverwriteConfirmFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        SettingsUiState(
            settings = values[0] as AppSettings,
            checkingUpdate = values[1] as Boolean,
            availableRelease = values[2] as ReleaseInfo?,
            updateStatusMessage = values[3] as String?,
            downloadProgressPercent = values[4] as Int?,
            downloadedApkFile = values[5] as File?,
            snapshots = values[6] as List<BackupSnapshotInfo>,
            pendingRestoreFileId = values[7] as String?,
            showOverwriteConfirmDialog = values[8] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    }

    fun setCompactCards(compact: Boolean) {
        viewModelScope.launch { settingsDataStore.setCompactCards(compact) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setNotificationsEnabled(enabled)
            if (enabled) reminderScheduler.rescheduleAll()
        }
    }

    fun toggleReminderInterval(minutes: Int) {
        viewModelScope.launch {
            val current = settingsDataStore.getSettings().reminderIntervalsMinutes
            val updated = if (minutes in current) current - minutes else current + minutes
            settingsDataStore.setReminderIntervals(updated)
            reminderScheduler.rescheduleAll()
        }
    }

    fun setCustomReminderMinutes(minutes: Int?) {
        viewModelScope.launch {
            settingsDataStore.setCustomReminderMinutes(minutes)
            reminderScheduler.rescheduleAll()
        }
    }

    fun getGoogleSignInIntent(): Intent = googleDriveService.getSignInIntent()

    fun handleGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                val email = account.email
                if (email.isNullOrBlank()) {
                    _events.emit(SettingsEvent.ShowMessage("Не удалось получить email Google-аккаунта"))
                } else {
                    settingsDataStore.setGoogleAccount(email)
                    _events.emit(SettingsEvent.ShowMessage("Подключён аккаунт $email"))
                    syncNow(createSnapshot = false)
                }
            } catch (e: ApiException) {
                _events.emit(SettingsEvent.ShowMessage("Ошибка входа Google (код ${e.statusCode})"))
            } catch (e: Exception) {
                _events.emit(SettingsEvent.ShowMessage(e.localizedMessage ?: "Ошибка входа Google"))
            }
        }
    }

    fun disconnectGoogleAccount() {
        viewModelScope.launch {
            googleDriveService.signOut()
            settingsDataStore.setGoogleAccount(null)
            snapshotsFlow.value = emptyList()
            _events.emit(SettingsEvent.ShowMessage("Google-аккаунт отключён"))
        }
    }

    fun syncNow(createSnapshot: Boolean = false) {
        viewModelScope.launch {
            val res = syncRepository.syncNow(createSnapshot = createSnapshot)
            res.onSuccess { conflicts ->
                val msg = if (createSnapshot) {
                    "Резервная копия создана в Google Drive"
                } else if (conflicts > 0) {
                    "Синхронизировано (конфликтов: $conflicts)"
                } else {
                    "Синхронизация завершена"
                }
                _events.emit(SettingsEvent.ShowMessage(msg))
            }.onFailure { err ->
                _events.emit(SettingsEvent.ShowMessage(err.message ?: "Ошибка синхронизации"))
            }
        }
    }

    fun verifyBackupIntegrity() {
        viewModelScope.launch {
            syncRepository.verifyLatestBackupIntegrity()
                .onSuccess { msg -> _events.emit(SettingsEvent.ShowMessage(msg)) }
                .onFailure { err -> _events.emit(SettingsEvent.ShowMessage(err.message ?: "Ошибка проверки")) }
        }
    }

    fun loadSnapshots() {
        viewModelScope.launch {
            syncRepository.listSnapshots()
                .onSuccess { list -> snapshotsFlow.value = list }
                .onFailure { err -> _events.emit(SettingsEvent.ShowMessage(err.message ?: "Не удалось загрузить список копий")) }
        }
    }

    fun requestRestore(fileId: String?, force: Boolean = false) {
        viewModelScope.launch {
            val res = syncRepository.restoreFromDriveFile(fileId, forceOverwriteNewerLocal = force)
            res.onSuccess { count ->
                showOverwriteConfirmFlow.value = false
                pendingRestoreFileIdFlow.value = null
                _events.emit(SettingsEvent.ShowMessage("Восстановлено задач: $count"))
            }.onFailure { err ->
                val msg = err.message.orEmpty()
                if (msg.startsWith("LOCAL_NEWER_EXISTS:")) {
                    pendingRestoreFileIdFlow.value = fileId
                    showOverwriteConfirmFlow.value = true
                } else {
                    _events.emit(SettingsEvent.ShowMessage(msg.ifBlank { "Ошибка восстановления" }))
                }
            }
        }
    }

    fun dismissOverwriteConfirm() {
        showOverwriteConfirmFlow.value = false
        pendingRestoreFileIdFlow.value = null
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            checkingUpdateFlow.value = true
            updateStatusFlow.value = null
            gitHubUpdateService.checkForUpdate()
                .onSuccess { release ->
                    availableReleaseFlow.value = release
                    updateStatusFlow.value = if (release == null) {
                        "Установлена последняя версия"
                    } else {
                        "Доступна версия ${release.versionName}"
                    }
                }
                .onFailure { err ->
                    updateStatusFlow.value = "Ошибка проверки: ${err.localizedMessage}"
                }
            checkingUpdateFlow.value = false
        }
    }

    fun downloadAndInstallUpdate(release: ReleaseInfo) {
        viewModelScope.launch {
            gitHubUpdateService.downloadAndVerifyApk(release).collect { state ->
                when (state) {
                    is UpdateDownloadState.Progress -> {
                        downloadProgressFlow.value = state.percent.coerceAtLeast(0)
                    }
                    is UpdateDownloadState.Completed -> {
                        downloadProgressFlow.value = null
                        downloadedApkFlow.value = state.apkFile
                        val verifiedNote = if (state.sha256Verified) " (SHA-256 проверен)" else ""
                        _events.emit(SettingsEvent.ShowMessage("Загрузка завершена$verifiedNote"))
                        _events.emit(SettingsEvent.InstallApk(state.apkFile))
                    }
                    is UpdateDownloadState.Error -> {
                        downloadProgressFlow.value = null
                        _events.emit(SettingsEvent.ShowMessage(state.message))
                    }
                }
            }
        }
    }

    fun exportJson() {
        viewModelScope.launch {
            val content = taskRepository.exportToJson()
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "personal_kanban_export.json").apply { writeText(content) }
            _events.emit(SettingsEvent.ShareFile(file, "application/json", "Экспорт JSON"))
        }
    }

    fun exportCsv() {
        viewModelScope.launch {
            val content = taskRepository.exportToCsv()
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "personal_kanban_export.csv").apply { writeText(content) }
            _events.emit(SettingsEvent.ShareFile(file, "text/csv", "Экспорт CSV"))
        }
    }

    fun importJson(rawJson: String) {
        viewModelScope.launch {
            taskRepository.importFromJson(rawJson)
                .onSuccess { count -> _events.emit(SettingsEvent.ShowMessage("Импортировано задач: $count")) }
                .onFailure { err -> _events.emit(SettingsEvent.ShowMessage("Ошибка импорта: ${err.localizedMessage}")) }
        }
    }

    fun clearLocalCache() {
        viewModelScope.launch {
            File(context.cacheDir, "updates").deleteRecursively()
            File(context.cacheDir, "exports").deleteRecursively()
            _events.emit(SettingsEvent.ShowMessage("Локальный кэш очищен"))
        }
    }

    fun createFileUri(file: File) = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}
