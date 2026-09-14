package com.example.data.local.media

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import com.example.data.local.database.VideoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SafDataSource(private val context: Context) {

    fun takePersistablePermission(uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun scanFolder(treeUri: Uri, folderName: String): List<VideoEntity> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoEntity>()
        val contentResolver: ContentResolver = context.contentResolver

        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            val cursor: Cursor? = contentResolver.query(
                childrenUri,
                projection,
                null,
                null,
                null
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                val videoExtensions = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "ts", "3gp", "flv", "wmv", "mpg", "mpeg")

                while (c.moveToNext()) {
                    val childId = c.getString(idCol)
                    val name = c.getString(nameCol) ?: "Video"
                    val mime = c.getString(mimeCol) ?: "video/*"
                    val size = c.getLong(sizeCol)
                    val lastModified = c.getLong(modCol)

                    val isVideoMime = mime.startsWith("video/")
                    val ext = name.substringAfterLast('.', "").lowercase()
                    val isVideoExt = videoExtensions.contains(ext)

                    if (isVideoMime || isVideoExt) {
                        val fileDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        results.add(
                            VideoEntity(
                                uri = fileDocUri.toString(),
                                displayName = name,
                                mimeType = mime,
                                size = size,
                                duration = 0L,
                                dateAdded = if (lastModified > 0) lastModified else System.currentTimeMillis(),
                                dateModified = lastModified,
                                folderName = folderName,
                                width = 0,
                                height = 0
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        results
    }
}
