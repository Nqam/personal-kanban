package com.personalkanban.app.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object TaskFilterEngine {

    fun isOverdue(
        task: TaskItem,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val due = task.dueAt ?: return false
        if (task.status == TaskStatus.DONE) return false
        return if (task.hasTime) {
            due < nowMillis
        } else {
            val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
            val dueDate = Instant.ofEpochMilli(due).atZone(zoneId).toLocalDate()
            dueDate.isBefore(today)
        }
    }

    fun isDueToday(
        task: TaskItem,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val due = task.dueAt ?: return false
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val dueDate = Instant.ofEpochMilli(due).atZone(zoneId).toLocalDate()
        return dueDate == today
    }

    fun isDueTomorrow(
        task: TaskItem,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val due = task.dueAt ?: return false
        val tomorrow = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate().plusDays(1)
        val dueDate = Instant.ofEpochMilli(due).atZone(zoneId).toLocalDate()
        return dueDate == tomorrow
    }

    fun isDueThisWeek(
        task: TaskItem,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val due = task.dueAt ?: return false
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val weekEnd = today.plusDays(7)
        val dueDate = Instant.ofEpochMilli(due).atZone(zoneId).toLocalDate()
        return !dueDate.isBefore(today) && !dueDate.isAfter(weekEnd)
    }

    fun filterAndSort(
        tasks: List<TaskItem>,
        filter: TaskFilterState,
        commentsByTaskId: Map<String, List<TaskComment>> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<TaskItem> {
        val normalizedQuery = filter.query.trim().lowercase()

        val filtered = tasks.asSequence()
            .filter { !it.isDeleted && !it.isArchived }
            .filter { task -> matchesTab(task, filter.selectedTab, nowMillis, zoneId) }
            .filter { task ->
                if (filter.statuses.isEmpty()) true else task.status in filter.statuses
            }
            .filter { task ->
                if (filter.priorities.isEmpty()) true else task.priority in filter.priorities
            }
            .filter { task ->
                val targetCustomer = filter.customerName
                if (targetCustomer.isNullOrBlank()) true
                else task.customerName.equals(targetCustomer, ignoreCase = true)
            }
            .filter { task ->
                val targetEpic = filter.epicName
                if (targetEpic.isNullOrBlank()) true
                else task.epicName.equals(targetEpic, ignoreCase = true)
            }
            .filter { task -> matchesDueFilter(task, filter.dueDateFilter, nowMillis, zoneId) }
            .filter { task ->
                if (!filter.onlyOverdue) true else isOverdue(task, nowMillis, zoneId)
            }
            .filter { task ->
                if (!filter.onlyWithComments) true else task.commentCount > 0
            }
            .filter { task ->
                val label = filter.selectedLabel
                if (label.isNullOrBlank()) true else task.labels.any { it.equals(label, ignoreCase = true) }
            }
            .filter { task ->
                if (normalizedQuery.isEmpty()) {
                    true
                } else {
                    val inTitle = task.title.lowercase().contains(normalizedQuery)
                    val inDesc = task.description.lowercase().contains(normalizedQuery)
                    val inCustomer = task.customerName?.lowercase()?.contains(normalizedQuery) == true
                    val inEpic = task.epicName?.lowercase()?.contains(normalizedQuery) == true
                    val inLabels = task.labels.any { it.lowercase().contains(normalizedQuery) }
                    val inComments = commentsByTaskId[task.id]
                        ?.any { !it.isDeleted && it.text.lowercase().contains(normalizedQuery) } == true
                    inTitle || inDesc || inCustomer || inEpic || inLabels || inComments
                }
            }
            .toList()

        return sortTasks(filtered, filter.sortOption)
    }

    private fun matchesTab(
        task: TaskItem,
        tab: TaskViewTab,
        nowMillis: Long,
        zoneId: ZoneId
    ): Boolean = when (tab) {
        TaskViewTab.ALL -> true
        TaskViewTab.TODO -> task.status == TaskStatus.TODO
        TaskViewTab.IN_PROGRESS -> task.status == TaskStatus.IN_PROGRESS
        TaskViewTab.DONE -> task.status == TaskStatus.DONE
    }

    private fun matchesDueFilter(
        task: TaskItem,
        dueFilter: DueDateFilter,
        nowMillis: Long,
        zoneId: ZoneId
    ): Boolean = when (dueFilter) {
        DueDateFilter.ALL -> true
        DueDateFilter.TODAY -> isDueToday(task, nowMillis, zoneId)
        DueDateFilter.TOMORROW -> isDueTomorrow(task, nowMillis, zoneId)
        DueDateFilter.THIS_WEEK -> isDueThisWeek(task, nowMillis, zoneId)
        DueDateFilter.OVERDUE -> isOverdue(task, nowMillis, zoneId)
        DueDateFilter.NO_DATE -> task.dueAt == null
    }

    fun sortTasks(tasks: List<TaskItem>, sortOption: SortOption): List<TaskItem> {
        return when (sortOption) {
            SortOption.PRIORITY_DESC -> tasks.sortedWith(
                compareByDescending<TaskItem> { it.priority.weight }
                    .thenBy { it.dueAt ?: Long.MAX_VALUE }
                    .thenByDescending { it.updatedAt }
            )
            SortOption.DUE_DATE_ASC -> tasks.sortedWith(
                compareBy<TaskItem> { it.dueAt ?: Long.MAX_VALUE }
                    .thenByDescending { it.priority.weight }
            )
            SortOption.CREATED_DESC -> tasks.sortedByDescending { it.createdAt }
            SortOption.UPDATED_DESC -> tasks.sortedByDescending { it.updatedAt }
            SortOption.CUSTOMER_ASC -> tasks.sortedWith(
                compareBy<TaskItem> { it.customerName.orEmpty().ifBlank { "\uFFFF" }.lowercase() }
                    .thenByDescending { it.priority.weight }
            )
        }
    }

    fun groupByStatus(tasks: List<TaskItem>): Map<TaskStatus, List<TaskItem>> {
        val grouped = tasks.groupBy { it.status }
        return TaskStatus.entries.associateWith { status -> grouped[status].orEmpty() }
    }
}
