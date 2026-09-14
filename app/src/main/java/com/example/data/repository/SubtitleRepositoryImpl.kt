package com.example.data.repository

import com.example.data.local.database.SubtitleDao
import com.example.data.local.database.SubtitleEntity
import com.example.domain.model.SubtitleTrack
import com.example.domain.repository.SubtitleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SubtitleRepositoryImpl(
    private val subtitleDao: SubtitleDao
) : SubtitleRepository {

    override fun getSubtitlesForVideo(videoUri: String): Flow<List<SubtitleTrack>> {
        return subtitleDao.getSubtitlesForVideo(videoUri).map { list ->
            list.map { entity ->
                SubtitleTrack(
                    id = "ext_${entity.id}",
                    label = entity.displayName,
                    language = entity.language,
                    isSelected = false,
                    uri = entity.uri,
                    isExternal = true
                )
            }
        }
    }

    override suspend fun addExternalSubtitle(
        videoUri: String,
        fileUri: String,
        displayName: String,
        language: String?
    ) {
        subtitleDao.insertSubtitle(
            SubtitleEntity(
                videoUri = videoUri,
                uri = fileUri,
                displayName = displayName,
                language = language,
                isExternal = true
            )
        )
    }

    override suspend fun removeExternalSubtitle(id: Long) {
        subtitleDao.deleteSubtitle(id)
    }
}
