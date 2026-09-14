package com.example.domain.repository

import android.net.Uri
import com.example.domain.model.VideoItem
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    fun getAllVideos(): Flow<List<VideoItem>>
    fun getRecentlyPlayedVideos(): Flow<List<VideoItem>>
    fun getContinueWatchingVideos(): Flow<List<VideoItem>>
    fun getRecentlyAddedVideos(): Flow<List<VideoItem>>
    fun getFavoriteVideos(): Flow<List<VideoItem>>
    fun getFolders(): Flow<List<String>>
    fun getVideosInFolder(folderName: String): Flow<List<VideoItem>>
    fun searchVideos(query: String): Flow<List<VideoItem>>
    suspend fun getVideoByUri(uri: String): VideoItem?
    suspend fun toggleFavorite(uri: String)
    suspend fun deleteVideoEntry(uri: String)
    suspend fun syncMediaStore(): Int
    suspend fun syncSafFolder(folderUri: Uri, folderName: String): Int
    fun getSafFolders(): Flow<List<SafFolder>>
    suspend fun removeSafFolder(folderUri: String)
}

data class SafFolder(
    val uri: String,
    val displayName: String,
    val dateAdded: Long = System.currentTimeMillis()
)
