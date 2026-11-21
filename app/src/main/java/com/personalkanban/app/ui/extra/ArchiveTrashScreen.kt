package com.personalkanban.app.ui.extra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalkanban.app.data.repository.TaskRepository
import com.personalkanban.app.domain.model.TaskItem
import com.personalkanban.app.ui.components.DateTimeFormatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArchiveTrashUiState(
    val archived: List<TaskItem> = emptyList(),
    val deleted: List<TaskItem> = emptyList()
)

@HiltViewModel
class ArchiveTrashViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {
    val uiState: StateFlow<ArchiveTrashUiState> = combine(
        taskRepository.observeArchivedTasks(),
        taskRepository.observeDeletedTasks()
    ) { archived, deleted ->
        ArchiveTrashUiState(archived, deleted)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ArchiveTrashUiState())

    fun unarchive(taskId: String) = viewModelScope.launch { taskRepository.archiveTask(taskId, false) }
    fun moveToTrash(taskId: String) = viewModelScope.launch { taskRepository.softDeleteTask(taskId) }
    fun restoreFromTrash(taskId: String) = viewModelScope.launch { taskRepository.restoreFromTrash(taskId) }
    fun emptyTrash() = viewModelScope.launch { taskRepository.emptyTrash() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveTrashScreen(
    viewModel: ArchiveTrashViewModel,
    onBack: () -> Unit,
    onOpenTask: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Старые задачи и корзина") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (selectedTab == 1 && state.deleted.isNotEmpty()) {
                        TextButton(onClick = viewModel::emptyTrash) {
                            Text("Очистить", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Старые / Архив (${state.archived.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Корзина (${state.deleted.size})") })
            }

            if (selectedTab == 0) {
                Text(
                    text = "Выполненные задачи автоматически перемещаются сюда через 7 дней после завершения.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            val itemsList = if (selectedTab == 0) state.archived else state.deleted
            if (itemsList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (selectedTab == 0) "Старых задач пока нет" else "Корзина пуста",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(itemsList, key = { it.id }) { task ->
                        Surface(
                            onClick = { onOpenTask(task.id) },
                            shape = RoundedCornerShape(14.dp),
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(task.title, style = MaterialTheme.typography.titleSmall)
                                    val completedSuffix = task.completedAt?.let {
                                        " · Завершено ${DateTimeFormatters.formatTimestamp(it)}"
                                    }.orEmpty()
                                    Text(
                                        text = "${task.status.displayName} · ${task.priority.displayName}$completedSuffix",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selectedTab == 0) {
                                    IconButton(onClick = { viewModel.unarchive(task.id) }) {
                                        Icon(Icons.Outlined.Unarchive, contentDescription = "Вернуть из архива")
                                    }
                                    IconButton(onClick = { viewModel.moveToTrash(task.id) }) {
                                        Icon(Icons.Outlined.DeleteForever, contentDescription = "В корзину", tint = MaterialTheme.colorScheme.error)
                                    }
                                } else {
                                    IconButton(onClick = { viewModel.restoreFromTrash(task.id) }) {
                                        Icon(Icons.Outlined.RestoreFromTrash, contentDescription = "Восстановить")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
