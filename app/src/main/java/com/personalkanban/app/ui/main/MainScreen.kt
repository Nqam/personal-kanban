package com.personalkanban.app.ui.main

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalkanban.app.data.repository.ConflictResolutionChoice
import com.personalkanban.app.domain.model.DueDateFilter
import com.personalkanban.app.domain.model.SortOption
import com.personalkanban.app.domain.model.SyncConflict
import com.personalkanban.app.domain.model.SyncStatusType
import com.personalkanban.app.domain.model.TaskPriority
import com.personalkanban.app.domain.model.TaskStatus
import com.personalkanban.app.domain.model.TaskViewTab
import com.personalkanban.app.ui.components.DateTimeFormatters
import com.personalkanban.app.ui.components.OpenGoogleDriveDocument
import com.personalkanban.app.ui.components.QuickAddBottomSheet
import com.personalkanban.app.ui.components.TaskCard
import com.personalkanban.app.ui.theme.accentColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    initialQuickAdd: Boolean,
    onConsumeInitialQuickAdd: () -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenArchiveTrash: () -> Unit,
    onOpenSettings: (scrollToUpdates: Boolean) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val createBackupDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            viewModel.bindSafBackupUri(uri)
        }
    }

    val openBackupDocLauncher = rememberLauncherForActivityResult(
        contract = OpenGoogleDriveDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreFromSafUri(uri)
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleGoogleSignInResult(result.data)
    }

    var showSearch by remember { mutableStateOf(false) }
    var showQuickAddSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var activeConflict by remember { mutableStateOf<SyncConflict?>(null) }
    var inlineTaskTitle by remember { mutableStateOf("") }
    var pendingCompleteTask by remember { mutableStateOf<com.personalkanban.app.domain.model.TaskItem?>(null) }
    var pendingDeleteTask by remember { mutableStateOf<com.personalkanban.app.domain.model.TaskItem?>(null) }
    var showDumpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(initialQuickAdd) {
        if (initialQuickAdd) {
            showQuickAddSheet = true
            onConsumeInitialQuickAdd()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MainUiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message, duration = SnackbarDuration.Short)
                }
                is MainUiEvent.ShowUndoSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = "Отменить",
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        scope.launch { event.onUndo() }
                    }
                }
                is MainUiEvent.SendDumpToDrive -> {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        event.file
                    )
                    val driveIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "Personal Kanban Backup")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setPackage("com.google.android.apps.docs")
                    }
                    if (driveIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(driveIntent)
                    } else {
                        val fallback = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "Personal Kanban Backup")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(fallback, "Сохранить дамп в Google Диск"))
                    }
                }
            }
        }
    }

    val isSelectionMode = state.selectedTaskIds.isNotEmpty()
    val isDark = isSystemInDarkTheme()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 6.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = DateTimeFormatters.formatHeaderDate(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SyncStatusChip(
                            status = state.settings.lastSyncStatus,
                            onClick = { showDumpDialog = true }
                        )
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Поиск")
                        }
                        val hasActiveFilters = state.filterState.statuses.isNotEmpty() ||
                            state.filterState.priorities.isNotEmpty() ||
                            state.filterState.customerName != null ||
                            state.filterState.epicName != null ||
                            state.filterState.dueDateFilter != DueDateFilter.ALL ||
                            state.filterState.onlyOverdue ||
                            state.filterState.onlyWithComments ||
                            state.filterState.selectedLabel != null
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(
                                Icons.Outlined.FilterList,
                                contentDescription = "Фильтры",
                                tint = if (hasActiveFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = onOpenArchiveTrash) {
                            Icon(Icons.Outlined.Archive, contentDescription = "Старые задачи и корзина")
                        }
                        IconButton(onClick = { onOpenSettings(false) }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Настройки")
                        }
                    }
                }

                // Compact stats strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatLabel("Активных: ${state.stats.activeCount}")
                    StatLabel("Сегодня: ${state.stats.todayCount}")
                    StatLabel(
                        text = "Просрочено: ${state.stats.overdueCount}",
                        isAlert = state.stats.overdueCount > 0
                    )
                    StatLabel("Выполнено: ${state.stats.doneCount}")
                }

                // Search bar
                AnimatedVisibility(visible = showSearch) {
                    OutlinedTextField(
                        value = state.filterState.query,
                        onValueChange = viewModel::updateSearchQuery,
                        placeholder = { Text("Поиск по названию, описанию, комментариям, заказчику...") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = {
                            if (state.filterState.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Очистить")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                // View tabs: only the 4 statuses (tap active chip again to show all)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        TaskViewTab.TODO to TaskStatus.TODO,
                        TaskViewTab.IN_PROGRESS to TaskStatus.IN_PROGRESS,
                        TaskViewTab.REVIEW to TaskStatus.REVIEW,
                        TaskViewTab.DONE to TaskStatus.DONE
                    ).forEach { (tab, status) ->
                        val tabColor = status.accentColor(isDark)
                        val isSelected = state.filterState.selectedTab == tab
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.selectTab(if (isSelected) TaskViewTab.ALL else tab)
                            },
                            label = { Text(tab.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = tabColor.copy(alpha = 0.08f),
                                labelColor = tabColor,
                                selectedContainerColor = tabColor.copy(alpha = 0.22f),
                                selectedLabelColor = tabColor
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = tabColor.copy(alpha = 0.30f),
                                selectedBorderColor = tabColor,
                                borderWidth = 1.dp,
                                selectedBorderWidth = 1.5.dp
                            )
                        )
                    }
                }

                val hasAnyActiveFilter = state.filterState.statuses.isNotEmpty() ||
                    state.filterState.priorities.isNotEmpty() ||
                    state.filterState.customerName != null ||
                    state.filterState.epicName != null ||
                    state.filterState.dueDateFilter != DueDateFilter.ALL ||
                    state.filterState.onlyOverdue ||
                    state.filterState.onlyWithComments ||
                    state.filterState.selectedLabel != null

                if (hasAnyActiveFilter) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Применены дополнительные фильтры",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(onClick = viewModel::clearFilters) {
                            Text("Сбросить", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Conflict banner
                if (state.conflicts.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { activeConflict = state.conflicts.first() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Конфликты синхронизации (${state.conflicts.size}). Нажмите для выбора версии.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        bottomBar = {
            if (isSelectionMode) {
                BulkSelectionBar(
                    selectedCount = state.selectedTaskIds.size,
                    onClear = viewModel::clearSelection,
                    onBulkStatus = viewModel::bulkChangeStatus,
                    onBulkPriority = viewModel::bulkChangePriority,
                    onBulkDelete = viewModel::bulkDelete
                )
            } else {
                // One-handed bottom quick-input bar + FAB
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inlineTaskTitle,
                            onValueChange = { inlineTaskTitle = it },
                            placeholder = { Text("Быстрая задача...") },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (inlineTaskTitle.isNotBlank()) {
                                        viewModel.quickCreateInline(inlineTaskTitle)
                                        inlineTaskTitle = ""
                                    }
                                }
                            ),
                            trailingIcon = {
                                if (inlineTaskTitle.isNotBlank()) {
                                    IconButton(onClick = {
                                        viewModel.quickCreateInline(inlineTaskTitle)
                                        inlineTaskTitle = ""
                                    }) {
                                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Добавить")
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        FloatingActionButton(
                            onClick = { showQuickAddSheet = true },
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "Создать задачу")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val totalVisible = state.groupedTasks.values.sumOf { it.size }
        if (totalVisible == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Задач пока нет",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Напишите задачу внизу или нажмите «+» для быстрого создания",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TaskStatus.entries.forEach { status ->
                    val tasksInGroup = state.groupedTasks[status].orEmpty()
                    if (tasksInGroup.isNotEmpty()) {
                        val isCollapsed = status.name in state.collapsedStatuses
                        item(key = "header_${status.name}") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.toggleStatusGroup(status) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = status.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = status.accentColor(isDark)
                                    )
                                    Badge(
                                        containerColor = status.accentColor(isDark).copy(alpha = 0.15f),
                                        contentColor = status.accentColor(isDark)
                                    ) {
                                        Text("${tasksInGroup.size}")
                                    }
                                }
                                Icon(
                                    imageVector = if (isCollapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                                    contentDescription = if (isCollapsed) "Развернуть" else "Свернуть",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!isCollapsed) {
                            items(
                                items = tasksInGroup,
                                key = { it.id }
                            ) { task ->
                                TaskCard(
                                    task = task,
                                    compact = state.settings.compactCards,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = task.id in state.selectedTaskIds,
                                    onClick = { onOpenTask(task.id) },
                                    onToggleSelect = { viewModel.toggleSelection(task.id) },
                                    onSwipeComplete = { pendingCompleteTask = task },
                                    onSwipeDelete = { pendingDeleteTask = task },
                                    onChangeStatus = { newStatus -> viewModel.changeTaskStatus(task.id, newStatus) },
                                    onDuplicate = { viewModel.duplicateTask(task.id) },
                                    onArchive = { viewModel.archiveTask(task) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQuickAddSheet) {
        QuickAddBottomSheet(
            customers = state.customers,
            epics = state.epics,
            onDismiss = { showQuickAddSheet = false },
            onCreateTask = { title, status, priority, dueAt, hasTime, customer, epic, comment, attachmentUris, keepOpen ->
                viewModel.createTask(title, status, priority, dueAt, hasTime, customer, epic, comment, attachmentUris)
                if (!keepOpen) {
                    showQuickAddSheet = false
                }
            }
        )
    }

    if (showFilterSheet) {
        FilterSortBottomSheet(
            state = state,
            onDismiss = { showFilterSheet = false },
            onUpdateFilter = viewModel::updateFilter,
            onSetSort = viewModel::setSortOption,
            onClear = viewModel::clearFilters
        )
    }

    activeConflict?.let { conflict ->
        ConflictResolutionDialog(
            conflict = conflict,
            onChoose = { choice ->
                viewModel.resolveConflict(conflict, choice)
                activeConflict = null
            },
            onDismiss = { activeConflict = null }
        )
    }

    pendingCompleteTask?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingCompleteTask = null },
            title = { Text("Завершить задачу?") },
            text = { Text("Отметить «${task.title}» как выполненную?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.completeTask(task)
                    pendingCompleteTask = null
                }) {
                    Text("Завершить")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCompleteTask = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    pendingDeleteTask?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTask = null },
            title = { Text("Удалить задачу?") },
            text = { Text("Переместить «${task.title}» в корзину?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteTask(task)
                    pendingDeleteTask = null
                }) {
                    Text("В корзину")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTask = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showDumpDialog) {
        AlertDialog(
            onDismissRequest = { showDumpDialog = false },
            title = { Text("Резервная копия") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (!state.backupFileUri.isNullOrBlank()) {
                            "Файл резервной копии привязан. При сохранении он перезаписывается (новый файл не создаётся)."
                        } else {
                            "Привяжите файл дампа один раз (на телефоне или в облаке), чтобы он всегда перезаписывался, либо отправьте копию в Google Диск."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!state.backupFileUri.isNullOrBlank()) {
                        Button(
                            onClick = {
                                showDumpDialog = false
                                viewModel.syncNow()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Перезаписать дамп сейчас")
                        }
                    } else {
                        Button(
                            onClick = {
                                showDumpDialog = false
                                createBackupDocLauncher.launch("taskflow_backup.json")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Привязать файл дампа (для перезаписи)")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            showDumpDialog = false
                            viewModel.sendDumpToGoogleDrive()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Отправить копию в Google Диск")
                    }
                    OutlinedButton(
                        onClick = {
                            showDumpDialog = false
                            openBackupDocLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Восстановить из дампа")
                    }
                    if (!state.backupFileUri.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                showDumpDialog = false
                                createBackupDocLauncher.launch("taskflow_backup.json")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Выбрать другой файл для перезаписи")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDumpDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    state.availableUpdate?.let { release ->
        AlertDialog(
            onDismissRequest = viewModel::dismissUpdateBanner,
            title = { Text("Доступно обновление ${release.versionName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Текущая версия: ${com.personalkanban.app.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (release.releaseNotes.isNotBlank()) {
                        Text(
                            text = release.releaseNotes,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.dismissUpdateBanner()
                    onOpenSettings(true)
                }) {
                    Text("Перейти к обновлению")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissUpdateBanner) {
                    Text("Позже")
                }
            }
        )
    }

    if (state.showDriveOnboarding) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDriveOnboarding,
            title = { Text("Резервное копирование") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Вы можете сразу сохранить дамп в Google Диск через штатное приложение Google Диск или загрузить существующий дамп.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { viewModel.sendDumpToGoogleDrive() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Сохранить дамп в Google Диск")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.dismissDriveOnboarding()
                            openBackupDocLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Загрузить дамп из Google Диска")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::dismissDriveOnboarding) {
                    Text("Позже (локально)")
                }
            }
        )
    }
}

@Composable
private fun StatLabel(text: String, isAlert: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (isAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (isAlert) FontWeight.SemiBold else FontWeight.Medium
    )
}

@Composable
private fun SyncStatusChip(
    status: SyncStatusType,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when (status) {
                SyncStatusType.SYNCING -> {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                }
                SyncStatusType.SYNCED -> {
                    Icon(Icons.Outlined.CloudDone, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                }
                SyncStatusType.UNSYNCED -> {
                    Icon(Icons.Outlined.CloudUpload, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
                SyncStatusType.ERROR -> {
                    Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                }
                SyncStatusType.OFFLINE -> {
                    Icon(Icons.Outlined.CloudOff, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SyncStatusType.NOT_CONNECTED -> {
                    Icon(Icons.Outlined.CloudSync, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BulkSelectionBar(
    selectedCount: Int,
    onClear: () -> Unit,
    onBulkStatus: (TaskStatus) -> Unit,
    onBulkPriority: (TaskPriority) -> Unit,
    onBulkDelete: () -> Unit
) {
    var showStatusMenu by remember { mutableStateOf(false) }
    var showPriorityMenu by remember { mutableStateOf(false) }

    Surface(tonalElevation = 4.dp, shadowElevation = 6.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Outlined.Close, contentDescription = "Отмена")
                }
                Text("Выбрано: $selectedCount", style = MaterialTheme.typography.titleSmall)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { showStatusMenu = true }) {
                        Icon(Icons.Outlined.SwapHoriz, contentDescription = "Сменить статус")
                    }
                    DropdownMenu(expanded = showStatusMenu, onDismissRequest = { showStatusMenu = false }) {
                        TaskStatus.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.displayName) },
                                onClick = {
                                    showStatusMenu = false
                                    onBulkStatus(s)
                                }
                            )
                        }
                    }
                }
                Box {
                    IconButton(onClick = { showPriorityMenu = true }) {
                        Icon(Icons.Outlined.Flag, contentDescription = "Сменить приоритет")
                    }
                    DropdownMenu(expanded = showPriorityMenu, onDismissRequest = { showPriorityMenu = false }) {
                        TaskPriority.entries.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.displayName) },
                                onClick = {
                                    showPriorityMenu = false
                                    onBulkPriority(p)
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = onBulkDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSortBottomSheet(
    state: MainUiState,
    onDismiss: () -> Unit,
    onUpdateFilter: ((com.personalkanban.app.domain.model.TaskFilterState) -> com.personalkanban.app.domain.model.TaskFilterState) -> Unit,
    onSetSort: (SortOption) -> Unit,
    onClear: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Фильтры и сортировка", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onClear) { Text("Сбросить") }
            }

            Text("Сортировка", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortOption.entries.forEach { opt ->
                    FilterChip(
                        selected = state.filterState.sortOption == opt,
                        onClick = { onSetSort(opt) },
                        label = { Text(opt.displayName) }
                    )
                }
            }

            Text("Статус", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskStatus.entries.forEach { s ->
                    val selected = s in state.filterState.statuses
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onUpdateFilter { f ->
                                f.copy(statuses = if (selected) f.statuses - s else f.statuses + s)
                            }
                        },
                        label = { Text(s.displayName) }
                    )
                }
            }

            Text("Приоритет", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskPriority.entries.forEach { p ->
                    val selected = p in state.filterState.priorities
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onUpdateFilter { f ->
                                f.copy(priorities = if (selected) f.priorities - p else f.priorities + p)
                            }
                        },
                        label = { Text(p.displayName) }
                    )
                }
            }

            Text("Срок", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DueDateFilter.entries.forEach { d ->
                    FilterChip(
                        selected = state.filterState.dueDateFilter == d,
                        onClick = { onUpdateFilter { it.copy(dueDateFilter = d) } },
                        label = { Text(d.displayName) }
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.filterState.onlyOverdue,
                    onClick = { onUpdateFilter { it.copy(onlyOverdue = !it.onlyOverdue) } },
                    label = { Text("Только просроченные") }
                )
                FilterChip(
                    selected = state.filterState.onlyWithComments,
                    onClick = { onUpdateFilter { it.copy(onlyWithComments = !it.onlyWithComments) } },
                    label = { Text("С комментариями") }
                )
            }

            if (state.epics.isNotEmpty()) {
                Text("Тематика (Эпик)", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.epics.forEach { epic ->
                        val selected = state.filterState.epicName.equals(epic.name, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onUpdateFilter { it.copy(epicName = if (selected) null else epic.name) }
                            },
                            label = { Text(epic.name) }
                        )
                    }
                }
            }

            if (state.customers.isNotEmpty()) {
                Text("Заказчик", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.customers.forEach { c ->
                        val selected = state.filterState.customerName.equals(c.name, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onUpdateFilter { it.copy(customerName = if (selected) null else c.name) }
                            },
                            label = { Text(c.name) }
                        )
                    }
                }
            }

            if (state.allLabels.isNotEmpty()) {
                Text("Метки", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.allLabels.forEach { label ->
                        val selected = state.filterState.selectedLabel == label
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onUpdateFilter { it.copy(selectedLabel = if (selected) null else label) }
                            },
                            label = { Text("#$label") }
                        )
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Применить")
            }
        }
    }
}

@Composable
private fun ConflictResolutionDialog(
    conflict: SyncConflict,
    onChoose: (ConflictResolutionChoice) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Конфликт: ${conflict.taskTitle}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Изменения на телефоне и в Google Drive разошлись по полям: ${conflict.conflictFields.joinToString(", ")}.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "На телефоне: «${conflict.localTask.title}» (${conflict.localTask.status.displayName}, ${conflict.localTask.priority.displayName})",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "В Google Drive: «${conflict.remoteTask.title}» (${conflict.remoteTask.status.displayName}, ${conflict.remoteTask.priority.displayName})",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(onClick = { onChoose(ConflictResolutionChoice.KEEP_LOCAL) }) {
                Text("Оставить с телефона")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { onChoose(ConflictResolutionChoice.USE_REMOTE) }) {
                Text("Взять из Drive")
            }
        }
    )
}
