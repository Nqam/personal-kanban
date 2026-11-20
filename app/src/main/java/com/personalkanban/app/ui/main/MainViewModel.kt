package com.personalkanban.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalkanban.app.data.local.SettingsDataStore
import com.personalkanban.app.data.remote.GitHubUpdateService
import com.personalkanban.app.data.repository.ConflictResolutionChoice
import com.personalkanban.app.data.repository.SyncRepository
import com.personalkanban.app.data.repository.TaskRepository
import com.personalkanban.app.domain.model.AppSettings
import com.personalkanban.app.domain.model.Customer
import com.personalkanban.app.domain.model.DueDateFilter
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
    val allLabels: List<String> = emptyList(),
    val selectedTaskIds: Set<String> = emptySet(),
    val conflicts: List<SyncConflict> = emptyList(),
    val unsyncedCount: Int = 0,
    val availableUpdate: ReleaseInfo? = null
)

sealed class MainUiEvent {
    data class ShowUndoSnackbar(val message: String, val onUndo: suspend () -> Unit) : MainUiEvent()
    data class ShowMessage(val message: String) : MainUiEvent()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val syncRepository: SyncRepository,
    private val settingsDataStore: SettingsDataStore,
    private val gitHubUpdateService: GitHubUpdateService
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
        availableUpdateFlow
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
            allLabels = allLabels,
            selectedTaskIds = selectedIds,
            conflicts = conflicts,
            unsyncedCount = unsyncedCount,
            availableUpdate = update
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    init {
        viewModelScope.launch {
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
        comment: String?
    ) {
        viewModelScope.launch {
            taskRepository.createTask(
                title = title,
                status = status,
                priority = priority,
                dueAt = dueAt,
                hasTime = hasTime,
                customerName = customerName,
                initialComment = comment
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

    fun resolveConflict(conflict: SyncConflict, choice: ConflictResolutionChoice) {
        viewModelScope.launch {
            syncRepository.resolveConflict(conflict, choice)
            _events.emit(MainUiEvent.ShowMessage("Конфликт разрешён"))
        }
    }
}
