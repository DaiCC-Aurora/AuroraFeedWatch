package com.aurora.podcast.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.aurora.podcast.PodcastApplication
import com.aurora.podcast.data.db.EpisodeEntity
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * 缓存列表页：列出本地已下载的节目，显示文件大小，点按播放，支持清空。
 */
@Composable
fun CacheScreen(
    onBack: () -> Unit,
    onPlay: (String) -> Unit
) {
    val app = LocalContext.current.applicationContext as PodcastApplication
    val repo = app.repository
    val episodes by repo.downloadedEpisodes.collectAsState(initial = emptyList())

    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }

    val totalBytes = episodes.sumOf { ep ->
        (ep.audioLocalPath?.let { File(it).length() } ?: 0L) +
            (ep.subtitleLocalPath?.let { File(it).length() } ?: 0L)
    }

    Scaffold {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text("缓存列表", style = MaterialTheme.typography.title3)
            }
            item {
                Text(
                    "共 ${episodes.size} 期 · ${formatBytes(totalBytes)}",
                    style = MaterialTheme.typography.caption2,
                    color = Color(0xFF9E9E9E)
                )
            }
            if (msg != null) {
                item {
                    Text(msg!!, style = MaterialTheme.typography.caption2, color = Color(0xFF81C784))
                }
            }
            if (episodes.isEmpty()) {
                item {
                    Text(
                        "暂无缓存",
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(episodes) { ep ->
                    CacheRow(ep) { onPlay(ep.guid) }
                }
                item {
                    Chip(
                        onClick = {
                            scope.launch {
                                val n = repo.clearAllCached()
                                msg = "已清空 $n 期"
                            }
                        },
                        label = { Text("清空缓存") }
                    )
                }
            }
            item {
                Chip(onClick = onBack, label = { Text("返回") })
            }
        }
    }
}

@Composable
private fun CacheRow(ep: EpisodeEntity, onClick: () -> Unit) {
    val size = (ep.audioLocalPath?.let { File(it).length() } ?: 0L) +
        (ep.subtitleLocalPath?.let { File(it).length() } ?: 0L)
    Chip(
        onClick = onClick,
        label = {
            Text(ep.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        secondaryLabel = {
            Text("${formatBytes(size)} · ${formatDuration(ep.durationSeconds)} · 点按播放")
        },
        icon = { Text("▶") }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
}
