package com.aurora.podcast.data.repository

import android.content.Context
import android.util.Log
import com.aurora.podcast.data.db.AppDatabase
import com.aurora.podcast.data.db.EpisodeEntity
import com.aurora.podcast.data.network.NetworkModule
import com.aurora.podcast.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.time.Instant

/**
 * 数据仓库：组合云端 Feed + Room + 本地文件系统。
 */
class PodcastRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).episodeDao()
    private val settingsRepository = SettingsRepository(context)

    private val filesDir = context.filesDir
    val audioDir = File(filesDir, "audio").apply { mkdirs() }
    val subtitleDir = File(filesDir, "subtitle").apply { mkdirs() }

    val episodes: Flow<List<EpisodeEntity>> = dao.observeAll()
    val downloadedEpisodes: Flow<List<EpisodeEntity>> = dao.observeDownloaded()

    // ---------------- 列表 / 查询 ----------------

    fun audioFileFor(guid: String): File = File(audioDir, safeFileName(guid) + ".audio")

    fun subtitleFileFor(guid: String): File = File(subtitleDir, safeFileName(guid) + ".vtt")

    suspend fun episodeByGuid(guid: String): EpisodeEntity? = dao.getByGuid(guid)

    /** 按时间倒序取未下载且有音频 URL 的前 count 期。 */
    suspend fun episodesForDownload(count: Int): List<EpisodeEntity> =
        dao.getAll()
            .asSequence()
            .filter { !it.isDownloaded && it.remoteAudioUrl.isNotBlank() }
            .sortedByDescending { it.pubDate }
            .take(count)
            .toList()

    // ---------------- 同步 ----------------

    /** 从云端 /api/feed 拉取节目列表并 upsert 进 Room，返回新增/更新条数。 */
    suspend fun refreshFromCloud(): Int {
        val resp = NetworkModule.apiService.getFeed(50)
        val items = resp.items.orEmpty()
        val entities = items.mapNotNull { dto ->
            val guid = dto.guid ?: return@mapNotNull null
            val audioUrl = dto.audioUrl ?: return@mapNotNull null
            EpisodeEntity(
                guid = guid,
                title = dto.title ?: "未命名",
                pubDate = parseIsoToMillis(dto.pubDate),
                durationSeconds = dto.durationSeconds ?: 0,
                audioLocalPath = null,
                subtitleLocalPath = null,
                remoteAudioUrl = audioUrl,
                remoteSubtitleUrl = dto.subtitleUrl,
                isDownloaded = false,
                transcript = dto.transcript
            )
        }
        if (entities.isNotEmpty()) dao.upsertAll(entities)
        return entities.size
    }

    /** 应用启动时扫描本地文件，恢复已下载状态（如数据库被清或应用重装场景）。 */
    suspend fun reconcileLocalFiles() {
        for (ep in dao.getAll()) {
            if (ep.isDownloaded) continue
            val audio = audioFileFor(ep.guid)
            if (audio.exists()) {
                val sub = subtitleFileFor(ep.guid).takeIf { it.exists() }?.absolutePath
                dao.updateDownloadState(ep.guid, audio.absolutePath, sub, isDownloaded = true)
            }
        }
    }

    // ---------------- 下载状态 ----------------

    suspend fun markDownloaded(guid: String, subtitleLocalPath: String?) {
        val audioPath = audioFileFor(guid).takeIf { it.exists() }?.absolutePath
        dao.updateDownloadState(guid, audioPath, subtitleLocalPath, isDownloaded = true)
    }

    suspend fun markLastPlayed(guid: String) = settingsRepository.setLastPlayedGuid(guid)

    // ---------------- 清理 ----------------

    suspend fun currentKeepCount(): Int = settingsRepository.current().keepEpisodes

    /**
     * 清理：仅保留最近 keepCount 期已下载节目；
     * 当前正在播放（最近播放）的节目即使超出也不删除。
     * @return 移除的节目数
     */
    suspend fun cleanup(keepCount: Int): Int {
        val lastPlayed = settingsRepository.lastPlayedGuid()
        val downloaded = dao.getDownloaded() // 已按 pubDate 倒序
        val toDelete = downloaded.drop(keepCount).filter { it.guid != lastPlayed }

        var removed = 0
        for (ep in toDelete) {
            runCatching { ep.audioLocalPath?.let { File(it).delete() } }
            runCatching { ep.subtitleLocalPath?.let { File(it).delete() } }
            audioFileFor(ep.guid).delete()
            subtitleFileFor(ep.guid).delete()
            dao.updateDownloadState(ep.guid, null, null, isDownloaded = false)
            removed++
        }
        settingsRepository.setLastCleanup(System.currentTimeMillis())
        return removed
    }

    // ---------------- 工具 ----------------

    private fun safeFileName(guid: String): String =
        guid.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)

    private fun parseIsoToMillis(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        return try {
            Instant.parse(iso.trim()).toEpochMilli()
        } catch (e: Exception) {
            Log.w(TAG, "无法解析时间: $iso")
            0L
        }
    }

    companion object {
        private const val TAG = "PodcastRepository"
    }
}