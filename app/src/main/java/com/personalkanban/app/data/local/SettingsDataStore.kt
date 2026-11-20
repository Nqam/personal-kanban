package com.personalkanban.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.personalkanban.app.domain.model.AppSettings
import com.personalkanban.app.domain.model.SyncStatusType
import com.personalkanban.app.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "personal_kanban_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val COMPACT_CARDS = booleanPreferencesKey("compact_cards")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val REMINDER_INTERVALS = stringSetPreferencesKey("reminder_intervals")
        val CUSTOM_REMINDER_MINUTES = intPreferencesKey("custom_reminder_minutes")
        val GOOGLE_ACCOUNT_EMAIL = stringPreferencesKey("google_account_email")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val LAST_SYNC_STATUS = stringPreferencesKey("last_sync_status")
        val LAST_SYNC_ERROR = stringPreferencesKey("last_sync_error")
        val DRIVE_FOLDER_ID = stringPreferencesKey("drive_folder_id")
        val COLLAPSED_STATUSES = stringSetPreferencesKey("collapsed_statuses")
        val DRIVE_PROMPT_DISMISSED = booleanPreferencesKey("drive_prompt_dismissed")
        val BACKUP_FILE_URI = stringPreferencesKey("backup_file_uri")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val theme = prefs[Keys.THEME_MODE]?.let {
            runCatching { ThemeMode.valueOf(it) }.getOrNull()
        } ?: ThemeMode.SYSTEM

        val intervals = prefs[Keys.REMINDER_INTERVALS]
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: setOf(30, 1440)

        val syncStatus = prefs[Keys.LAST_SYNC_STATUS]?.let {
            runCatching { SyncStatusType.valueOf(it) }.getOrNull()
        } ?: if (prefs[Keys.GOOGLE_ACCOUNT_EMAIL] != null) {
            SyncStatusType.UNSYNCED
        } else {
            SyncStatusType.NOT_CONNECTED
        }

        AppSettings(
            themeMode = theme,
            compactCards = prefs[Keys.COMPACT_CARDS] ?: false,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            reminderIntervalsMinutes = intervals,
            customReminderMinutes = prefs[Keys.CUSTOM_REMINDER_MINUTES],
            googleAccountEmail = prefs[Keys.GOOGLE_ACCOUNT_EMAIL],
            lastSyncAt = prefs[Keys.LAST_SYNC_AT],
            lastSyncStatus = syncStatus,
            lastSyncError = prefs[Keys.LAST_SYNC_ERROR],
            driveFolderId = prefs[Keys.DRIVE_FOLDER_ID]
        )
    }

    val collapsedStatusesFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.COLLAPSED_STATUSES] ?: emptySet()
    }

    val drivePromptDismissedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.DRIVE_PROMPT_DISMISSED] ?: false
    }

    val backupFileUriFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.BACKUP_FILE_URI]
    }

    suspend fun getSettings(): AppSettings = settingsFlow.first()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setCompactCards(compact: Boolean) {
        context.dataStore.edit { it[Keys.COMPACT_CARDS] = compact }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setReminderIntervals(intervals: Set<Int>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.REMINDER_INTERVALS] = intervals.map { it.toString() }.toSet()
        }
    }

    suspend fun setCustomReminderMinutes(minutes: Int?) {
        context.dataStore.edit { prefs ->
            if (minutes == null || minutes <= 0) {
                prefs.remove(Keys.CUSTOM_REMINDER_MINUTES)
            } else {
                prefs[Keys.CUSTOM_REMINDER_MINUTES] = minutes
            }
        }
    }

    suspend fun setGoogleAccount(email: String?) {
        context.dataStore.edit { prefs ->
            if (email == null) {
                prefs.remove(Keys.GOOGLE_ACCOUNT_EMAIL)
                prefs.remove(Keys.DRIVE_FOLDER_ID)
                prefs[Keys.LAST_SYNC_STATUS] = SyncStatusType.NOT_CONNECTED.name
            } else {
                prefs[Keys.GOOGLE_ACCOUNT_EMAIL] = email
                if (prefs[Keys.LAST_SYNC_STATUS] == SyncStatusType.NOT_CONNECTED.name || prefs[Keys.LAST_SYNC_STATUS] == null) {
                    prefs[Keys.LAST_SYNC_STATUS] = SyncStatusType.UNSYNCED.name
                }
            }
        }
    }

    suspend fun setDriveFolderId(folderId: String?) {
        context.dataStore.edit { prefs ->
            if (folderId == null) prefs.remove(Keys.DRIVE_FOLDER_ID)
            else prefs[Keys.DRIVE_FOLDER_ID] = folderId
        }
    }

    suspend fun updateSyncState(
        status: SyncStatusType,
        lastSyncAt: Long? = null,
        error: String? = null
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_STATUS] = status.name
            if (lastSyncAt != null) {
                prefs[Keys.LAST_SYNC_AT] = lastSyncAt
            }
            if (error != null) {
                prefs[Keys.LAST_SYNC_ERROR] = error
            } else {
                prefs.remove(Keys.LAST_SYNC_ERROR)
            }
        }
    }

    suspend fun toggleCollapsedStatus(statusName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.COLLAPSED_STATUSES] ?: emptySet()
            prefs[Keys.COLLAPSED_STATUSES] = if (statusName in current) {
                current - statusName
            } else {
                current + statusName
            }
        }
    }

    suspend fun setDrivePromptDismissed(dismissed: Boolean) {
        context.dataStore.edit { it[Keys.DRIVE_PROMPT_DISMISSED] = dismissed }
    }

    suspend fun setBackupFileUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(Keys.BACKUP_FILE_URI)
            else prefs[Keys.BACKUP_FILE_URI] = uri
        }
    }
}
