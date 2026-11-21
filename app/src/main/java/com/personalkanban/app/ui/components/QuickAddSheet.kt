package com.personalkanban.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.personalkanban.app.domain.model.Customer
import com.personalkanban.app.domain.model.Epic
import com.personalkanban.app.domain.model.TaskPriority
import com.personalkanban.app.domain.model.TaskStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddBottomSheet(
    customers: List<Customer>,
    epics: List<Epic>,
    onDismiss: () -> Unit,
    onCreateTask: (
        title: String,
        status: TaskStatus,
        priority: TaskPriority,
        dueAt: Long?,
        hasTime: Boolean,
        customerName: String?,
        epicName: String?,
        comment: String?,
        attachmentUris: List<Uri>,
        keepOpen: Boolean
    ) -> Unit
) {
    // Prevent accidental swipe-down dismissal; user closes explicitly via X button or Back
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )
    val focusRequester = remember { FocusRequester() }

    var title by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(TaskStatus.INBOX) }
    var priority by remember { mutableStateOf(TaskPriority.MEDIUM) }
    var dueAt by remember { mutableStateOf<Long?>(null) }
    var hasTime by remember { mutableStateOf(false) }
    var customerName by remember { mutableStateOf("") }
    var epicName by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var attachmentUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) attachmentUris = attachmentUris + uri
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) attachmentUris = attachmentUris + uri
    }

    var showPriorityRow by remember { mutableStateOf(false) }
    var showStatusRow by remember { mutableStateOf(false) }
    var showCustomerField by remember { mutableStateOf(false) }
    var showEpicField by remember { mutableStateOf(false) }
    var showCommentField by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun resetForNext() {
        title = ""
        comment = ""
        attachmentUris = emptyList()
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Новая задача", style = MaterialTheme.typography.titleMedium)
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Закрыть")
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Что нужно сделать?") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (title.isNotBlank()) {
                            onCreateTask(
                                title, status, priority, dueAt, hasTime,
                                customerName.takeIf { it.isNotBlank() },
                                epicName.takeIf { it.isNotBlank() },
                                comment.takeIf { it.isNotBlank() },
                                attachmentUris,
                                false
                            )
                        }
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )

            // Quick action chips row (inline toggles — never steal focus or hide keyboard)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = dueAt != null,
                    onClick = { showDatePicker = true },
                    label = {
                        Text(dueAt?.let { DateTimeFormatters.formatDueDate(it, hasTime) } ?: "Срок")
                    },
                    leadingIcon = { Icon(Icons.Outlined.CalendarToday, null, Modifier.height(16.dp)) }
                )

                if (dueAt != null) {
                    FilterChip(
                        selected = hasTime,
                        onClick = { showTimePicker = true },
                        label = { Text(if (hasTime) "Время" else "+ Время") },
                        leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.height(16.dp)) }
                    )
                }

                FilterChip(
                    selected = false,
                    onClick = {
                        dueAt = DateTimeFormatters.endOfDayMillis(System.currentTimeMillis())
                        hasTime = false
                    },
                    label = { Text("Сегодня") }
                )

                FilterChip(
                    selected = showPriorityRow || priority != TaskPriority.MEDIUM,
                    onClick = {
                        showPriorityRow = !showPriorityRow
                        if (showPriorityRow) showStatusRow = false
                    },
                    label = { Text(priority.displayName) },
                    leadingIcon = { Icon(Icons.Outlined.Flag, null, Modifier.height(16.dp)) }
                )

                FilterChip(
                    selected = showStatusRow || status != TaskStatus.INBOX,
                    onClick = {
                        showStatusRow = !showStatusRow
                        if (showStatusRow) showPriorityRow = false
                    },
                    label = { Text(status.displayName) },
                    leadingIcon = { Icon(Icons.Outlined.SwapHoriz, null, Modifier.height(16.dp)) }
                )

                FilterChip(
                    selected = showCustomerField || customerName.isNotBlank(),
                    onClick = { showCustomerField = !showCustomerField },
                    label = { Text(customerName.ifBlank { "Заказчик" }) },
                    leadingIcon = { Icon(Icons.Outlined.PersonOutline, null, Modifier.height(16.dp)) }
                )

                FilterChip(
                    selected = showEpicField || epicName.isNotBlank(),
                    onClick = { showEpicField = !showEpicField },
                    label = { Text(epicName.ifBlank { "Тематика" }) },
                    leadingIcon = { Icon(Icons.Outlined.FolderOpen, null, Modifier.height(16.dp)) }
                )

                FilterChip(
                    selected = showCommentField || comment.isNotBlank(),
                    onClick = { showCommentField = !showCommentField },
                    label = { Text("Комментарий") },
                    leadingIcon = { Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.height(16.dp)) }
                )

                FilterChip(
                    selected = false,
                    onClick = {
                        pickPhotoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    label = { Text("+ Фото") },
                    leadingIcon = { Icon(Icons.Outlined.Image, null, Modifier.height(16.dp)) }
                )

                FilterChip(
                    selected = attachmentUris.isNotEmpty(),
                    onClick = { pickFileLauncher.launch(arrayOf("*/*")) },
                    label = {
                        Text(
                            if (attachmentUris.isEmpty()) "+ Файл" else "Файлов: ${attachmentUris.size}"
                        )
                    },
                    leadingIcon = { Icon(Icons.Outlined.AttachFile, null, Modifier.height(16.dp)) }
                )
            }

            AnimatedVisibility(visible = showPriorityRow) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TaskPriority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = {
                                priority = p
                                showPriorityRow = false
                            },
                            label = { Text(p.displayName) }
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showStatusRow) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TaskStatus.entries.forEach { s ->
                        FilterChip(
                            selected = status == s,
                            onClick = {
                                status = s
                                showStatusRow = false
                            },
                            label = { Text(s.displayName) }
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showCustomerField) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("Заказчик") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    val suggestions = remember(customerName, customers) {
                        customers.filter {
                            customerName.isBlank() || it.name.contains(customerName, ignoreCase = true)
                        }.take(5)
                    }
                    if (suggestions.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            suggestions.forEach { c ->
                                FilterChip(
                                    selected = customerName.equals(c.name, ignoreCase = true),
                                    onClick = { customerName = c.name },
                                    label = { Text(c.name) }
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showEpicField) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = epicName,
                        onValueChange = { epicName = it },
                        label = { Text("Тематика (Эпик)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    val epicSuggestions = remember(epicName, epics) {
                        epics.filter {
                            epicName.isBlank() || it.name.contains(epicName, ignoreCase = true)
                        }.take(5)
                    }
                    if (epicSuggestions.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            epicSuggestions.forEach { e ->
                                FilterChip(
                                    selected = epicName.equals(e.name, ignoreCase = true),
                                    onClick = { epicName = e.name },
                                    label = { Text(e.name) }
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showCommentField) {
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Комментарий") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (title.isNotBlank()) {
                            onCreateTask(
                                title, status, priority, dueAt, hasTime,
                                customerName.takeIf { it.isNotBlank() },
                                epicName.takeIf { it.isNotBlank() },
                                comment.takeIf { it.isNotBlank() },
                                attachmentUris,
                                true
                            )
                            resetForNext()
                        }
                    },
                    enabled = title.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("И ещё")
                }

                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onCreateTask(
                                title, status, priority, dueAt, hasTime,
                                customerName.takeIf { it.isNotBlank() },
                                epicName.takeIf { it.isNotBlank() },
                                comment.takeIf { it.isNotBlank() },
                                attachmentUris,
                                false
                            )
                        }
                    },
                    enabled = title.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.2f)
                ) {
                    Text("Сохранить")
                }
            }
        }
    }

    if (showDatePicker) {
        TaskDatePickerDialog(
            initialMillis = dueAt,
            onDateSelected = { picked ->
                dueAt = DateTimeFormatters.endOfDayMillis(picked)
                hasTime = false
            },
            onClear = {
                dueAt = null
                hasTime = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showTimePicker) {
        TaskTimePickerDialog(
            initialMillis = dueAt,
            onTimeSelected = { hour, minute ->
                val base = dueAt ?: System.currentTimeMillis()
                dueAt = DateTimeFormatters.combineDateAndTime(base, hour, minute)
                hasTime = true
            },
            onDismiss = { showTimePicker = false }
        )
    }
}
