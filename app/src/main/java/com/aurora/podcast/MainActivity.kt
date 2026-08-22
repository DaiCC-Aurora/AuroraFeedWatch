package com.aurora.podcast

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aurora.podcast.playback.PlaybackService
import com.aurora.podcast.ui.screen.EpisodesScreen
import com.aurora.podcast.ui.screen.PlayerScreen
import com.aurora.podcast.ui.screen.SettingsScreen
import com.aurora.podcast.ui.theme.PodcastTheme

class MainActivity : ComponentActivity() {

    private var mediaBrowser: MediaBrowserCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        connectToPlaybackService()

        setContent {
            PodcastTheme {
                var screen by rememberSaveable { mutableStateOf("episodes") }
                var currentGuid by rememberSaveable { mutableStateOf<String?>(null) }

                when (screen) {
                    "player" -> PlayerScreen(
                        initialGuid = currentGuid,
                        onBack = { screen = "episodes" }
                    )
                    "settings" -> SettingsScreen(onBack = { screen = "episodes" })
                    else -> EpisodesScreen(
                        onOpenPlayer = { guid ->
                            currentGuid = guid
                            screen = "player"
                        },
                        onOpenSettings = { screen = "settings" }
                    )
                }
            }
        }
    }

    /** 连接 MediaBrowser，让 PlaybackService 常驻，系统"正在播放"卡片可用。 */
    private fun connectToPlaybackService() {
        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, PlaybackService::class.java),
            object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    // 已连接：媒体会话已激活
                }
            },
            null
        ).also { it.connect() }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }
    }

    override fun onDestroy() {
        mediaBrowser?.disconnect()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
    }
}