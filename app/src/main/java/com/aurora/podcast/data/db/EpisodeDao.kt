package com.aurora.podcast.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {

    @Query("SELECT * FROM episodes ORDER BY pubDate DESC")
    fun observeAll(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes ORDER BY pubDate DESC")
    suspend fun getAll(): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE isDownloaded = 1 ORDER BY pubDate DESC")
    fun observeDownloaded(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE isDownloaded = 1 ORDER BY pubDate DESC")
    suspend fun getDownloaded(): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE guid = :guid")
    suspend fun getByGuid(guid: String): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE guid IN (:guids)")
    suspend fun getByGuids(guids: List<String>): List<EpisodeEntity>

    @Upsert
    suspend fun upsertAll(episodes: List<EpisodeEntity>)

    @Upsert
    suspend fun upsert(episode: EpisodeEntity)

    @Query(
        "UPDATE episodes SET audioLocalPath = :audioLocalPath, " +
            "subtitleLocalPath = :subtitleLocalPath, isDownloaded = :isDownloaded " +
            "WHERE guid = :guid"
    )
    suspend fun updateDownloadState(
        guid: String,
        audioLocalPath: String?,
        subtitleLocalPath: String?,
        isDownloaded: Boolean
    )

    @Query("DELETE FROM episodes WHERE guid = :guid")
    suspend fun deleteByGuid(guid: String)

    /** 更新下载状态与进度（下载中/完成/失败共用）。 */
    @Query("UPDATE episodes SET downloadState = :state, downloadProgress = :progress WHERE guid = :guid")
    suspend fun updateDownloadProgress(guid: String, state: Int, progress: Float)
}