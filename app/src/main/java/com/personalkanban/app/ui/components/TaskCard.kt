package com.personalkanban.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalkanban.app.domain.model.TaskFilterEngine
import com.personalkanban.app.domain.model.TaskItem
import com.personalkanban.app.domain.model.TaskPriority
import com.personalkanban.app.domain.model.TaskStatus
import com.personalkanban.app.ui.theme.accentColor
import com.personalkanban.app.ui.theme.color

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun TaskCard(
    task: TaskItem,
    compact: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onSwipeComplete: () -> Unit,
    onSwipeDelete: () -> Unit,
    onChangeStatus: (TaskStatus) -> Unit,
    onDuplicate: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val isOverdue = remember(task.dueAt, task.hasTime, task.status) { TaskFilterEngine.isOverdue(task) }
    var showContextMenu by remember { mutableStateOf(false) }
    var showQuickStatusMenu by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onSwipeComplete()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onSwipeDelete()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.55f }
    )

    Box(modifier = modifier.fillMaxWidth()) {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = !isSelectionMode && task.status != TaskStatus.DONE,
            enableDismissFromEndToStart = !isSelectionMode,
            backgroundContent = {
                val direction = dismissState.dismissDirection
                val bgColor by animateColorAsState(
                    targetValue = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> Color(0xFF059669).copy(alpha = 0.16f)
                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                        SwipeToDismissBoxValue.Settled -> Color.Transparent
                    },
                    label = "swipeBg"
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(bgColor)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                        Arrangement.Start
                    } else {
                        Arrangement.End
                    }
                ) {
                    if (direction == SwipeToDismissBoxValue.StartToEnd) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "Выполнить",
                            tint = Color(0xFF059669)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Готово",
                            color = Color(0xFF059669),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                        Text(
                            text = "В корзину",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "В корзину",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        ) {
            val priorityColor = task.priority.color(isDark)
            val statusColor = task.status.accentColor(isDark)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(
                        onClick = {
                            if (isSelectionMode) onToggleSelect() else onClick()
                        },
                        onLongClick = {
                            showContextMenu = true
                        }
                    ),
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                tonalElevation = 1.dp,
                shadowElevation = 1.dp,
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else if (isOverdue) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawRect(
                                color = priorityColor,
                                size = Size(4.dp.toPx(), size.height)
                            )
                        }
                        .padding(start = 4.dp)
                ) {

                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() },
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(
                                horizontal = if (compact) 12.dp else 14.dp,
                                vertical = if (compact) 7.dp else 14.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)
                    ) {
                        // Top row: title + overdue badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = task.title,
                                style = (if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall).copy(
                                    fontWeight = FontWeight.Medium,
                                    textDecoration = if (task.status == TaskStatus.DONE) {
                                        TextDecoration.LineThrough
                                    } else {
                                        TextDecoration.None
                                    }
                                ),
                                color = if (task.status == TaskStatus.DONE) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = if (compact) 1 else 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            if (isOverdue) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.WarningAmber,
                                            contentDescription = "Просрочено",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        if (!compact) {
                                            Text(
                                                text = "Просрочено",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (!compact && task.description.isNotBlank()) {
                            Text(
                                text = task.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Metadata row: compact shows only status + due date + counts; normal shows all pills
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
                        ) {
                            if (!compact) {
                                // Priority chip (in compact mode, left color stripe indicates priority)
                                MetaPill(
                                    text = task.priority.displayName,
                                    dotColor = priorityColor,
                                    containerColor = priorityColor.copy(alpha = 0.10f),
                                    textColor = priorityColor
                                )
                            }

                            // Status chip (tap to change status quickly)
                            Box {
                                MetaPill(
                                    text = task.status.displayName,
                                    containerColor = statusColor.copy(alpha = 0.12f),
                                    textColor = statusColor,
                                    onClick = if (!isSelectionMode) {
                                        { showQuickStatusMenu = true }
                                    } else null
                                )
                                DropdownMenu(
                                    expanded = showQuickStatusMenu,
                                    onDismissRequest = { showQuickStatusMenu = false }
                                ) {
                                    TaskStatus.entries.forEach { targetStatus ->
                                        DropdownMenuItem(
                                            text = { Text(targetStatus.displayName) },
                                            onClick = {
                                                showQuickStatusMenu = false
                                                if (targetStatus != task.status) {
                                                    onChangeStatus(targetStatus)
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // Due date
                            task.dueAt?.let { due ->
                                val dueText = DateTimeFormatters.formatDueDate(due, task.hasTime)
                                MetaPill(
                                    text = dueText,
                                    icon = Icons.Outlined.CalendarToday,
                                    containerColor = if (isOverdue) {
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    textColor = if (isOverdue) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }

                            if (!compact) {
                                // Customer
                                task.customerName?.takeIf { it.isNotBlank() }?.let { customer ->
                                    MetaPill(
                                        text = customer,
                                        icon = Icons.Outlined.PersonOutline,
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                                        textColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }

                                // Epic (Тематика)
                                task.epicName?.takeIf { it.isNotBlank() }?.let { epic ->
                                    MetaPill(
                                        text = epic,
                                        icon = Icons.Outlined.FolderOpen,
                                        containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                                        textColor = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }

                            // Comments count
                            if (task.commentCount > 0) {
                                MetaPill(
                                    text = "${task.commentCount}",
                                    icon = Icons.Outlined.ChatBubbleOutline,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    textColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Attachments count
                            if (task.attachments.isNotEmpty()) {
                                MetaPill(
                                    text = "${task.attachments.size}",
                                    icon = Icons.Outlined.AttachFile,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    textColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Labels (only in expanded mode)
                            if (!compact) {
                                task.labels.forEach { label ->
                                    MetaPill(
                                        text = "#$label",
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        textColor = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            TaskStatus.entries.filter { it != task.status }.forEach { targetStatus ->
                DropdownMenuItem(
                    text = { Text("В «${targetStatus.displayName}»") },
                    leadingIcon = { Icon(Icons.Outlined.SwapHoriz, null) },
                    onClick = {
                        showContextMenu = false
                        onChangeStatus(targetStatus)
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Выбрать несколько") },
                leadingIcon = { Icon(Icons.Outlined.CheckCircle, null) },
                onClick = {
                    showContextMenu = false
                    onToggleSelect()
                }
            )
            DropdownMenuItem(
                text = { Text("Дублировать") },
                leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                onClick = {
                    showContextMenu = false
                    onDuplicate()
                }
            )
            DropdownMenuItem(
                text = { Text("В архив") },
                leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                onClick = {
                    showContextMenu = false
                    onArchive()
                }
            )
            DropdownMenuItem(
                text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                },
                onClick = {
                    showContextMenu = false
                    onSwipeDelete()
                }
            )
        }
    }
}

@Composable
private fun MetaPill(
    text: String,
    containerColor: Color,
    textColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    dotColor: Color? = null,
    onClick: (() -> Unit)? = null
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(8.dp),
        modifier = if (onClick != null) {
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
        } else {
            Modifier
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
