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
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.HorizontalDivider
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.IconButton // safe fallback: won't be used
import com.aurora.podcast.data.db.DownloadStates
import com.aurora.podcast.data.db.EpisodeEntity
import com.aurora.podcast.ui.viewmodel.EpisodesViewModel

/**
 * 节目列表页（重布局）：
 *  - 标题行：左"英语听力" + 右"已下载X/Y" + 刷新圈
 *  - 快捷入口行：CompactChip 三个（缓存/历史/设置），始终可见
 *  - 分割线 + 可选进度条
 *  - ScalingLazyColumn 节目列表
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

    Scaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp)
        ) {
            // ── 标题行 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("英语听力", style = MaterialTheme.typography.title3)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "已下载 ${episodes.count { it.isDownloaded }}/${episodes.size}",
                        style = MaterialTheme.typography.caption2,
                        color = Color(0xFF9E9E9E)
                    )
                    if (refreshing) {
                        androidx.wear.compose.material.CircularProgressIndicator(
                            modifier = androidx.compose.foundation.layout.width(18.dp)
                        )
                    }
                }
            }

            // ── 快捷入口行（始终在顶部可见！） ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, alignment = Alignment.CenterHorizontally)
            ) {
                CompactChip(onClick = onOpenCache, label = { Text("☰ 缓存") })
                CompactChip(onClick = onOpenHistory, label = { Text("🕘 历史") })
                CompactChip(onClick = onOpenSettings, label = { Text("⚙ 设置") })
            }

            // ── 分割线 ──
            HorizontalDivider()

            // ── 下载进度条 ──
            val downloading = episodes.filter { it.downloadState == DownloadStates.DOWNLOADING }
            if (downloading.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    if (downloading.size == 1) {
                        Text(downloading[0].title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.caption2)
                    } else {
                        Text("${downloading.size} 项下载中", style = MaterialTheme.typography.caption2)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF3A3A3A))
                    ) {
                        val fastest = downloading.maxOfOrNull { it.downloadProgress } ?: 0f
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fastest)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF81C784))
                        )
                    }
                }
            }

            // ── 消息提示 ──
            if (message != null) {
                Text(message!!, style = MaterialTheme.typography.caption2, color = Color(0xFF81C784))
            }

            // ── 节目列表 ──
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(episodes) { episode ->
                    EpisodeItem(
                        episode = episode,
                        onDownload = { vm.download(episode) },
                        onOpen = { onOpenPlayer(episode.guid) }
                    )
                }
                item {
                    // 底部留白，避免最后一个元素被截断
                }
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
    val icon = when (episode.downloadState) {
        DownloadStates.COMPLETED -> "▶"
        DownloadStates.DOWNLOADING -> "⏳"
        DownloadStates.FAILED -> "⚠"
        else -> "↓"
    }
    val secondary = when (episode.downloadState) {
        DownloadStates.COMPLETED -> "点按播放 · ${formatDuration(episode.durationSeconds)}"
        DownloadStates.DOWNLOADING -> "下载中 ${(episode.downloadProgress * 100).toInt()}%"
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
