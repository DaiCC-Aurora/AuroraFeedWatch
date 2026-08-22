package com.aurora.podcast.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.MaterialTheme

/**
 * 应用主题（Wear Material 默认配色即可）。
 */
@Composable
fun PodcastTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}