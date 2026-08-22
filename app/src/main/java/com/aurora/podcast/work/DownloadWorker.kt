package com.aurora.podcast.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aurora.podcast.data.db.EpisodeEntity
import com.aurora.podcast.data.repository.PodcastRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 下载 Worker：
 *  - 无 inputData[KEY_GUID] 时：自动下载最新 KEY_COUNT（默认 5）期未下载节目。
 *  - 有 KEY_GUID 时：仅下载指定单期（用户手动点击下载）。
 * 状态上报：下载过程中把"下载中 + 进度"写入数据库（列表页显示进度条），
 * 完成写"已完成"，失败写"失败"（用户可点按重试）。
 * 约束（自动下载）：充电中 + 仅 Wi-Fi（由 Scheduler 决定 UNMETERED/CONNECTED）。
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    /** 进度回调是非 suspend lambda，用独立 IO 协程写数据库。 */
    private val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun doWork(): Result {
        val repository = PodcastRepository(applicationContext)
        return try {
            val guid = inputData.getString(KEY_GUID)
            val episodes: List<EpisodeEntity> = if (guid != null) {
                listOfNotNull(repository.episodeByGuid(guid))
            } else {
                val count = inputData.getInt(KEY_COUNT, 5)
                repository.episodesForDownload(count)
            }

            if (episodes.isEmpty()) {
                Log.i(TAG, "没有需要下载的节目")
                return Result.success()
            }

            var ok = 0
            for (ep in episodes) {
                try {
                    downloadEpisode(repository, ep)
                    ok++
                } catch (e: Exception) {
                    Log.w(TAG, "下载失败 ${ep.guid}（${ep.title}）: ${e.message}")
                    runCatching { repository.markDownloadFailed(ep.guid) }
                }
            }
            if (ok > 0) Result.success() else Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "DownloadWorker 异常", e)
            Result.retry()
        }
    }

    private suspend fun downloadEpisode(repository: PodcastRepository, ep: EpisodeEntity) {
        // 音频（断点续传 + 进度上报）
        val audioFile = repository.audioFileFor(ep.guid)
        if (!audioFile.exists()) {
            repository.setDownloading(ep.guid)
            var lastEmitAt = 0L
            var lastPercent = -1
            FileDownloader.download(ep.remoteAudioUrl, audioFile) { cur, total ->
                if (total > 0) {
                    val pct = (cur * 100 / total).toInt()
                    val now = System.currentTimeMillis()
                    // 节流：每 300ms 或整百分点变化时写一次数据库
                    if (now - lastEmitAt >= 300L || pct != lastPercent) {
                        lastEmitAt = now
                        lastPercent = pct
                        val frac = (cur * 1.0f / total).coerceIn(0f, 1f)
                        progressScope.launch {
                            runCatching { repository.updateDownloadProgress(ep.guid, frac) }
                        }
                    }
                }
            }
        }

        // 字幕（若有云端字幕 URL 则下载；否则 transcript 存于数据库可离线兜底）
        var subPath: String? = null
        val subFile = repository.subtitleFileFor(ep.guid)
        if (subFile.exists()) {
            subPath = subFile.absolutePath
        } else if (!ep.remoteSubtitleUrl.isNullOrBlank()) {
            try {
                FileDownloader.download(ep.remoteSubtitleUrl, subFile)
                subPath = subFile.absolutePath
            } catch (e: Exception) {
                Log.w(TAG, "字幕下载失败 ${ep.guid}: ${e.message}")
            }
        }

        repository.markDownloaded(ep.guid, subPath)
        Log.i(TAG, "下载完成 ${ep.guid}（${ep.title}）")
    }

    companion object {
        const val KEY_GUID = "guid"
        const val KEY_COUNT = "count"
        private const val TAG = "DownloadWorker"
    }
}
