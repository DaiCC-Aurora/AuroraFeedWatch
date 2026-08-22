package com.aurora.podcast.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 播放历史：每期一条记录（guid 唯一），每次播放更新进度。
 * startedAt 为第一次播放时间；lastPlayedMs 为最近一次播放到的进度；
 * totalMs 为记录时节目时长（毫秒，0 表示未知）。
 */
@Entity(
    tableName = "history",
    indices = [Index(value = ["guid"], unique = true)]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val guid: String,
    val title: String,
    val startedAt: Long,       // 首次播放时间（毫秒）
    val lastPlayedMs: Long,    // 最近播放进度（毫秒）
    val totalMs: Long,         // 节目时长（毫秒，0=未知）
    val completed: Boolean     // 是否已完整播放
)
