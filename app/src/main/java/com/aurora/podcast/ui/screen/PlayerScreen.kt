package com.aurora.podcast.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.IconButton
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.aurora.podcast.ui.viewmodel.PlayerViewModel

/**
 * 播放页：标题 + 当前字幕行（随进度自动切换，即"高亮当前行"）+ 播放控制。
 * 返回按钮在左上（‹）。
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

    Scaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
                Text("‹ 返回")
            }
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
                    .height(90.dp)
                    .padding(horizontal = 8.dp)
            )
            Text(
                text = formatTime(positionMs),
                style = MaterialTheme.typography.caption2
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.skipPrevious() }) { Text("◀◀") }
                IconButton(onClick = { vm.toggle() }) { Text(if (isPlaying) "❚❚" else "▶") }
                IconButton(onClick = { vm.skipNext() }) { Text("▶▶") }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}