package com.aurora.podcast.data.model

/**
 * 一条字幕：startMillis/endMillis 为相对于音频开头的时间（毫秒）。
 */
data class SubtitleCue(
    val startMillis: Long,
    val endMillis: Long,
    val text: String
)