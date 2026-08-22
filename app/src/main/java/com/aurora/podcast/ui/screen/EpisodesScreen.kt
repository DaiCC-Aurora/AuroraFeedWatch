package com.aurora.podcast.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.aurora.podcast.data.db.EpisodeEntity
import com.aurora.podcast.ui.viewmodel.EpisodesViewModel

/**
 * 节目列表页：
 *  - 显示云端节目（已下载/未下载）
 *  - 点击已下载 -> 进入播放页；点击未下载 -> 加入下载队列
 */
@Composable
fun EpisodesScreen(
    onOpenPlayer: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val vm: EpisodesViewModel = viewModel()
    val episodes by vm.episodes.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val message by vm.message.collectAsState()

    Scaffold {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("英语听力", style = MaterialTheme.typography.title3)
                    if (refreshing) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 2.dp))
                    }
                }
            }
            item {
                Text(
                    "已下载 ${episodes.count { it.isDownloaded }} / ${episodes.size} 期",
                    style = MaterialTheme.typography.caption2,
                    color = Color(0xFF9E9E9E)
                )
            }
            if (message != null) {
                item {
                    Text(message!!, style = MaterialTheme.typography.caption2, color = Color(0xFF81C784))
                }
            }
            items(episodes) { episode ->
                EpisodeItem(
                    episode = episode,
                    onDownload = { vm.download(episode) },
                    onOpen = { onOpenPlayer(episode.guid) }
                )
            }
            item {
                Chip(onClick = onOpenSettings, label = { Text("⚙ 设置") })
            }
        }
    }
}

@Composable
private fun EpisodeItem(
    episode: EpisodeEntity,
    onDownload: () -> Unit,
    onOpen: () -> Unit
) {
    Chip(
        onClick = { if (episode.isDownloaded) onOpen() else onDownload() },
        label = {
            Text(
                episode.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            Text(
                "${if (episode.isDownloaded) "已下载" else "点击下载"} · ${formatDuration(episode.durationSeconds)}"
            )
        },
        icon = {
            Text(if (episode.isDownloaded) "✓" else "↓")
        }
    )
}

fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}