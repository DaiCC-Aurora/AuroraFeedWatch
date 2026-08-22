package com.aurora.podcast.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.aurora.podcast.data.db.DownloadStates
import com.aurora.podcast.data.db.EpisodeEntity
import com.aurora.podcast.ui.viewmodel.EpisodesViewModel

/**
 * 节目列表页：
 *  - 显示云端节目（已下载/下载中/失败/未下载）
 *  - 下载中的节目顶部显示进度条
 *  - 点击已下载 → 进入播放页；点击未下载/失败 → 加入下载队列或重试
 *  - 底部 3 个快捷入口：缓存列表 / 历史记录 / 设置
 */
@Composable
fun EpisodesScreen(
    onOpenPlayer: (String) -> Unit,
    onOpenCache: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val vm: EpisodesViewModel = viewModel()
    val episodes by vm.episodes.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val message by vm.message.collectAsState()

    val downloading = episodes.filter { it.downloadState == DownloadStates.DOWNLOADING }

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

            // 下载进度条区域
            if (downloading.isNotEmpty()) {
                items(downloading) { ep ->
                    DownloadProgressRow(ep)
                }
            }

            if (message != null) {
                item {
                    Text(
                        message!!,
                        style = MaterialTheme.typography.caption2,
                        color = Color(0xFF81C784),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                    )
                }
            }

            items(episodes) { episode ->
                EpisodeItem(
                    episode = episode,
                    onDownload = { vm.download(episode) },
                    onOpen = { onOpenPlayer(episode.guid) }
                )
            }

            // 底部导航
            item {
                Chip(onClick = onOpenCache, label = { Text("☰ 缓存列表") })
            }
            item {
                Chip(onClick = onOpenHistory, label = { Text("🕘 历史记录") })
            }
            item {
                Chip(onClick = onOpenSettings, label = { Text("⚙ 设置") })
            }
        }
    }
}

/** 下载中单期进度行：自定义 Box 进度条，避免依赖不确定的 LinearProgressIndicator API。 */
@Composable
private fun DownloadProgressRow(ep: EpisodeEntity) {
    val pct = (ep.downloadProgress * 100).toInt()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Text(
            ep.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.caption2
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF3A3A3A))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(ep.downloadProgress)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF81C784))
            )
        }
        Text(
            "下载中 $pct% · ${formatDuration(ep.durationSeconds)}",
            style = MaterialTheme.typography.caption3,
            color = Color(0xFF81C784)
        )
    }
}

@Composable
private fun EpisodeItem(
    episode: EpisodeEntity,
    onDownload: () -> Unit,
    onOpen: () -> Unit
) {
    val icon = when (episode.downloadState) {
        DownloadStates.COMPLETED -> "▶"
        DownloadStates.DOWNLOADING -> "⏳"
        DownloadStates.FAILED -> "⚠"
        else -> "↓"
    }
    val secondary = when (episode.downloadState) {
        DownloadStates.COMPLETED -> "点按播放 · ${formatDuration(episode.durationSeconds)}"
        DownloadStates.DOWNLOADING -> "下载中 ${(episode.downloadProgress * 100).toInt()}% · ${formatDuration(episode.durationSeconds)}"
        DownloadStates.FAILED -> "下载失败 · 点按重试"
        else -> "点按下载 · ${formatDuration(episode.durationSeconds)}"
    }
    Chip(
        onClick = {
            if (episode.downloadState == DownloadStates.COMPLETED) onOpen() else onDownload()
        },
        label = {
            Text(
                episode.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = { Text(secondary) },
        icon = { Text(icon) }
    )
}

fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
