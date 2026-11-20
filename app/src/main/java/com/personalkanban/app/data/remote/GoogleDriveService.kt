package com.personalkanban.app.data.remote

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.personalkanban.app.domain.model.BackupSnapshotInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

class DriveAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Singleton
class GoogleDriveService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    companion object {
        private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val OAUTH_SCOPE = "oauth2:$DRIVE_FILE_SCOPE"
        private const val FOLDER_NAME = "PersonalKanban"
        private const val LATEST_BACKUP_NAME = "backup_latest.json"
        private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
    }

    private fun getSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_FILE_SCOPE))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun getSignInIntent(): Intent = getSignInClient().signInIntent

    fun getLastSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    suspend fun signOut() {
        runCatching { getSignInClient().signOut().await() }
    }

    private suspend fun getAccessToken(accountEmail: String): String = withContext(Dispatchers.IO) {
        val account = getLastSignedInAccount()?.account ?: Account(accountEmail, "com.google")
        try {
            GoogleAuthUtil.getToken(context, account, OAUTH_SCOPE)
        } catch (e: Exception) {
            throw DriveAuthException("Требуется повторная авторизация Google: ${e.localizedMessage}", e)
        }
    }

    suspend fun ensureAppFolder(accountEmail: String, cachedFolderId: String?): String = withContext(Dispatchers.IO) {
        val token = getAccessToken(accountEmail)
        if (!cachedFolderId.isNullOrBlank()) {
            val checkReq = Request.Builder()
                .url("$DRIVE_FILES_URL/$cachedFolderId?fields=id,trashed")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            val exists = runCatching {
                okHttpClient.newCall(checkReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val item = json.decodeFromString<DriveFileDto>(body)
                        !item.trashed
                    } else false
                }
            }.getOrDefault(false)
            if (exists) return@withContext cachedFolderId
        }

        val query = URLEncoder.encode(
            "name = '$FOLDER_NAME' and mimeType = 'application/vnd.google-apps.folder' and trashed = false",
            "UTF-8"
        )
        val searchReq = Request.Builder()
            .url("$DRIVE_FILES_URL?q=$query&fields=files(id,name)&spaces=drive")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        val foundId = executeWithRetry {
            okHttpClient.newCall(searchReq).execute().use { resp ->
                ensureSuccess(resp.code, resp.body?.string())
                val list = json.decodeFromString<DriveFileListDto>(resp.body?.string() ?: "{}")
                list.files.firstOrNull()?.id
            }
        }
        if (foundId != null) return@withContext foundId

        val createMeta = """{"name":"$FOLDER_NAME","mimeType":"application/vnd.google-apps.folder"}"""
        val createReq = Request.Builder()
            .url("$DRIVE_FILES_URL?fields=id")
            .header("Authorization", "Bearer $token")
            .post(createMeta.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        executeWithRetry {
            okHttpClient.newCall(createReq).execute().use { resp ->
                val body = resp.body?.string()
                ensureSuccess(resp.code, body)
                json.decodeFromString<DriveFileDto>(body ?: "{}").id
            }
        }
    }

    suspend fun downloadLatestBackup(accountEmail: String, folderId: String): BackupPayload? = withContext(Dispatchers.IO) {
        val token = getAccessToken(accountEmail)
        val fileId = findFileInFolder(token, folderId, LATEST_BACKUP_NAME) ?: return@withContext null
        downloadBackupByFileId(accountEmail, fileId)
    }

    suspend fun downloadBackupByFileId(accountEmail: String, fileId: String): BackupPayload = withContext(Dispatchers.IO) {
        val token = getAccessToken(accountEmail)
        val req = Request.Builder()
            .url("$DRIVE_FILES_URL/$fileId?alt=media")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        executeWithRetry {
            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string()
                ensureSuccess(resp.code, body)
                json.decodeFromString(BackupPayload.serializer(), body ?: throw IOException("Пустой ответ от Google Drive"))
            }
        }
    }

    suspend fun uploadBackup(
        accountEmail: String,
        folderId: String,
        payload: BackupPayload,
        createSnapshot: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val token = getAccessToken(accountEmail)
        val jsonContent = json.encodeToString(BackupPayload.serializer(), payload)
        val existingId = findFileInFolder(token, folderId, LATEST_BACKUP_NAME)

        if (existingId != null) {
            updateFileContent(token, existingId, jsonContent)
        } else {
            createFileInFolder(token, folderId, LATEST_BACKUP_NAME, jsonContent)
        }

        if (createSnapshot) {
            val snapshotName = "backup_${payload.exportedAt}.json"
            createFileInFolder(token, folderId, snapshotName, jsonContent)
        }
    }

    suspend fun listBackupSnapshots(accountEmail: String, folderId: String): List<BackupSnapshotInfo> = withContext(Dispatchers.IO) {
        val token = getAccessToken(accountEmail)
        val query = URLEncoder.encode("'$folderId' in parents and trashed = false", "UTF-8")
        val req = Request.Builder()
            .url("$DRIVE_FILES_URL?q=$query&orderBy=modifiedTime desc&pageSize=20&fields=files(id,name,modifiedTime,size)")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        executeWithRetry {
            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string()
                ensureSuccess(resp.code, body)
                val list = json.decodeFromString<DriveFileListDto>(body ?: "{}")
                list.files.map {
                    BackupSnapshotInfo(
                        fileId = it.id,
                        fileName = it.name,
                        modifiedTime = it.modifiedTime.orEmpty(),
                        sizeBytes = it.size?.toLongOrNull() ?: 0L
                    )
                }
            }
        }
    }

    private fun findFileInFolder(token: String, folderId: String, fileName: String): String? {
        val query = URLEncoder.encode("name = '$fileName' and '$folderId' in parents and trashed = false", "UTF-8")
        val req = Request.Builder()
            .url("$DRIVE_FILES_URL?q=$query&fields=files(id,name)")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        return okHttpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string()
            ensureSuccess(resp.code, body)
            val list = json.decodeFromString<DriveFileListDto>(body ?: "{}")
            list.files.firstOrNull()?.id
        }
    }

    private fun createFileInFolder(token: String, folderId: String, fileName: String, content: String) {
        val metadataJson = """{"name":"$fileName","parents":["$folderId"],"mimeType":"application/json"}"""
        val multipart = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadataJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .addPart(content.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val req = Request.Builder()
            .url("$DRIVE_UPLOAD_URL?uploadType=multipart&fields=id")
            .header("Authorization", "Bearer $token")
            .post(multipart)
            .build()

        okHttpClient.newCall(req).execute().use { resp ->
            ensureSuccess(resp.code, resp.body?.string())
        }
    }

    private fun updateFileContent(token: String, fileId: String, content: String) {
        val req = Request.Builder()
            .url("$DRIVE_UPLOAD_URL/$fileId?uploadType=media")
            .header("Authorization", "Bearer $token")
            .patch(content.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        okHttpClient.newCall(req).execute().use { resp ->
            ensureSuccess(resp.code, resp.body?.string())
        }
    }

    private fun ensureSuccess(code: Int, body: String?) {
        when (code) {
            in 200..299 -> return
            401, 403 -> throw DriveAuthException("Ошибка авторизации Google Drive (HTTP $code). Переподключите аккаунт.")
            else -> throw IOException("Ошибка Google Drive (HTTP $code): ${body?.take(200)}")
        }
    }

    private suspend fun <T> executeWithRetry(maxAttempts: Int = 3, block: () -> T): T {
        var attempt = 0
        var delayMs = 800L
        while (true) {
            try {
                return block()
            } catch (e: DriveAuthException) {
                throw e
            } catch (e: IOException) {
                attempt++
                if (attempt >= maxAttempts) throw e
                delay(delayMs)
                delayMs *= 2
            }
        }
    }

    @Serializable
    private data class DriveFileListDto(
        val files: List<DriveFileDto> = emptyList()
    )

    @Serializable
    private data class DriveFileDto(
        val id: String = "",
        val name: String = "",
        val modifiedTime: String? = null,
        val size: String? = null,
        val trashed: Boolean = false
    )
}
