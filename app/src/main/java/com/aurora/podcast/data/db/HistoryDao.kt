package com.aurora.podcast.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    /** 最近 limit 条历史，按首次播放时间倒序。 */
    @Query("SELECT * FROM history ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    /**
     * 插入或更新：guid 冲突时更新进度/时长/完成状态，
     * 但保留最早的 startedAt（首次播放时间）。
     */
    @Query(
        "INSERT INTO history(guid, title, startedAt, lastPlayedMs, totalMs, completed) " +
            "VALUES(:guid, :title, :startedAt, :lastPlayedMs, :totalMs, :completed) " +
            "ON CONFLICT(guid) DO UPDATE SET " +
            "title = excluded.title, " +
            "lastPlayedMs = excluded.lastPlayedMs, " +
            "totalMs = excluded.totalMs, " +
            "completed = excluded.completed"
    )
    suspend fun upsert(
        guid: String,
        title: String,
        startedAt: Long,
        lastPlayedMs: Long,
        totalMs: Long,
        completed: Boolean
    )

    @Query("DELETE FROM history")
    suspend fun deleteAll()
}
