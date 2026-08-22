package com.aurora.podcast.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aurora.podcast.data.db.EpisodeEntity
import com.aurora.podcast.data.repository.PodcastRepository

/**
 * 下载 Worker：
 *  - 无 inputData[KEY_GUID] 时：自动下载最新 KEY_COUNT（默认 5）期未下载节目。
 *  - 有 KEY_GUID 时：仅下载指定单期（用户手动点击下载）。
 * 约束（自动下载）：充电中 + 仅 Wi-Fi（由 Scheduler 决定 UNMETERED/CONNECTED）。
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

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
                }
            }
            if (ok > 0) Result.success() else Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "DownloadWorker 异常", e)
            Result.retry()
        }
    }

    private suspend fun downloadEpisode(repository: PodcastRepository, ep: EpisodeEntity) {
        // 音频（断点续传）
        val audioFile = repository.audioFileFor(ep.guid)
        if (!audioFile.exists()) {
            FileDownloader.download(ep.remoteAudioUrl, audioFile)
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