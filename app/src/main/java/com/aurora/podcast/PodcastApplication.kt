package com.aurora.podcast

import android.app.Application
import android.util.Log
import com.aurora.podcast.data.db.AppDatabase
import com.aurora.podcast.data.repository.PodcastRepository
import com.aurora.podcast.data.settings.SettingsRepository
import com.aurora.podcast.playback.PlayerManager
import com.aurora.podcast.work.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PodcastApplication : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var repository: PodcastRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var playerManager: PlayerManager
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        settingsRepository = SettingsRepository(this)
        repository = PodcastRepository(this)
        playerManager = PlayerManager(this, repository)

        appScope.launch {
            runCatching { repository.reconcileLocalFiles() }
                .onFailure { Log.e("PodcastApplication", "恢复本地文件失败", it) }
            // 调度：自动下载 + 每日清理
            Scheduler.scheduleOnAppStart(this@PodcastApplication)
        }
    }
}