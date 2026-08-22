package com.aurora.podcast.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aurora.podcast.data.repository.PodcastRepository

/**
 * 清理 Worker：按设置中的保留期数（默认 10）删除最旧且非当前播放的本地文件，
 * 并更新数据库（isDownloaded=false）。
 * 由 Scheduler 调度为每日定时（凌晨 3 点）执行。
 */
class CleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val repository = PodcastRepository(applicationContext)
            val keep = repository.currentKeepCount()
            val removed = repository.cleanup(keep)
            Log.i(TAG, "清理完成：移除 $removed 期过期节目（保留 $keep 期）")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "清理失败", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "CleanupWorker"
    }
}