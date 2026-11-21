package com.personalkanban.app.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.personalkanban.app.data.local.SettingsDataStore
import com.personalkanban.app.data.remote.GitHubUpdateService
import com.personalkanban.app.data.remote.GoogleDriveService
import dagger.hilt.android.qualifiers.ApplicationContext
import com.personalkanban.app.data.repository.ConflictResolutionChoice
import com.personalkanban.app.data.repository.SyncRepository
import com.personalkanban.app.data.repository.TaskRepository
import com.personalkanban.app.domain.model.AppSettings
import com.personalkanban.app.domain.model.Customer
import com.personalkanban.app.domain.model.DueDateFilter
import com.personalkanban.app.domain.model.Epic
import com.personalkanban.app.domain.model.ReleaseInfo
import com.personalkanban.app.domain.model.SortOption
import com.personalkanban.app.domain.model.SyncConflict
import com.personalkanban.app.domain.model.TaskFilterEngine
import com.personalkanban.app.domain.model.TaskFilterState
import com.personalkanban.app.domain.model.TaskItem
import com.personalkanban.app.domain.model.TaskPriority
import com.personalkanban.app.domain.model.TaskStatus
import com.personalkanban.app.domain.model.TaskViewTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainStats(
    val activeCount: Int = 0,
    val todayCount: Int = 0,
    val overdueCount: Int = 0,
    val doneCount: Int = 0
)

data class MainUiState(
    val groupedTasks: Map<TaskStatus, List<TaskItem>> = emptyMap(),
    val filterState: TaskFilterState = TaskFilterState(),
    val stats: MainStats = MainStats(),
    val settings: AppSettings = AppSettings(),
    val collapsedStatuses: Set<String> = emptySet(),
    val customers: List<Customer> = emptyList(),
    val epics: List<Epic> = emptyList(),
    val allLabels: List<String> = emptyList(),
    val selectedTaskIds: Set<String> = emptySet(),
    val conflicts: List<SyncConflict> = emptyList(),
    val unsyncedCount: Int = 0,
    val availableUpdate: ReleaseInfo? = null,
    val showDriveOnboarding: Boolean = false,
    val backupFileUri: String? = null
)

