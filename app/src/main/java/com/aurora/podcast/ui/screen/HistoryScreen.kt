package com.aurora.podcast.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.aurora.podcast.data.db.HistoryEntity
import com.aurora.podcast.ui.viewmodel.HistoryViewModel

/**
 * 播放历史页：显示最近播放过的节目，点按再次播放；支持清空历史。
 */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onPlay: (String) -> Unit
) {
    val vm: HistoryViewModel = viewModel()
    val history by vm.history.collectAsState()
    val message by vm.message.collectAsState()

    Scaffold {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text("历史记录", style = MaterialTheme.typography.title3)
            }
            if (message != null) {
                item {
                    Text(
                        message!!,
                        style = MaterialTheme.typography.caption2,
                        color = Color(0xFF81C784)
                    )
                }
            }
            if (history.isEmpty()) {
                item {
                    Text(
                        "暂无播放历史",
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(history) { h ->
                    HistoryRow(h) { onPlay(h.guid) }
                }
                item {
                    Chip(onClick = { vm.clear() }, label = { Text("清除历史") })
                }
            }
            item {
                Chip(onClick = onBack, label = { Text("返回") })
            }
        }
    }
}

@Composable
private fun HistoryRow(h: HistoryEntity, onClick: () -> Unit) {
    val progress = when {
        h.completed -> "已播完"
        h.totalMs > 0L && h.lastPlayedMs > 0L -> "已播 ${h.lastPlayedMs * 100 / h.totalMs}%"
        h.lastPlayedMs > 0L -> "已播 ${formatMillisH(h.lastPlayedMs)}"
        else -> "刚开始"
    }
    Chip(
        onClick = onClick,
        label = {
            Text(h.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        secondaryLabel = {
            Text("${timeAgo(h.startedAt)} · $progress")
        },
        icon = { Text("▶") }
    )
}

/** 距离现在多久：刚刚 / X 分钟前 / X 小时前 / X 天前 / X 个月前。 */
private fun timeAgo(ms: Long): String {
    if (ms <= 0) return ""
    val diff = System.currentTimeMillis() - ms
    val seconds = diff / 1000
    return when {
        seconds < 60 -> "刚刚"
        seconds < 3600 -> "${seconds / 60} 分钟前"
        seconds < 86400 -> "${seconds / 3600} 小时前"
        seconds < 86400 * 30 -> "${seconds / 86400} 天前"
        else -> "${seconds / (86400 * 30)} 个月前"
    }
}

private fun formatMillisH(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
