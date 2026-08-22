package com.aurora.podcast.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.aurora.podcast.ui.viewmodel.PlayerViewModel

/**
 * 播放页：标题 + 当前字幕行（随进度自动切换，即"高亮当前行"）
 * + 可拖动跳转的进度条 + 播放控制。
 * 返回按钮在顶部（CompactChip），底部为 上一首 / 播放暂停 / 下一首 圆形按钮。
 */
@Composable
fun PlayerScreen(
    initialGuid: String?,
    onBack: () -> Unit
) {
    val vm: PlayerViewModel = viewModel()

    LaunchedEffect(initialGuid) {
        if (initialGuid != null) vm.load(initialGuid)
    }

    val episode by vm.currentEpisode.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val cue by vm.currentCue.collectAsState()
    val positionMs by vm.positionMs.collectAsState()

    val durationMs = (episode?.durationSeconds ?: 0).coerceAtLeast(0) * 1000L

    // 拖动进度条时进度条跟随手指位置，松手后恢复播放实际进度
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val displayFraction = dragFraction ?: if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else 0f

    Scaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CompactChip(
                onClick = onBack,
                modifier = Modifier.align(Alignment.Start),
                label = { Text("‹ 返回") }
            )
            Text(
                text = episode?.title ?: "选择一期节目",
                style = MaterialTheme.typography.title3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            )
            // 字幕区域：当前高亮行
            Text(
                text = cue?.text ?: "（暂无字幕）",
                style = MaterialTheme.typography.body1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 8.dp)
            )
            Text(
                text = if (durationMs > 0) {
                    "${formatTime(positionMs)} / ${formatTime(durationMs)}"
                } else {
                    formatTime(positionMs)
                },
                style = MaterialTheme.typography.caption2
            )
            // 可拖动跳转的进度条（时长未知时不显示）
            if (durationMs > 0) {
                SeekBar(
                    progress = displayFraction,
                    onSeekStart = { frac ->
                        dragFraction = frac
                        vm.seekTo((frac * durationMs).toLong())
                    },
                    onSeek = { frac ->
                        vm.seekTo((frac * durationMs).toLong())
                    },
                    onSeekEnd = { dragFraction = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 4.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { vm.skipPrevious() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Text("◀◀")
                }
                Button(
                    onClick = { vm.toggle() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Text(if (isPlaying) "❚❚" else "▶")
                }
                Button(
                    onClick = { vm.skipNext() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Text("▶▶")
                }
            }
        }
    }
}

/**
 * 可拖动跳转的播放进度条：点按或左右拖动即可跳转播放位置。
 * 使用标准 Compose foundation API（Box + drag 手势）实现，不依赖额外组件。
 */
@Composable
private fun SeekBar(
    progress: Float,
    onSeekStart: (Float) -> Unit,
    onSeek: (Float) -> Unit,
    onSeekEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onSeekStart((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                    },
                    onDragEnd = { onSeekEnd() },
                    onDragCancel = { onSeekEnd() },
                    onDrag = { change, _ ->
                        change.consume()
                        onSeek((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                )
            }
    ) {
        // 轨道（灰色）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .align(Alignment.CenterVertically)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF3A3A3A))
        )
        // 已播放部分（绿色）
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(6.dp)
                .align(Alignment.CenterVertically)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF81C784))
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
