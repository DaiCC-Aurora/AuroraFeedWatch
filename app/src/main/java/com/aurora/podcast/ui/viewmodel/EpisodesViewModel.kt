package com.aurora.podcast.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.podcast.PodcastApplication
import com.aurora.podcast.data.db.EpisodeEntity
import com.aurora.podcast.work.Scheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EpisodesViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as PodcastApplication).repository
    private val context = app.applicationContext

    val episodes: StateFlow<List<EpisodeEntity>> = repo.episodes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                val n = repo.refreshFromCloud()
                _message.value = if (n > 0) "已获取 $n 期新节目" else "已是最新"
                // 拉取成功后调度（Wi-Fi/充电约束内的）自动下载
                Scheduler.enqueueDownloadAfterRefresh(context)
            } catch (e: Exception) {
                _message.value = "更新失败：${e.message}"
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun download(episode: EpisodeEntity) {
        Scheduler.enqueueSingleDownload(context, episode.guid)
        _message.value = "已加入下载队列：${episode.title}"
    }

    fun clearMessage() {
        _message.value = null
    }
}