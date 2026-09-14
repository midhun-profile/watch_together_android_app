package com.example.data.media

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RecentMovieEntity::class], version = 1, exportSchema = false)
abstract class MediaDatabase : RoomDatabase() {

    abstract fun recentMovieDao(): RecentMovieDao

    companion object {
        @Volatile
        private var instance: MediaDatabase? = null

        fun getInstance(context: Context): MediaDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MediaDatabase::class.java,
                    "watchtogether_media.db"
                ).build().also { instance = it }
            }
        }
    }
}
