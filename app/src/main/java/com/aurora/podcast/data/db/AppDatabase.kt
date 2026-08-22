package com.aurora.podcast.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [EpisodeEntity::class, HistoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun episodeDao(): EpisodeDao

    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "podcast.db"
                )
                    // v2 变更：episodes 增加下载进度字段 + 新增 history 表。
                    // 本地数据均可从云端重新拉取，旧库直接重建最稳妥。
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
        }
    }
}