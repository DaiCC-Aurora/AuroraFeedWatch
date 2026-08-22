package com.aurora.podcast.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.IconButton
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import com.aurora.podcast.ui.viewmodel.SettingsViewModel

/**
 * 设置页：
 *  - 保留最近期数（步进调节，默认 10）
 *  - 仅 Wi-Fi 下载开关
 *  - 立即清理缓存
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsState()
    val cleaning by vm.cleaning.collectAsState()
    val message by vm.message.collectAsState()

    Scaffold {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text("设置", style = MaterialTheme.typography.title3)
            }
            item {
                Text(
                    "离线保留最近期数",
                    style = MaterialTheme.typography.caption2
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { vm.setKeepEpisodes(settings.keepEpisodes - 1) }) {
                        Text("−")
                    }
                    Text("${settings.keepEpisodes}", style = MaterialTheme.typography.body1)
                    IconButton(onClick = { vm.setKeepEpisodes(settings.keepEpisodes + 1) }) {
                        Text("＋")
                    }
                }
            }
            item {
                ToggleChip(
                    checked = settings.wifiOnly,
                    onCheckedChange = { vm.setWifiOnly(it) },
                    label = { Text("仅 Wi-Fi 下载") }
                )
            }
            item {
                Chip(
                    onClick = { vm.cleanupNow() },
                    label = { Text(if (cleaning) "清理中…" else "立即清理缓存") }
                )
            }
            if (message != null) {
                item { Text(message!!, style = MaterialTheme.typography.caption2) }
            }
            item {
                Chip(onClick = onBack, label = { Text("返回") })
            }
        }
    }
}