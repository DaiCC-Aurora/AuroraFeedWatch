package com.aurora.podcast.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.podcast.PodcastApplication
import com.aurora.podcast.data.db.DownloadStates
import com.aurora.podcast.data.db.EpisodeEntity
import com.aurora.podcast.work.Scheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    private var messageClearJob: Job? = null
    private var prevStates: Map<String, Int> = emptyMap()

    init {
        refresh()
        // 监听下载状态变化，弹出对应消息（完成/失败），10 秒后自动消失
        viewModelScope.launch {
            repo.episodes.collect { eps ->
                val now = HashMap<String, Int>(eps.size)
                for (ep in eps) {
                    val prev = prevStates[ep.guid]
                    val s = ep.downloadState
                    if (prev != null && prev != s) {
                        when (s) {
                            DownloadStates.COMPLETED -> {
                                _message.value = "✓ 下载完成：${ep.title}"
                                scheduleMessageClear()
                            }
                            DownloadStates.FAILED -> {
                                _message.value = "✗ 下载失败：${ep.title}，点按重试"
                                scheduleMessageClear()
                            }
                        }
                    }
                    now[ep.guid] = s
                }
                prevStates = now
            }
        }
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
            scheduleMessageClear()
        }
    }

    fun download(episode: EpisodeEntity) {
        Scheduler.enqueueSingleDownload(context, episode.guid)
        _message.value = "已加入下载队列：${episode.title}"
        scheduleMessageClear()
    }

    fun clearMessage() {
        messageClearJob?.cancel()
        _message.value = null
    }

    private fun scheduleMessageClear() {
        messageClearJob?.cancel()
        messageClearJob = viewModelScope.launch { delay(10_000); _message.value = null }
    }
}
