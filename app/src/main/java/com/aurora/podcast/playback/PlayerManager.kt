package com.aurora.podcast.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.aurora.podcast.data.db.AppDatabase
import com.aurora.podcast.data.db.EpisodeEntity
import com.aurora.podcast.data.model.SubtitleCue
import com.aurora.podcast.data.repository.PodcastRepository
import com.aurora.podcast.data.model.SubtitleParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * 播放核心：封装 Media3 ExoPlayer + 字幕装载 + 播放队列（下一首/上一首）。
 * 由 Application 持有单例，PlaybackService 与 UI 共享同一实例。
 */
class PlayerManager(
    private val context: Context,
    private val repository: PodcastRepository
) {

    private var exoPlayer: ExoPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionJob: Job? = null
    private var queue: List<EpisodeEntity> = emptyList()
    private var current: EpisodeEntity? = null
    private val historyDao = AppDatabase.getInstance(context).historyDao()
    private var lastHistoryWriteAt = 0L

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _subtitleCues = MutableStateFlow<List<SubtitleCue>>(emptyList())
    val subtitleCues: StateFlow<List<SubtitleCue>> = _subtitleCues.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentEpisode = MutableStateFlow<EpisodeEntity?>(null)
    val currentEpisode: StateFlow<EpisodeEntity?> = _currentEpisode.asStateFlow()

    /** 播放/暂停状态变化（PlaybackService 据此更新 MediaSession 与通知）。 */
    var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    var onEpisodeChanged: ((EpisodeEntity?) -> Unit)? = null
    var onPlaybackEnded: (() -> Unit)? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (!isPlaying) writeHistoryProgress(completed = false)
            onPlaybackStateChanged?.invoke(isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                writeHistoryProgress(completed = true)
                onPlaybackEnded?.invoke()
            }
        }
    }

    /** 把当前进度（completed=true 时写满）写入播放历史。 */
    private fun writeHistoryProgress(completed: Boolean) {
        val c = current ?: return
        val p = exoPlayer
        // 播放已结束时不重复写"暂停"进度
        if (!completed && p != null && p.playbackState == Player.STATE_ENDED) return
        val total = if (p != null && p.duration > 0) p.duration else 0L
        val pos = if (completed) total else (p?.currentPosition ?: 0L)
        val now = System.currentTimeMillis()
        scope.launch {
            runCatching { historyDao.upsert(c.guid, c.title, now, pos, total, completed) }
        }
    }

    fun setQueue(list: List<EpisodeEntity>) {
        queue = list
    }

    fun currentEpisodeValue(): EpisodeEntity? = current

    fun play(episode: EpisodeEntity) {
        val audioPath = episode.audioLocalPath
        if (audioPath.isNullOrBlank() || !File(audioPath).exists()) {
            Log.w(TAG, "音频文件不存在，无法播放：${episode.guid}（${episode.title}）")
            return
        }
        val player = ensurePlayer()
        current = episode
        _currentEpisode.value = episode
        _subtitleCues.value = loadSubtitles(episode)

        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(audioPath))))
        player.prepare()
        player.playWhenReady = true

        onEpisodeChanged?.invoke(episode)
        startPositionPolling()

        // 记录播放历史（已存在则保留首次播放时间）
        val now = System.currentTimeMillis()
        scope.launch {
            runCatching {
                historyDao.upsert(
                    episode.guid, episode.title, now, 0L,
                    episode.durationSeconds * 1000L, false
                )
            }
        }
    }

    fun togglePlayPause() {
        val p = exoPlayer ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        exoPlayer?.play()
    }

    fun seekTo(ms: Long) {
        exoPlayer?.seekTo(ms)
    }

    fun skipToNext() {
        if (queue.isEmpty()) return
        val idx = queue.indexOfFirst { it.guid == current?.guid }
        val next = if (idx >= 0 && idx + 1 < queue.size) queue[idx + 1] else queue.firstOrNull()
        next?.let { play(it) }
    }

    fun skipToPrevious() {
        if (queue.isEmpty()) return
        val idx = queue.indexOfFirst { it.guid == current?.guid }
        val prev = if (idx > 0) queue[idx - 1] else queue.lastOrNull()
        prev?.let { play(it) }
    }

    /** 释放播放器并复位状态（服务销毁/应用退出时调用）。 */
    fun release() {
        positionJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
        current = null
        _currentEpisode.value = null
        _positionMs.value = 0L
        _subtitleCues.value = emptyList()
        _isPlaying.value = false
    }

    private fun ensurePlayer(): ExoPlayer {
        return exoPlayer ?: ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true
            )
            addListener(playerListener)
            exoPlayer = this
        }
    }

    private fun startPositionPolling() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                val pos = exoPlayer?.currentPosition ?: 0L
                _positionMs.value = pos
                // 播放中每 5 秒持久化一次播放进度（历史页"看到哪了"）
                if (_isPlaying.value) {
                    val now = System.currentTimeMillis()
                    if (now - lastHistoryWriteAt >= 5000L) {
                        lastHistoryWriteAt = now
                        val c = current
                        if (c != null) {
                            val p = exoPlayer
                            val total = if (p != null && p.duration > 0) p.duration else 0L
                            scope.launch {
                                runCatching {
                                    historyDao.upsert(c.guid, c.title, now, pos, total, false)
                                }
                            }
                        }
                    }
                }
                delay(250)
            }
        }
    }

    /**
     * 装载字幕，优先级：
     *  1) 云端带时间戳的 VTT 字幕（subtitleVtt，歌词式实时字幕）
     *  2) 本地字幕文件（.vtt）
     *  3) 纯文本 transcript 兜底（无时间轴，按句均分，仅作应急）
     */
    private fun loadSubtitles(episode: EpisodeEntity): List<SubtitleCue> {
        if (!episode.subtitleVtt.isNullOrBlank()) {
            return SubtitleParser.parse(episode.subtitleVtt, episode.durationSeconds)
        }
        val path = episode.subtitleLocalPath
        if (!path.isNullOrBlank()) {
            val f = File(path)
            if (f.exists()) {
                try {
                    return SubtitleParser.parse(f.readText(), episode.durationSeconds)
                } catch (e: Exception) {
                    Log.w(TAG, "字幕文件解析失败，回退 transcript: ${e.message}")
                }
            }
        }
        return SubtitleParser.parse(episode.transcript, episode.durationSeconds)
    }

    companion object {
        private const val TAG = "PlayerManager"
    }
}