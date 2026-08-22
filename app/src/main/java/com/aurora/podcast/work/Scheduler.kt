package com.aurora.podcast.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.aurora.podcast.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * WorkManager 调度器：
 *  - 自动下载：应用启动/后台刷新后执行（充电 + 网络约束，仅 Wi-Fi 开关生效时用 UNMETERED）
 *  - 每日清理：每天凌晨 3:00 附近执行
 *  - 单期下载：用户在列表点击"下载"时执行（仅需网络，不要求充电）
 */
object Scheduler {

    private const val UNIQUE_AUTO_DOWNLOAD = "auto_download"
    private const val UNIQUE_CLEANUP = "cleanup"
    private const val UNIQUE_SINGLE_PREFIX = "single_download_"
    private const val AUTO_DOWNLOAD_COUNT = 5

    /** 应用启动时调用：调度自动下载 + 每日清理。 */
    fun scheduleOnAppStart(context: Context) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            val settings = SettingsRepository(context).current()
            enqueueAutoDownload(context, settings.wifiOnly, requiresCharging = true)
            enqueueDailyCleanup(context)
            // 数据可能已在清理后变化；无额外动作
        }
    }

    /** 云端刷新成功后调用：立即调度一次自动下载（不要求充电，方便演示）。 */
    fun enqueueDownloadAfterRefresh(context: Context) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            val settings = SettingsRepository(context).current()
            enqueueAutoDownload(context, settings.wifiOnly, requiresCharging = false)
        }
    }

    /** 用户点击某期"下载"。 */
    fun enqueueSingleDownload(context: Context, guid: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(workDataOf(DownloadWorker.KEY_GUID to guid))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_SINGLE_PREFIX + guid,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun enqueueAutoDownload(context: Context, wifiOnly: Boolean, requiresCharging: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiresCharging(requiresCharging)
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(DownloadWorker.KEY_COUNT to AUTO_DOWNLOAD_COUNT))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_AUTO_DOWNLOAD,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun enqueueDailyCleanup(context: Context) {
        val request = PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(millisUntilNext3am(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_CLEANUP,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** 距下一个凌晨 3:00 的毫秒数。 */
    private fun millisUntilNext3am(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}