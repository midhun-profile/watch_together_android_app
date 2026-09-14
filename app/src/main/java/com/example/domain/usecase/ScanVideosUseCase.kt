package com.example.domain.usecase

import android.net.Uri
import com.example.domain.repository.VideoRepository

class ScanVideosUseCase(private val repository: VideoRepository) {
    suspend fun scanMediaStore(): Int {
        return repository.syncMediaStore()
    }

    suspend fun scanSafFolder(folderUri: Uri, folderName: String): Int {
        return repository.syncSafFolder(folderUri, folderName)
    }
}
