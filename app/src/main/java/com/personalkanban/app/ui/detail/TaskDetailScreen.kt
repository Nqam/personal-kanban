package com.personalkanban.app.ui.detail

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personalkanban.app.domain.model.TaskComment
import com.personalkanban.app.domain.model.TaskPriority
import com.personalkanban.app.domain.model.TaskStatus
import com.personalkanban.app.ui.components.DateTimeFormatters
import com.personalkanban.app.ui.components.TaskDatePickerDialog
import com.personalkanban.app.ui.components.TaskTimePickerDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDetailScreen(
    viewModel: TaskDetailViewModel,
    onBack: () -> Unit,
    onOpenTask: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val task = state.task

    if (task == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Задача") },
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
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Задача не найдена")
            }
        }
        return
    }

    var title by remember(task.id) { mutableStateOf(task.title) }
    var description by remember(task.id) { mutableStateOf(task.description) }
    var customerName by remember(task.id) { mutableStateOf(task.customerName.orEmpty()) }
    var epicName by remember(task.id) { mutableStateOf(task.epicName.orEmpty()) }
    var newLabel by remember { mutableStateOf("") }
    var newComment by remember { mutableStateOf("") }
    var editingComment by remember { mutableStateOf<TaskComment?>(null) }
    var editCommentText by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.addAttachment(uri)
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.addAttachment(uri)
    }

    fun currentDraftTask() = task.copy(
        title = title.ifBlank { task.title },
        description = description,
        customerName = customerName.takeIf { it.isNotBlank() },
        epicName = epicName.takeIf { it.isNotBlank() }
    )

    LaunchedEffect(task.title, task.description, task.customerName, task.epicName) {
        if (title != task.title && title.isEmpty()) title = task.title
        if (description != task.description && description.isEmpty()) description = task.description
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Детали задачи") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (title.isNotBlank() && (title != task.title || description != task.description || customerName != task.customerName.orEmpty() || epicName != task.epicName.orEmpty())) {
                            viewModel.saveTask(
                                task.copy(
                                    title = title,
                                    description = description,
                                    customerName = customerName.takeIf { it.isNotBlank() },
                                    epicName = epicName.takeIf { it.isNotBlank() }
                                )
                            )
                        }
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.duplicateTask { newId -> onOpenTask(newId) }
                    }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Дублировать")
                    }
                    IconButton(onClick = { viewModel.archiveTask(onBack) }) {
                        Icon(Icons.Outlined.Archive, contentDescription = "В архив")
                    }
                    IconButton(onClick = { viewModel.deleteTask(onBack) }) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = "Удалить",
                            tint = MaterialTheme.colorScheme.error
                        )
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
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Подробное описание") },
                minLines = 3,
                maxLines = 8,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Status
            Text("Статус", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskStatus.entries.forEach { s ->
                    FilterChip(
                        selected = task.status == s,
                        onClick = {
                            viewModel.saveTask(currentDraftTask().copy(status = s))
                        },
                        label = { Text(s.displayName) }
                    )
                }
            }

            // Priority
            Text("Приоритет", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskPriority.entries.forEach { p ->
                    FilterChip(
                        selected = task.priority == p,
                        onClick = {
                            viewModel.saveTask(currentDraftTask().copy(priority = p))
                        },
                        label = { Text(p.displayName) }
                    )
                }
            }

            // Due date & time
            Text("Срок выполнения", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.CalendarToday, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(task.dueAt?.let { DateTimeFormatters.formatDueDate(it, task.hasTime) } ?: "Выбрать дату")
                }

                if (task.dueAt != null) {
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (task.hasTime) "Изменить время" else "Добавить время")
                    }
                }
            }

            // Reminder toggle
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NotificationsActive, null)
                        Spacer(Modifier.width(10.dp))
                        Text("Напоминания по сроку", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = task.reminderEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.saveTask(currentDraftTask().copy(reminderEnabled = enabled))
                        }
                    )
                }
            }

            // Customer with autocomplete suggestions
            OutlinedTextField(
                value = customerName,
                onValueChange = { customerName = it },
                label = { Text("Заказчик") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            val customerSuggestions = remember(customerName, state.customers) {
                state.customers.filter {
                    customerName.isBlank() || it.name.contains(customerName, ignoreCase = true)
                }.take(6)
            }
            if (customerSuggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    customerSuggestions.forEach { c ->
                        FilterChip(
                            selected = customerName.equals(c.name, ignoreCase = true),
                            onClick = {
                                customerName = c.name
                                viewModel.saveTask(currentDraftTask().copy(customerName = c.name))
                            },
                            label = { Text(c.name) }
                        )
                    }
                }
            }

            // Epic (Тематика) with autocomplete suggestions
            OutlinedTextField(
                value = epicName,
                onValueChange = { epicName = it },
                label = { Text("Тематика (Эпик)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            val epicSuggestions = remember(epicName, state.epics) {
                state.epics.filter {
                    epicName.isBlank() || it.name.contains(epicName, ignoreCase = true)
                }.take(6)
            }
            if (epicSuggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    epicSuggestions.forEach { epic ->
                        FilterChip(
                            selected = epicName.equals(epic.name, ignoreCase = true),
                            onClick = {
                                epicName = epic.name
                                viewModel.saveTask(currentDraftTask().copy(epicName = epic.name))
                            },
                            label = { Text(epic.name) }
                        )
                    }
                }
            }

            // Labels
            Text("Метки", style = MaterialTheme.typography.labelMedium)
            if (task.labels.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    task.labels.forEach { label ->
                        InputChip(
                            selected = true,
                            onClick = {
                                viewModel.saveTask(currentDraftTask().copy(labels = task.labels - label))
                            },
                            label = { Text("#$label ×") }
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newLabel,
                    onValueChange = { newLabel = it },
                    placeholder = { Text("Новая метка") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {
                        val clean = newLabel.trim().removePrefix("#")
                        if (clean.isNotEmpty()) {
                            viewModel.saveTask(currentDraftTask().copy(labels = (task.labels + clean).distinct()))
                            newLabel = ""
                        }
                    },
                    enabled = newLabel.isNotBlank()
                ) {
                    Text("Добавить")
                }
            }

            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        viewModel.saveTask(
                            task.copy(
                                title = title,
                                description = description,
                                customerName = customerName.takeIf { it.isNotBlank() },
                                epicName = epicName.takeIf { it.isNotBlank() }
                            )
                        )
                        onBack()
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сохранить изменения")
            }

            // Metadata timestamps
            Text(
                text = "Создано: ${DateTimeFormatters.formatTimestamp(task.createdAt)} · Изменено: ${DateTimeFormatters.formatTimestamp(task.updatedAt)}" +
                    (task.completedAt?.let { " · Завершено: ${DateTimeFormatters.formatTimestamp(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // Attachments section
            Text("Вложения (${task.attachments.size})", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        pickPhotoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Image, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("+ Фото")
                }
                OutlinedButton(
                    onClick = { pickFileLauncher.launch(arrayOf("*/*")) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("+ Файл")
                }
            }

            task.attachments.forEach { att ->
                val isImage = att.mimeType.startsWith("image/")
                val thumb = remember(att.localPath) {
                    if (isImage) {
                        runCatching {
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(att.localPath, bounds)
                            var sample = 1
                            while (bounds.outWidth / sample > 128 && bounds.outHeight / sample > 128) {
                                sample *= 2
                            }
                            val opts = BitmapFactory.Options().apply {
                                inSampleSize = sample
                                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                            }
                            BitmapFactory.decodeFile(att.localPath, opts)?.asImageBitmap()
                        }.getOrNull()
                    } else null
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            runCatching {
                                val file = File(att.localPath)
                                if (file.exists()) {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, att.mimeType)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, att.displayName))
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (thumb != null) {
                            Image(
                                bitmap = thumb,
                                contentDescription = att.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = att.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val sizeKb = (att.sizeBytes / 1024).coerceAtLeast(1)
                            Text(
                                text = "$sizeKb КБ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.removeAttachment(att.id) }) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "Удалить вложение",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Comments section
            Text("Комментарии (${state.comments.size})", style = MaterialTheme.typography.titleMedium)

            state.comments.forEach { comment ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = DateTimeFormatters.formatTimestamp(comment.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row {
                                IconButton(
                                    onClick = {
                                        editingComment = comment
                                        editCommentText = comment.text
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Редактировать", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.deleteComment(comment.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = "Удалить",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        Text(text = comment.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newComment,
                    onValueChange = { newComment = it },
                    placeholder = { Text("Добавить комментарий...") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (newComment.isNotBlank()) {
                            viewModel.addComment(newComment)
                            newComment = ""
                        }
                    },
                    enabled = newComment.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Отправить")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        TaskDatePickerDialog(
            initialMillis = task.dueAt,
            onDateSelected = { picked ->
                viewModel.saveTask(
                    task.copy(
                        dueAt = DateTimeFormatters.endOfDayMillis(picked),
                        hasTime = false
                    )
                )
            },
            onClear = {
                viewModel.saveTask(task.copy(dueAt = null, hasTime = false))
            },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showTimePicker) {
        TaskTimePickerDialog(
            initialMillis = task.dueAt,
            onTimeSelected = { hour, minute ->
                val base = task.dueAt ?: System.currentTimeMillis()
                viewModel.saveTask(
                    task.copy(
                        dueAt = DateTimeFormatters.combineDateAndTime(base, hour, minute),
                        hasTime = true
                    )
                )
            },
            onDismiss = { showTimePicker = false }
        )
    }

    editingComment?.let { comment ->
        AlertDialog(
            onDismissRequest = { editingComment = null },
            title = { Text("Редактировать комментарий") },
            text = {
                OutlinedTextField(
                    value = editCommentText,
                    onValueChange = { editCommentText = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editCommentText.isNotBlank()) {
                        viewModel.editComment(comment.id, editCommentText)
                    }
                    editingComment = null
                }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingComment = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}
