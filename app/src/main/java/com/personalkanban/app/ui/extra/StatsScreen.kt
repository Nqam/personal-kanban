package com.personalkanban.app.ui.extra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalkanban.app.data.repository.TaskRepository
import com.personalkanban.app.domain.model.TaskFilterEngine
import com.personalkanban.app.domain.model.TaskPriority
import com.personalkanban.app.domain.model.TaskStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StatsUiState(
    val totalActive: Int = 0,
    val completed: Int = 0,
    val overdue: Int = 0,
    val dueToday: Int = 0,
    val byStatus: Map<TaskStatus, Int> = emptyMap(),
    val byPriority: Map<TaskPriority, Int> = emptyMap()
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    taskRepository: TaskRepository
) : ViewModel() {
    val uiState: StateFlow<StatsUiState> = taskRepository.observeActiveTasks().map { tasks ->
        val now = System.currentTimeMillis()
        StatsUiState(
            totalActive = tasks.count { it.status != TaskStatus.DONE },
            completed = tasks.count { it.status == TaskStatus.DONE },
            overdue = tasks.count { TaskFilterEngine.isOverdue(it, now) },
            dueToday = tasks.count { it.status != TaskStatus.DONE && TaskFilterEngine.isDueToday(it, now) },
            byStatus = TaskStatus.entries.associateWith { s -> tasks.count { it.status == s } },
            byPriority = TaskPriority.entries.associateWith { p -> tasks.count { it.priority == p && it.status != TaskStatus.DONE } }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Статистика") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Активных", state.totalActive.toString(), Modifier.weight(1f))
                StatCard("Выполнено", state.completed.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("На сегодня", state.dueToday.toString(), Modifier.weight(1f))
                StatCard("Просрочено", state.overdue.toString(), Modifier.weight(1f))
            }

            Text("По статусам", style = MaterialTheme.typography.titleMedium)
            TaskStatus.entries.forEach { s ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(s.displayName, style = MaterialTheme.typography.bodyMedium)
                    Text("${state.byStatus[s] ?: 0}", style = MaterialTheme.typography.titleSmall)
                }
            }

            Text("Активные по приоритетам", style = MaterialTheme.typography.titleMedium)
            TaskPriority.entries.forEach { p ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(p.displayName, style = MaterialTheme.typography.bodyMedium)
                    Text("${state.byPriority[p] ?: 0}", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
