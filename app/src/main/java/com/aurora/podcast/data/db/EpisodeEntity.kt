package com.aurora.podcast.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 节目实体，字段与规格第 2 节一致。
 */
@Entity(
    tableName = "episodes",
    indices = [Index(value = ["pubDate"])]
)
data class EpisodeEntity(
    @PrimaryKey val guid: String,          // 唯一标识
    val title: String,
    val pubDate: Long,                     // 时间戳（毫秒）
    val durationSeconds: Int,
    val audioLocalPath: String?,           // 本地音频文件路径
    val subtitleLocalPath: String?,        // 本地字幕文件路径
    val remoteAudioUrl: String,            // 云端音频 URL
    val remoteSubtitleUrl: String?,        // 云端字幕 URL
    val isDownloaded: Boolean = false,
    val transcript: String? = null         // 纯文本备用字幕
)