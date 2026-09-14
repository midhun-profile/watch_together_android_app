package com.example.domain.repository

import com.example.domain.model.SubtitleTrack
import kotlinx.coroutines.flow.Flow

interface SubtitleRepository {
    fun getSubtitlesForVideo(videoUri: String): Flow<List<SubtitleTrack>>
    suspend fun addExternalSubtitle(
        videoUri: String,
        fileUri: String,
        displayName: String,
        language: String? = null
    )
    suspend fun removeExternalSubtitle(id: Long)
}
