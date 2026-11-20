package com.personalkanban.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalkanban.app.data.repository.TaskRepository
import com.personalkanban.app.domain.model.Customer
import com.personalkanban.app.domain.model.TaskComment
import com.personalkanban.app.domain.model.TaskItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskDetailUiState(
    val task: TaskItem? = null,
    val comments: List<TaskComment> = emptyList(),
    val customers: List<Customer> = emptyList()
)

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val taskId: String = checkNotNull(savedStateHandle["taskId"])

    val uiState: StateFlow<TaskDetailUiState> = combine(
        taskRepository.observeTaskById(taskId),
        taskRepository.observeCommentsForTask(taskId),
        taskRepository.observeCustomers()
    ) { task, comments, customers ->
        TaskDetailUiState(task = task, comments = comments, customers = customers)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TaskDetailUiState()
    )

    fun saveTask(updated: TaskItem) {
        viewModelScope.launch {
            taskRepository.updateTask(updated)
        }
    }

    fun addComment(text: String) {
        viewModelScope.launch {
            taskRepository.addComment(taskId, text)
        }
    }

    fun editComment(commentId: String, newText: String) {
        viewModelScope.launch {
            taskRepository.editComment(commentId, newText)
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            taskRepository.deleteComment(commentId)
        }
    }

    fun duplicateTask(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val copy = taskRepository.duplicateTask(taskId)
            if (copy != null) onCreated(copy.id)
        }
    }

    fun archiveTask(onDone: () -> Unit) {
        viewModelScope.launch {
            taskRepository.archiveTask(taskId, true)
            onDone()
        }
    }

    fun deleteTask(onDone: () -> Unit) {
        viewModelScope.launch {
            taskRepository.softDeleteTask(taskId)
            onDone()
        }
    }
}
