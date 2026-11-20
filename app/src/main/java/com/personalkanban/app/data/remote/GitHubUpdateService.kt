package com.personalkanban.app.data.remote

import android.content.Context
import com.personalkanban.app.BuildConfig
import com.personalkanban.app.domain.model.ReleaseInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpdateDownloadState {
    data class Progress(val percent: Int, val downloadedBytes: Long, val totalBytes: Long) : UpdateDownloadState()
    data class Completed(val apkFile: File, val sha256Verified: Boolean) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

@Singleton
class GitHubUpdateService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    companion object {
        private const val GITHUB_API_BASE = "https://api.github.com"
    }

    suspend fun checkForUpdate(
        owner: String = BuildConfig.GITHUB_OWNER,
        repo: String = BuildConfig.GITHUB_REPO,
        currentVersion: String = BuildConfig.VERSION_NAME
    ): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$GITHUB_API_BASE/repos/$owner/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "PersonalKanban-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    return@runCatching null
                }
                if (!response.isSuccessful) {
                    throw IOException("GitHub API вернул HTTP ${response.code}")
                }
                val body = response.body?.string() ?: throw IOException("Пустой ответ от GitHub API")
                val dto = json.decodeFromString<GitHubReleaseDto>(body)
                val remoteVersion = dto.tagName.removePrefix("v").trim()
                if (!isNewerVersion(remoteVersion, currentVersion.removeSuffix("-debug"))) {
                    return@runCatching null
                }
                val apkAsset = dto.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                val shaAsset = dto.assets.firstOrNull { it.name.endsWith(".sha256", ignoreCase = true) }
                ReleaseInfo(
                    tagName = dto.tagName,
                    versionName = remoteVersion,
                    releaseNotes = dto.body.orEmpty(),
                    apkDownloadUrl = apkAsset?.browserDownloadUrl,
                    sha256DownloadUrl = shaAsset?.browserDownloadUrl,
                    publishedAt = dto.publishedAt.orEmpty()
                )
            }
        }
    }

    fun downloadAndVerifyApk(release: ReleaseInfo): Flow<UpdateDownloadState> = flow {
        val url = release.apkDownloadUrl
        if (url.isNullOrBlank()) {
            emit(UpdateDownloadState.Error("В релизе не найден APK-файл"))
            return@flow
        }

        // Verify source origin is GitHub
        if (!url.startsWith("https://github.com/") && !url.startsWith("https://objects.githubusercontent.com/")) {
            emit(UpdateDownloadState.Error("Недоверенный источник загрузки: $url"))
            return@flow
        }

        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val targetFile = File(updatesDir, "PersonalKanban-${release.versionName}.apk")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PersonalKanban-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(UpdateDownloadState.Error("Ошибка загрузки APK: HTTP ${response.code}"))
                    return@flow
                }
                val body = response.body ?: run {
                    emit(UpdateDownloadState.Error("Пустое тело ответа при загрузке APK"))
                    return@flow
                }
                val totalBytes = body.contentLength()
                var downloaded = 0L
                val buffer = ByteArray(8192)
                val digest = MessageDigest.getInstance("SHA-256")

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            val percent = if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt() else -1
                            emit(UpdateDownloadState.Progress(percent, downloaded, totalBytes))
                        }
                    }
                }

                val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                val expectedShaUrl = release.sha256DownloadUrl
                var shaVerified = false

                if (!expectedShaUrl.isNullOrBlank()) {
                    val expectedSha = fetchExpectedSha256(expectedShaUrl)
                    if (expectedSha != null) {
                        if (!expectedSha.equals(actualSha256, ignoreCase = true)) {
                            targetFile.delete()
                            emit(UpdateDownloadState.Error("Ошибка целостности: контрольная сумма SHA-256 не совпадает"))
                            return@flow
                        }
                        shaVerified = true
                    }
                }

                emit(UpdateDownloadState.Completed(targetFile, shaVerified))
            }
        } catch (e: Exception) {
            emit(UpdateDownloadState.Error(e.localizedMessage ?: "Ошибка загрузки обновления"))
        }
    }.flowOn(Dispatchers.IO)

    private fun fetchExpectedSha256(url: String): String? {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "PersonalKanban-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val raw = resp.body?.string()?.trim().orEmpty()
                raw.split(Regex("\\s+")).firstOrNull()?.takeIf { it.length == 64 }
            }
        }.getOrNull()
    }

    fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    @Serializable
    private data class GitHubReleaseDto(
        @SerialName("tag_name") val tagName: String = "",
        val name: String? = null,
        val body: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        val assets: List<GitHubAssetDto> = emptyList()
    )

    @Serializable
    private data class GitHubAssetDto(
        val name: String = "",
        @SerialName("browser_download_url") val browserDownloadUrl: String = ""
    )
}
