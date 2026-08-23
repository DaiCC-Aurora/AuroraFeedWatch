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
    val transcript: String? = null,        // 纯文本备用字幕
    val subtitleVtt: String? = null,       // 云端带时间戳的 VTT 字幕（歌词式实时字幕的数据源）
    val downloadState: Int = DownloadStates.NOT_STARTED, // 0 未开始 1 下载中 2 已完成 3 失败
    val downloadProgress: Float = 0f       // 下载进度 0f~1f（仅下载中有意义）
)

/** 下载状态常量。 */
object DownloadStates {
    const val NOT_STARTED = 0
    const val DOWNLOADING = 1
    const val COMPLETED = 2
    const val FAILED = 3
}