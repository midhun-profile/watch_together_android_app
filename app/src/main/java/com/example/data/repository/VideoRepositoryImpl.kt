package com.example.data.repository

import android.net.Uri
import com.example.data.local.database.SafFolderDao
import com.example.data.local.database.SafFolderEntity
import com.example.data.local.database.VideoDao
import com.example.data.local.media.MediaStoreDataSource
import com.example.data.local.media.SafDataSource
import com.example.domain.model.VideoItem
import com.example.domain.repository.SafFolder
import com.example.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VideoRepositoryImpl(
    private val videoDao: VideoDao,
    private val safFolderDao: SafFolderDao,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val safDataSource: SafDataSource
) : VideoRepository {

    override fun getAllVideos(): Flow<List<VideoItem>> {
        return videoDao.getAllVideos().map { list -> list.map { it.toDomainModel() } }
    }

    override fun getRecentlyPlayedVideos(): Flow<List<VideoItem>> {
        return videoDao.getRecentlyPlayedVideos().map { list -> list.map { it.toDomainModel() } }
    }

    override fun getContinueWatchingVideos(): Flow<List<VideoItem>> {
        return videoDao.getContinueWatchingVideos().map { list -> list.map { it.toDomainModel() } }
    }

    override fun getRecentlyAddedVideos(): Flow<List<VideoItem>> {
        return videoDao.getRecentlyAddedVideos().map { list -> list.map { it.toDomainModel() } }
    }

    override fun getFavoriteVideos(): Flow<List<VideoItem>> {
        return videoDao.getFavoriteVideos().map { list -> list.map { it.toDomainModel() } }
    }

    override fun getFolders(): Flow<List<String>> {
        return videoDao.getFolders()
    }

    override fun getVideosInFolder(folderName: String): Flow<List<VideoItem>> {
        return videoDao.getVideosInFolder(folderName).map { list -> list.map { it.toDomainModel() } }
    }

    override fun searchVideos(query: String): Flow<List<VideoItem>> {
        return videoDao.searchVideos(query).map { list -> list.map { it.toDomainModel() } }
    }

    override suspend fun getVideoByUri(uri: String): VideoItem? {
        return videoDao.getVideoByUri(uri)?.toDomainModel()
    }

    override suspend fun toggleFavorite(uri: String) {
        videoDao.toggleFavorite(uri)
    }

    override suspend fun deleteVideoEntry(uri: String) {
        videoDao.deleteByUri(uri)
    }

    override suspend fun syncMediaStore(): Int {
        val discovered = mediaStoreDataSource.queryVideos()
        if (discovered.isNotEmpty()) {
            videoDao.insertVideos(discovered)
        }
        return discovered.size
    }

    override suspend fun syncSafFolder(folderUri: Uri, folderName: String): Int {
        safDataSource.takePersistablePermission(folderUri)
        safFolderDao.insertFolder(
            SafFolderEntity(
                uri = folderUri.toString(),
                displayName = folderName,
                dateAdded = System.currentTimeMillis()
            )
        )
        val discovered = safDataSource.scanFolder(folderUri, folderName)
        if (discovered.isNotEmpty()) {
            videoDao.insertVideos(discovered)
        }
        return discovered.size
    }

    override fun getSafFolders(): Flow<List<SafFolder>> {
        return safFolderDao.getAllFolders().map { list ->
            list.map { SafFolder(it.uri, it.displayName, it.dateAdded) }
        }
    }

    override suspend fun removeSafFolder(folderUri: String) {
        safFolderDao.deleteFolder(folderUri)
    }
}
