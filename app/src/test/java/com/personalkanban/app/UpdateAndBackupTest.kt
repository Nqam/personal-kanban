package com.personalkanban.app

import com.personalkanban.app.data.local.TaskSnapshotDto
import com.personalkanban.app.data.remote.BackupCommentDto
import com.personalkanban.app.data.remote.BackupPayload
import com.personalkanban.app.data.remote.GitHubUpdateService
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateAndBackupTest {

    @Test
    fun `semver comparison detects newer versions accurately`() {
        val service = GitHubUpdateService(
            context = io.mockk.mockk(relaxed = true),
            okHttpClient = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true }
        )

        assertTrue(service.isNewerVersion("1.0.1", "1.0.0"))
        assertTrue(service.isNewerVersion("1.1.0", "1.0.9"))
        assertTrue(service.isNewerVersion("2.0.0", "1.9.9"))
        assertFalse(service.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(service.isNewerVersion("0.9.9", "1.0.0"))
    }

    @Test
    fun `backup sha256 checksum is deterministic and detects tampering`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val tasks = listOf(
            TaskSnapshotDto(id = "1", title = "Test", createdAt = 100L, updatedAt = 100L)
        )
        val comments = listOf(
            BackupCommentDto(id = "c1", taskId = "1", text = "Hello", createdAt = 100L, updatedAt = 100L)
        )

        val hash1 = BackupPayload.computeDataChecksum(tasks, comments, emptyList(), json)
        val hash2 = BackupPayload.computeDataChecksum(tasks, comments, emptyList(), json)
        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length)

        val tampered = listOf(
            TaskSnapshotDto(id = "1", title = "Tampered", createdAt = 100L, updatedAt = 100L)
        )
        val hash3 = BackupPayload.computeDataChecksum(tampered, comments, emptyList(), json)
        assertNotEquals(hash1, hash3)
    }
}
