package com.personalkanban.app

import com.personalkanban.app.domain.model.DueDateFilter
import com.personalkanban.app.domain.model.SortOption
import com.personalkanban.app.domain.model.TaskComment
import com.personalkanban.app.domain.model.TaskFilterEngine
import com.personalkanban.app.domain.model.TaskFilterState
import com.personalkanban.app.domain.model.TaskItem
import com.personalkanban.app.domain.model.TaskPriority
import com.personalkanban.app.domain.model.TaskStatus
import com.personalkanban.app.domain.model.TaskViewTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TaskFilterEngineTest {

    private val zone = ZoneId.of("UTC")
    private val now = LocalDate.of(2026, 11, 20).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val yesterday = LocalDate.of(2026, 11, 19).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val todayEvening = LocalDate.of(2026, 11, 20).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
    private val tomorrow = LocalDate.of(2026, 11, 21).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

    private fun task(
        id: String,
        title: String = "Task $id",
        description: String = "",
        status: TaskStatus = TaskStatus.TODO,
        priority: TaskPriority = TaskPriority.MEDIUM,
        dueAt: Long? = null,
        customerName: String? = null,
        commentCount: Int = 0
    ) = TaskItem(
        id = id,
        title = title,
        description = description,
        status = status,
        priority = priority,
        dueAt = dueAt,
        customerName = customerName,
        createdAt = now,
        updatedAt = now,
        commentCount = commentCount
    )

    @Test
    fun `search matches title description customer and comments`() {
        val t1 = task("1", title = "Подготовить отчёт")
        val t2 = task("2", title = "Звонок", description = "Обсудить бюджет")
        val t3 = task("3", title = "Договор", customerName = "Альфа")
        val t4 = task("4", title = "Релиз", commentCount = 1)
        val comments = mapOf(
            "4" to listOf(
                TaskComment("c1", "4", "Проверить APK на Galaxy S26", now, now)
            )
        )

        val byComment = TaskFilterEngine.filterAndSort(
            listOf(t1, t2, t3, t4),
            TaskFilterState(query = "galaxy"),
            comments,
            now,
            zone
        )
        assertEquals(listOf("4"), byComment.map { it.id })

        val byCustomer = TaskFilterEngine.filterAndSort(
            listOf(t1, t2, t3, t4),
            TaskFilterState(query = "альфа"),
            comments,
            now,
            zone
        )
        assertEquals(listOf("3"), byCustomer.map { it.id })
    }

    @Test
    fun `overdue filter excludes done tasks`() {
        val overdueTask = task("1", dueAt = yesterday, status = TaskStatus.TODO)
        val todayTask = task("2", dueAt = todayEvening, status = TaskStatus.IN_PROGRESS)
        val doneOverdue = task("3", dueAt = yesterday, status = TaskStatus.DONE)
        val futureTask = task("4", dueAt = tomorrow, status = TaskStatus.IN_PROGRESS)

        val all = listOf(overdueTask, todayTask, doneOverdue, futureTask)

        assertTrue(TaskFilterEngine.isOverdue(overdueTask, now, zone))
        assertFalse(TaskFilterEngine.isOverdue(doneOverdue, now, zone))

        val overdueRes = TaskFilterEngine.filterAndSort(
            all,
            TaskFilterState(dueDateFilter = DueDateFilter.OVERDUE),
            emptyMap(),
            now,
            zone
        )
        assertEquals(listOf("1"), overdueRes.map { it.id })
    }

    @Test
    fun `combined filters and priority sorting work together`() {
        val t1 = task("1", priority = TaskPriority.LOW, customerName = "Acme", dueAt = todayEvening)
        val t2 = task("2", priority = TaskPriority.CRITICAL, customerName = "Acme", dueAt = tomorrow)
        val t3 = task("3", priority = TaskPriority.HIGH, customerName = "Beta", dueAt = todayEvening)

        val res = TaskFilterEngine.filterAndSort(
            listOf(t1, t2, t3),
            TaskFilterState(
                customerName = "Acme",
                dueDateFilter = DueDateFilter.THIS_WEEK,
                sortOption = SortOption.PRIORITY_DESC
            ),
            emptyMap(),
            now,
            zone
        )
        assertEquals(listOf("2", "1"), res.map { it.id })
    }

    @Test
    fun `all 3 statuses and view tabs filter consistently`() {
        val todo = task("1", status = TaskStatus.TODO)
        val inProgress = task("2", status = TaskStatus.IN_PROGRESS)
        val done = task("3", status = TaskStatus.DONE)
        val all = listOf(todo, inProgress, done)

        assertEquals(listOf("1"), TaskFilterEngine.filterAndSort(all, TaskFilterState(selectedTab = TaskViewTab.TODO), emptyMap(), now, zone).map { it.id })
        assertEquals(listOf("2"), TaskFilterEngine.filterAndSort(all, TaskFilterState(selectedTab = TaskViewTab.IN_PROGRESS), emptyMap(), now, zone).map { it.id })
        assertEquals(listOf("3"), TaskFilterEngine.filterAndSort(all, TaskFilterState(selectedTab = TaskViewTab.DONE), emptyMap(), now, zone).map { it.id })

        val multiStatus = TaskFilterEngine.filterAndSort(
            all,
            TaskFilterState(statuses = setOf(TaskStatus.IN_PROGRESS, TaskStatus.TODO)),
            emptyMap(),
            now,
            zone
        )
        assertEquals(setOf("1", "2"), multiStatus.map { it.id }.toSet())
    }

    @Test
    fun `overdue triggers on next day when no time set or after exact time when time set`() {
        val lateTonight = LocalDate.of(2026, 11, 20).atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
        val sameDayNoTime = task("1", dueAt = now, status = TaskStatus.TODO).copy(hasTime = false)
        val sameDayPastTime = task("2", dueAt = now, status = TaskStatus.TODO).copy(hasTime = true)
        val yesterdayNoTime = task("3", dueAt = yesterday, status = TaskStatus.TODO).copy(hasTime = false)

        assertFalse(TaskFilterEngine.isOverdue(sameDayNoTime, lateTonight, zone))
        assertTrue(TaskFilterEngine.isOverdue(sameDayPastTime, lateTonight, zone))
        assertTrue(TaskFilterEngine.isOverdue(yesterdayNoTime, lateTonight, zone))
    }
}
