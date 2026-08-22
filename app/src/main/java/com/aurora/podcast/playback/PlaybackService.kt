package com.aurora.podcast.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.aurora.podcast.PodcastApplication
import com.aurora.podcast.R
import com.aurora.podcast.data.db.EpisodeEntity
import com.aurora.podcast.data.repository.PodcastRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 系统媒体服务：
 *  - 持有 MediaSessionCompat，让手表"正在播放"卡片/音量键能控制播放/暂停、上一首/下一首
 *  - MediaBrowserServiceCompat 提供节目浏览与队列
 *  - 前台服务 + MediaStyle 通知
 */
class PlaybackService : MediaBrowserServiceCompat() {

    companion object {
        const val CHANNEL_ID = "podcast_playback"
        const val NOTIFICATION_ID = 1
        private const val MEDIA_ID_ROOT = "root"
        private const val MEDIA_ID_PREFIX = "episode_"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var playerManager: PlayerManager
    private lateinit var repository: PodcastRepository
    private var playlist: List<EpisodeEntity> = emptyList()

    override fun onCreate() {
        super.onCreate()
        createChannel()

        val app = application as PodcastApplication
        repository = app.repository
        playerManager = app.playerManager

        mediaSession = MediaSessionCompat(this, "PodcastPlayback").apply {
            setCallback(sessionCallback)
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            // 让系统"正在播放"卡片可见可控制
            setPlaybackState(playbackState(isPlaying = false))
            isActive = true
        }
        sessionToken = mediaSession.sessionToken

        // PlayerManager 状态 -> MediaSession + 前台通知
        playerManager.onPlaybackStateChanged = { isPlaying ->
            if (isPlaying) ensureStarted()
            mediaSession.setPlaybackState(playbackState(isPlaying))
            startOrUpdateForeground(isPlaying)
        }
        playerManager.onEpisodeChanged = { episode ->
            mediaSession.setMetadata(episode?.let { metadataFrom(it) })
        }
        playerManager.onPlaybackEnded = {
            playerManager.skipToNext()
        }

        // 已下载节目即播放队列
        serviceScope.launch {
            repository.downloadedEpisodes.collect { list ->
                playlist = list
                playerManager.setQueue(list)
            }
        }

        // 播放进度同步到系统媒体卡片（约 250ms 一次）
        serviceScope.launch {
            playerManager.positionMs.collect { pos ->
                if (playerManager.isPlaying.value) {
                    mediaSession.setPlaybackState(playbackState(isPlaying = true))
                }
            }
        }
    }

    private val sessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            ensureStarted()
            playerManager.resume()
        }

        override fun onPause() {
            playerManager.pause()
        }

        override fun onSkipToNext() {
            playerManager.skipToNext()
        }

        override fun onSkipToPrevious() {
            playerManager.skipToPrevious()
        }

        override fun onSeekTo(pos: Long) {
            playerManager.seekTo(pos)
        }

        override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
            val guid = mediaId.removePrefix(MEDIA_ID_PREFIX)
            serviceScope.launch {
                val ep = repository.episodeByGuid(guid)
                if (ep != null) {
                    repository.markLastPlayed(ep.guid)
                    ensureStarted()
                    playerManager.play(ep)
                }
            }
        }

        override fun onStop() {
            stopSelf()
        }
    }

    // ---------------- 状态 / 元数据 ----------------

    private fun playbackState(isPlaying: Boolean): PlaybackStateCompat {
        return PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY
                    or PlaybackStateCompat.ACTION_PAUSE
                    or PlaybackStateCompat.ACTION_PLAY_PAUSE
                    or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    or PlaybackStateCompat.ACTION_SEEK_TO
                    or PlaybackStateCompat.ACTION_STOP
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING
                else PlaybackStateCompat.STATE_PAUSED,
                playerManager.positionMs.value,
                1f
            )
            .build()
    }

    private fun metadataFrom(episode: EpisodeEntity): MediaMetadataCompat {
        return MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, episode.title)
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, MEDIA_ID_PREFIX + episode.guid)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, episode.durationSeconds * 1000L)
            .build()
    }

    // ---------------- 通知 ----------------

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_notification),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.setShowBadge(false)
        nm.createNotificationChannel(channel)
    }

    private fun ensureStarted() {
        val intent = Intent(this, PlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun startOrUpdateForeground(isPlaying: Boolean) {
        val episode = playerManager.currentEpisodeValue()
        val notification = buildNotification(episode, isPlaying)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(episode: EpisodeEntity?, isPlaying: Boolean): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(episode?.title ?: getString(R.string.app_name))
            .setContentText(getString(R.string.app_name))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_prev,
                    "上一首",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    )
                )
            )
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_play,
                    if (isPlaying) "暂停" else "播放",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                )
            )
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_next,
                    "下一首",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    )
                )
            )
            .build()
    }

    // ---------------- MediaBrowser ----------------

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot = BrowserRoot(MEDIA_ID_ROOT, null)

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        val items: MutableList<MediaBrowserCompat.MediaItem> =
            if (parentId == MEDIA_ID_ROOT) {
                playlist.map { it.toMediaItem() }.toMutableList()
            } else {
                mutableListOf()
            }
        result.sendResult(items)
    }

    private fun EpisodeEntity.toMediaItem(): MediaBrowserCompat.MediaItem {
        val desc = MediaDescriptionCompat.Builder()
            .setMediaId(MEDIA_ID_PREFIX + guid)
            .setTitle(title)
            .build()
        return MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 通知栏按钮（MediaButtonReceiver）与媒体按键回调
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        // 解除回调，避免玩家继续引用已销毁的服务
        playerManager.onPlaybackStateChanged = null
        playerManager.onEpisodeChanged = null
        playerManager.onPlaybackEnded = null
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }
}