sealed class MainUiEvent {
    data class ShowUndoSnackbar(val message: String, val onUndo: suspend () -> Unit) : MainUiEvent()
    data class ShowMessage(val message: String) : MainUiEvent()
    data class SendDumpToDrive(val file: java.io.File) : MainUiEvent()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskRepository: TaskRepository,
    private val syncRepository: SyncRepository,
    private val settingsDataStore: SettingsDataStore,
    private val gitHubUpdateService: GitHubUpdateService,
    private val googleDriveService: GoogleDriveService
) : ViewModel() {

    private val filterFlow = MutableStateFlow(TaskFilterState())
    private val selectedIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val availableUpdateFlow = MutableStateFlow<ReleaseInfo?>(null)

    private val _events = MutableSharedFlow<MainUiEvent>()
    val events = _events.asSharedFlow()

    val uiState: StateFlow<MainUiState> = combine(
        taskRepository.observeActiveTasks(),
        taskRepository.observeAllCommentsByTask(),
        filterFlow,
        settingsDataStore.settingsFlow,
        settingsDataStore.collapsedStatusesFlow,
        taskRepository.observeCustomers(),
        selectedIdsFlow,
        syncRepository.observeUnresolvedConflicts(),
        taskRepository.observeUnsyncedCount(),
        availableUpdateFlow,
        settingsDataStore.drivePromptDismissedFlow,
        settingsDataStore.backupFileUriFlow,
        taskRepository.observeEpics()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val tasks = values[0] as List<TaskItem>
        @Suppress("UNCHECKED_CAST")
        val commentsMap = values[1] as Map<String, List<com.personalkanban.app.domain.model.TaskComment>>
        val filter = values[2] as TaskFilterState
        val settings = values[3] as AppSettings
        @Suppress("UNCHECKED_CAST")
        val collapsed = values[4] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val customers = values[5] as List<Customer>
        @Suppress("UNCHECKED_CAST")
        val selectedIds = values[6] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val conflicts = values[7] as List<SyncConflict>
        val unsyncedCount = values[8] as Int
        val update = values[9] as ReleaseInfo?
        val promptDismissed = values[10] as Boolean
        val backupFileUri = values[11] as String?
        @Suppress("UNCHECKED_CAST")
        val epics = values[12] as List<Epic>

        val now = System.currentTimeMillis()
        val stats = MainStats(
            activeCount = tasks.count { it.status != TaskStatus.DONE },
            todayCount = tasks.count { it.status != TaskStatus.DONE && TaskFilterEngine.isDueToday(it, now) },
            overdueCount = tasks.count { TaskFilterEngine.isOverdue(it, now) },
            doneCount = tasks.count { it.status == TaskStatus.DONE }
        )

        val filtered = TaskFilterEngine.filterAndSort(tasks, filter, commentsMap, now)
        val grouped = TaskFilterEngine.groupByStatus(filtered)
        val allLabels = tasks.flatMap { it.labels }.distinct().sorted()

        MainUiState(
            groupedTasks = grouped,
            filterState = filter,
            stats = stats,
            settings = settings,
            collapsedStatuses = collapsed,
            customers = customers,
            epics = epics,
            allLabels = allLabels,
            selectedTaskIds = selectedIds,
            conflicts = conflicts,
            unsyncedCount = unsyncedCount,
            availableUpdate = update,
            showDriveOnboarding = !promptDismissed && settings.googleAccountEmail == null && backupFileUri.isNullOrBlank(),
            backupFileUri = backupFileUri
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    init {
        viewModelScope.launch {
            taskRepository.autoArchiveOldCompletedTasks()
            val settings = settingsDataStore.getSettings()
            if (settings.googleAccountEmail != null) {
                syncRepository.syncNow(createSnapshot = false)
            }
        }
        viewModelScope.launch {
            gitHubUpdateService.checkForUpdate().onSuccess { release ->
                availableUpdateFlow.value = release
            }
        }
    }

    fun dismissUpdateBanner() {
        availableUpdateFlow.value = null
    }

    fun selectTab(tab: TaskViewTab) {
        filterFlow.update { it.copy(selectedTab = tab) }
    }

    fun updateSearchQuery(query: String) {
        filterFlow.update { it.copy(query = query) }
    }

    fun updateFilter(transform: (TaskFilterState) -> TaskFilterState) {
        filterFlow.update(transform)
    }

    fun clearFilters() {
        filterFlow.update {
            TaskFilterState(selectedTab = it.selectedTab, query = it.query, sortOption = it.sortOption)
        }
    }

    fun setSortOption(option: SortOption) {
        filterFlow.update { it.copy(sortOption = option) }
    }

    fun toggleStatusGroup(status: TaskStatus) {
        viewModelScope.launch {
            settingsDataStore.toggleCollapsedStatus(status.name)
        }
    }

    fun quickCreateInline(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            taskRepository.createTask(title = title)
        }
    }

    fun createTask(
        title: String,
        status: TaskStatus,
        priority: TaskPriority,
        dueAt: Long?,
        hasTime: Boolean,
        customerName: String?,
        epicName: String?,
        comment: String?,
        attachmentUris: List<Uri> = emptyList()
    ) {
        viewModelScope.launch {
            taskRepository.createTask(
                title = title,
                status = status,
                priority = priority,
                dueAt = dueAt,
                hasTime = hasTime,
                customerName = customerName,
                epicName = epicName,
                initialComment = comment,
                attachmentUris = attachmentUris
            )
        }
    }

    fun completeTask(task: TaskItem) {
        viewModelScope.launch {
            val previous = taskRepository.changeStatus(task.id, TaskStatus.DONE) ?: return@launch
            _events.emit(
                MainUiEvent.ShowUndoSnackbar("Задача «${task.title}» выполнена") {
                    taskRepository.restoreSnapshot(previous)
                }
            )
        }
    }

    fun deleteTask(task: TaskItem) {
        viewModelScope.launch {
            taskRepository.softDeleteTask(task.id)
            _events.emit(
                MainUiEvent.ShowUndoSnackbar("Задача перемещена в корзину") {
                    taskRepository.restoreFromTrash(task.id)
                }
            )
        }
    }

    fun changeTaskStatus(taskId: String, status: TaskStatus) {
        viewModelScope.launch {
            taskRepository.changeStatus(taskId, status)
        }
    }

    fun duplicateTask(taskId: String) {
        viewModelScope.launch {
            taskRepository.duplicateTask(taskId)
            _events.emit(MainUiEvent.ShowMessage("Создана копия задачи"))
        }
    }

    fun archiveTask(task: TaskItem) {
        viewModelScope.launch {
            taskRepository.archiveTask(task.id, true)
            _events.emit(
                MainUiEvent.ShowUndoSnackbar("Задача перемещена в архив") {
                    taskRepository.archiveTask(task.id, false)
                }
            )
        }
    }

    fun toggleSelection(taskId: String) {
        selectedIdsFlow.update { current ->
            if (taskId in current) current - taskId else current + taskId
        }
    }

    fun clearSelection() {
        selectedIdsFlow.value = emptySet()
    }

    fun bulkChangeStatus(status: TaskStatus) {
        val ids = selectedIdsFlow.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            taskRepository.bulkUpdateStatus(ids, status)
            selectedIdsFlow.value = emptySet()
        }
    }

    fun bulkChangePriority(priority: TaskPriority) {
        val ids = selectedIdsFlow.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            taskRepository.bulkUpdatePriority(ids, priority)
            selectedIdsFlow.value = emptySet()
        }
    }

    fun bulkDelete() {
        val ids = selectedIdsFlow.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { taskRepository.softDeleteTask(it) }
            selectedIdsFlow.value = emptySet()
            _events.emit(MainUiEvent.ShowMessage("Удалено задач: ${ids.size}"))
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            val currentUri = uiState.value.backupFileUri
            if (!currentUri.isNullOrBlank()) {
                taskRepository.writeBackupToSafUri(Uri.parse(currentUri))
                    .onSuccess {
                        _events.emit(MainUiEvent.ShowMessage("Дамп сохранён в Google Диск"))
                    }
                    .onFailure { err ->
                        _events.emit(MainUiEvent.ShowMessage("Ошибка сохранения дампа: ${err.localizedMessage}"))
                    }
                return@launch
            }
            val result = syncRepository.syncNow(createSnapshot = false)
            result.onSuccess { conflictsCount ->
                val msg = if (conflictsCount > 0) {
                    "Синхронизировано (конфликтов: $conflictsCount)"
                } else {
                    "Синхронизация завершена"
                }
                _events.emit(MainUiEvent.ShowMessage(msg))
            }.onFailure { err ->
                _events.emit(MainUiEvent.ShowMessage(err.message ?: "Ошибка синхронизации"))
            }
        }
    }

    fun sendDumpToGoogleDrive() {
        viewModelScope.launch {
            runCatching {
                taskRepository.createDumpFileForShare()
            }.onSuccess { file ->
                settingsDataStore.setDrivePromptDismissed(true)
                _events.emit(MainUiEvent.SendDumpToDrive(file))
            }.onFailure { err ->
                _events.emit(MainUiEvent.ShowMessage("Ошибка подготовки дампа: ${err.localizedMessage}"))
            }
        }
    }

    fun restoreFromSafUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Не удалось прочитать файл дампа")
            }.onSuccess { rawJson ->
                taskRepository.importFromJson(rawJson)
                    .onSuccess { count ->
                        _events.emit(MainUiEvent.ShowMessage("Восстановлено задач из дампа: $count"))
                    }
                    .onFailure { err ->
                        _events.emit(MainUiEvent.ShowMessage("Ошибка формата дампа: ${err.localizedMessage}"))
                    }
            }.onFailure { err ->
                _events.emit(MainUiEvent.ShowMessage("Ошибка чтения файла: ${err.localizedMessage}"))
            }
        }
    }

    fun resolveConflict(conflict: SyncConflict, choice: ConflictResolutionChoice) {
        viewModelScope.launch {
            syncRepository.resolveConflict(conflict, choice)
            _events.emit(MainUiEvent.ShowMessage("Конфликт разрешён"))
        }
    }

    fun dismissDriveOnboarding() {
        viewModelScope.launch {
            settingsDataStore.setDrivePromptDismissed(true)
        }
    }

    fun bindSafBackupUri(uri: Uri) {
        viewModelScope.launch {
            settingsDataStore.setBackupFileUri(uri.toString())
            settingsDataStore.setDrivePromptDismissed(true)
            taskRepository.writeBackupToSafUri(uri)
                .onSuccess {
                    _events.emit(MainUiEvent.ShowMessage("Файл резервной копии привязан. Автосохранение включено!"))
                }
                .onFailure { err ->
                    _events.emit(MainUiEvent.ShowMessage("Ошибка записи копии: ${err.localizedMessage}"))
                }
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
                    _events.emit(MainUiEvent.ShowMessage("Не удалось получить email Google-аккаунта"))
                } else {
                    settingsDataStore.setGoogleAccount(email)
                    settingsDataStore.setDrivePromptDismissed(true)
                    _events.emit(MainUiEvent.ShowMessage("Подключён аккаунт $email"))
                    syncNow()
                }
            } catch (e: ApiException) {
                _events.emit(MainUiEvent.ShowMessage("Ошибка входа Google (код ${e.statusCode})"))
            } catch (e: Exception) {
                _events.emit(MainUiEvent.ShowMessage(e.localizedMessage ?: "Ошибка входа Google"))
            }
        }
    }
}
