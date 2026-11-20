package com.personalkanban.app.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.personalkanban.app.BuildConfig
import com.personalkanban.app.domain.model.ReminderPresets
import com.personalkanban.app.domain.model.ThemeMode
import com.personalkanban.app.ui.components.DateTimeFormatters
import com.personalkanban.app.ui.components.OpenGoogleDriveDocument

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenArchiveTrash: () -> Unit,
    onOpenCustomers: () -> Unit,
    onOpenStats: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var customMinutesInput by remember(state.settings.customReminderMinutes) {
        mutableStateOf(state.settings.customReminderMinutes?.toString().orEmpty())
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleGoogleSignInResult(result.data)
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setNotificationsEnabled(granted)
    }

    val importDumpLauncher = rememberLauncherForActivityResult(
        contract = OpenGoogleDriveDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreFromDumpUri(uri)
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (raw != null) viewModel.importJson(raw)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is SettingsEvent.ShareFile -> {
                    val uri = viewModel.createFileUri(event.file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = event.mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, event.title))
                }
                is SettingsEvent.SendDumpToDrive -> {
                    val uri = viewModel.createFileUri(event.file)
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
                is SettingsEvent.InstallApk -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                        val settingsIntent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(settingsIntent)
                    } else {
                        val uri = viewModel.createFileUri(event.file)
                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(installIntent)
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quick navigation cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenArchiveTrash,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Archive, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Архив/Корзина")
                }
                OutlinedButton(
                    onClick = onOpenCustomers,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.PeopleOutline, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Заказчики")
                }
            }
            OutlinedButton(
                onClick = onOpenStats,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.BarChart, null)
                Spacer(Modifier.width(6.dp))
                Text("Статистика задач")
            }

            HorizontalDivider()

            // Appearance
            SectionTitle("Внешний вид")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(mode.displayName) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Компактные карточки", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = state.settings.compactCards,
                    onCheckedChange = viewModel::setCompactCards
                )
            }

            HorizontalDivider()

            // Notifications
            SectionTitle("Уведомления и напоминания")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Локальные уведомления", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = state.settings.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setNotificationsEnabled(enabled)
                        }
                    }
                )
            }

            Text("Интервалы напоминаний (можно выбрать несколько)", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ReminderPresets.defaultOptions.forEach { (minutes, label) ->
                    FilterChip(
                        selected = minutes in state.settings.reminderIntervalsMinutes,
                        onClick = { viewModel.toggleReminderInterval(minutes) },
                        label = { Text(label) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customMinutesInput,
                    onValueChange = { customMinutesInput = it.filter(Char::isDigit) },
                    label = { Text("Свой интервал (минут до срока)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = {
                    val mins = customMinutesInput.toIntOrNull()
                    viewModel.setCustomReminderMinutes(mins)
                }) {
                    Text("Задать")
                }
            }

            HorizontalDivider()

            // Google Drive Sync
            SectionTitle("Синхронизация с Google Drive")
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = state.settings.googleAccountEmail?.let { "Аккаунт: $it" } ?: "Google-аккаунт не подключён (локальный режим)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Статус: ${state.settings.lastSyncStatus.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.settings.lastSyncAt?.let { ts ->
                        Text(
                            text = "Последняя синхронизация: ${DateTimeFormatters.formatTimestamp(ts)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    state.settings.lastSyncError?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (state.settings.googleAccountEmail == null) {
                Button(
                    onClick = viewModel::sendDumpToGoogleDrive,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.CloudSync, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Сохранить дамп в Google Диск")
                }
                OutlinedButton(
                    onClick = { importDumpLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.CloudDownload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Загрузить дамп из Google Диска")
                }
                TextButton(
                    onClick = { googleSignInLauncher.launch(viewModel.getGoogleSignInIntent()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("OAuth-синхронизация (требует настройки SHA-1 в Google Cloud)")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.syncNow(createSnapshot = false) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Синхронизировать")
                    }
                    OutlinedButton(
                        onClick = { viewModel.syncNow(createSnapshot = true) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Создать копию")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = viewModel::verifyBackupIntegrity,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Проверить копию")
                    }
                    OutlinedButton(
                        onClick = { viewModel.requestRestore(null) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Восстановить")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = viewModel::loadSnapshots,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.CloudDownload, null)
                        Spacer(Modifier.width(4.dp))
                        Text("История копий")
                    }
                    TextButton(
                        onClick = viewModel::disconnectGoogleAccount,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Отключить", color = MaterialTheme.colorScheme.error)
                    }
                }

                if (state.snapshots.isNotEmpty()) {
                    Text("Доступные резервные копии:", style = MaterialTheme.typography.labelMedium)
                    state.snapshots.forEach { snap ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = snap.fileName,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.requestRestore(snap.fileId) }) {
                                Text("Восстановить")
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // GitHub Updates
            SectionTitle("Обновления приложения")
            Text(
                text = "Текущая версия: ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = viewModel::checkForUpdates,
                enabled = !state.checkingUpdate,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.checkingUpdate) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.SystemUpdate, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Проверить обновления")
                }
            }

            state.updateStatusMessage?.let { msg ->
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            state.availableRelease?.let { release ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Новая версия: ${release.versionName}", style = MaterialTheme.typography.titleSmall)
                        if (release.releaseNotes.isNotBlank()) {
                            Text(release.releaseNotes, style = MaterialTheme.typography.bodySmall)
                        }
                        state.downloadProgressPercent?.let { pct ->
                            LinearProgressIndicator(
                                progress = { pct / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Загрузка: $pct%", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = { viewModel.downloadAndInstallUpdate(release) },
                            enabled = state.downloadProgressPercent == null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Скачать и установить")
                        }
                    }
                }
            }

            HorizontalDivider()

            // Data Export/Import
            SectionTitle("Данные")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = viewModel::exportJson,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Экспорт JSON")
                }
                OutlinedButton(
                    onClick = viewModel::exportCsv,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Экспорт CSV")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { importJsonLauncher.launch("application/json") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Импорт JSON")
                }
                OutlinedButton(
                    onClick = viewModel::clearLocalCache,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.DeleteOutline, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Очистить кэш")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (state.showOverwriteConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissOverwriteConfirm,
            title = { Text("Есть несинхронизированные изменения") },
            text = {
                Text("На устройстве есть локальные изменения, которые ещё не отправлены в Google Drive. Перезаписать их резервной копией?")
            },
            confirmButton = {
                Button(onClick = { viewModel.requestRestore(state.pendingRestoreFileId, force = true) }) {
                    Text("Перезаписать")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissOverwriteConfirm) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}
