package com.example.data.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.domain.player.FileDetails
import com.example.utils.Formatters

object MediaMetadataHelper {

    fun extractFileDetails(
        context: Context,
        uri: Uri,
        knownDurationMs: Long = 0L,
        audioCount: Int = 0,
        subtitleCount: Int = 0
    ): FileDetails {
        val fileName = Formatters.getFileName(context, uri)
        val fileSize = Formatters.getFileSize(context, uri)
        val ext = fileName.substringAfterLast('.', "").uppercase()
        val container = if (ext.isNotBlank()) ext else "Video Container"

        var durationMs = knownDurationMs
        var resolution: String? = null
        var frameRate: String? = null
        var videoCodec: String? = null
        var videoBitrate: String? = null
        var audioCodec: String? = null
        var audioChannels: String? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durationMs <= 0L && durStr != null) {
                durationMs = durStr.toLongOrNull() ?: 0L
            }

            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (width != null && height != null) {
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                resolution = if (rotation == "90" || rotation == "270") {
                    "${height} × ${width}"
                } else {
                    "${width} × ${height}"
                }
            }

            val captureFps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            if (captureFps != null) {
                frameRate = "$captureFps fps"
            }

            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            if (bitrate != null) {
                val bps = bitrate.toLongOrNull() ?: 0L
                if (bps > 0) {
                    videoBitrate = "${bps / 1000} kbps"
                }
            }

            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            if (mime != null) {
                videoCodec = mime
            }

            val numTracks = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS)
            // Audio info where available
            if (mime?.contains("audio") == true) {
                audioCodec = mime
            }
        } catch (_: Exception) {
            // Some exotic containers might not parse in native retriever; LibVLC will still play them
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        return FileDetails(
            fileName = fileName,
            uriString = uri.toString(),
            container = container,
            durationFormatted = Formatters.formatDuration(durationMs),
            fileSizeFormatted = Formatters.formatFileSize(fileSize),
            resolution = resolution,
            frameRate = frameRate,
            videoCodec = videoCodec,
            videoBitrate = videoBitrate,
            audioCodec = audioCodec,
            audioChannels = audioChannels,
            audioTrackCount = audioCount,
            subtitleTrackCount = subtitleCount
        )
    }
}
