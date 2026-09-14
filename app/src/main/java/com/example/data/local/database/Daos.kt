package com.example.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {

    @Query("SELECT * FROM videos ORDER BY displayName COLLATE NOCASE ASC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT 30")
    fun getRecentlyPlayedVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE lastPosition > 5000 AND completed = 0 ORDER BY lastPlayedAt DESC LIMIT 20")
    fun getContinueWatchingVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos ORDER BY dateAdded DESC LIMIT 30")
    fun getRecentlyAddedVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE isFavorite = 1 ORDER BY displayName COLLATE NOCASE ASC")
    fun getFavoriteVideos(): Flow<List<VideoEntity>>

    @Query("SELECT DISTINCT folderName FROM videos WHERE folderName IS NOT NULL AND folderName != '' ORDER BY folderName ASC")
    fun getFolders(): Flow<List<String>>

    @Query("SELECT * FROM videos WHERE folderName = :folderName ORDER BY displayName COLLATE NOCASE ASC")
    fun getVideosInFolder(folderName: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE displayName LIKE '%' || :query || '%' OR folderName LIKE '%' || :query || '%' ORDER BY displayName COLLATE NOCASE ASC")
    fun searchVideos(query: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE uri = :uri LIMIT 1")
    suspend fun getVideoByUri(uri: String): VideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVideos(videos: List<VideoEntity>): List<Long>

    @Update
    suspend fun updateVideo(video: VideoEntity)

    @Query("UPDATE videos SET isFavorite = CASE WHEN isFavorite = 1 THEN 0 ELSE 1 END WHERE uri = :uri")
    suspend fun toggleFavorite(uri: String)

    @Query("UPDATE videos SET lastPosition = :position, duration = :duration, completed = :completed, lastPlayedAt = :timestamp, playCount = playCount + 1 WHERE uri = :uri")
    suspend fun updatePlaybackPosition(uri: String, position: Long, duration: Long, completed: Boolean, timestamp: Long)

    @Query("DELETE FROM videos WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("SELECT uri FROM videos")
    suspend fun getAllUris(): List<String>
}

@Dao
interface PlaybackDao {
    @Query("SELECT * FROM playback_history WHERE videoUri = :videoUri LIMIT 1")
    fun getPlayback(videoUri: String): Flow<PlaybackEntity?>

    @Query("SELECT * FROM playback_history WHERE videoUri = :videoUri LIMIT 1")
    suspend fun getPlaybackSync(videoUri: String): PlaybackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlayback(entity: PlaybackEntity)

    @Query("DELETE FROM playback_history WHERE videoUri = :videoUri")
    suspend fun deletePlayback(videoUri: String)

    @Query("DELETE FROM playback_history")
    suspend fun clearAllHistory()
}

@Dao
interface SubtitleDao {
    @Query("SELECT * FROM subtitles WHERE videoUri = :videoUri")
    fun getSubtitlesForVideo(videoUri: String): Flow<List<SubtitleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtitle(entity: SubtitleEntity): Long

    @Query("DELETE FROM subtitles WHERE id = :id")
    suspend fun deleteSubtitle(id: Long)
}

@Dao
interface SafFolderDao {
    @Query("SELECT * FROM saf_folders ORDER BY dateAdded DESC")
    fun getAllFolders(): Flow<List<SafFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(entity: SafFolderEntity)

    @Query("DELETE FROM saf_folders WHERE uri = :uri")
    suspend fun deleteFolder(uri: String)
}
