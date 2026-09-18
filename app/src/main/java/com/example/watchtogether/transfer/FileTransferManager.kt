package com.example.watchtogether.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import android.webkit.MimeTypeMap
import com.example.watchtogether.model.RoomRole
import com.example.watchtogether.signaling.SignalingClient
import com.example.watchtogether.signaling.SignalingMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

interface FileTransferListener {
    fun onTransferProgress(progress: Float, statusText: String)
    fun onFileReceived(localUri: Uri, fileName: String, mimeType: String)
    fun onError(error: String)
}

class FileTransferManager(
    private val context: Context,
    private val signalingClient: SignalingClient
) {
    companion object {
        private const val TAG_VIDEO = "VIDEO"
        private const val TAG_TRANSFER = "TRANSFER"
        private const val TAG_VIEWER = "VIEWER"
        private const val TAG_ERROR = "TRANSFER ERROR"
        private const val CHUNK_SIZE = 32 * 1024 // 32 KB chunks
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listener: FileTransferListener? = null

    // Host state
    private var hostLocalMediaUri: Uri? = null
    private var hostFileName: String = "movie.mp4"
    private var hostFileSize: Long = 0L
    private var hostMimeType: String = "video/mp4"
    private var hostDurationMs: Long = 0L
    private var activeRoomCode: String = ""
    private var activeRole: RoomRole = RoomRole.HOST
    private var sendingJob: Job? = null

    // Viewer receiving state
    private var receivingFile: File? = null
    private var receivingRaf: RandomAccessFile? = null
    private var expectedTotalChunks: Int = 0
    private var receivedChunkCount: Int = 0
    private var receivingFileName: String = ""
    private var receivingMimeType: String = "video/mp4"
    private var isTransferInProgress = false
    private val receivedChunks = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    fun setListener(listener: FileTransferListener?) {
        this.listener = listener
    }

    fun start(roomCode: String, role: RoomRole) {
        this.activeRoomCode = roomCode
        this.activeRole = role
        resetReceivingState()
    }

    fun stop() {
        sendingJob?.cancel()
        sendingJob = null
        resetReceivingState()
    }

    /**
     * Check if a locally cached copy of the shared video exists for this room.
     * Enables instantaneous, zero-latency playback when host and viewer run on the same device or emulator.
     */
    fun getCachedSharedFile(roomCode: String): File? {
        val roomKey = roomCode.trim().uppercase()
        val file = File(context.cacheDir, "shared_room_${roomKey}.mp4")
        return if (file.exists() && file.length() > 0L) file else null
    }

    /**
     * Host registers local media to share with viewer.
     */
    fun setHostLocalMedia(uri: Uri, name: String, durationMs: Long) {
        this.hostLocalMediaUri = uri
        this.hostDurationMs = durationMs
        this.hostFileName = queryFileName(uri, name)
        this.hostMimeType = queryMimeType(uri, hostFileName)
        this.hostFileSize = queryFileSize(uri)

        Log.d(TAG_VIDEO, "Host selected file: $hostFileName ($hostFileSize bytes, $hostMimeType)")

        // Cache local copy for guaranteed size, fast reading, and local emulator/device sharing
        scope.launch {
            try {
                val roomKey = activeRoomCode.ifEmpty { "DEFAULT" }.uppercase()
                val cacheFile = File(context.cacheDir, "shared_room_${roomKey}.mp4")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (cacheFile.exists() && cacheFile.length() > 0L) {
                    hostFileSize = cacheFile.length()
                    Log.d(TAG_VIDEO, "Cached local media to ${cacheFile.absolutePath}, size=$hostFileSize")
                }
            } catch (e: Exception) {
                Log.w(TAG_VIDEO, "Could not cache local file: ${e.message}")
            }
        }
    }

    fun getHostMediaMetadata(): Map<String, Any> {
        return mapOf(
            "name" to hostFileName,
            "durationMs" to hostDurationMs,
            "fileSize" to hostFileSize,
            "mimeType" to hostMimeType,
            "uri" to (hostLocalMediaUri?.toString() ?: "")
        )
    }

    /**
     * Host initiates chunked transfer to viewer.
     */
    fun startHostFileTransfer(roomCode: String) {
        val uri = hostLocalMediaUri
        if (uri == null) {
            Log.w(TAG_VIDEO, "Cannot start transfer: no local media set")
            return
        }

        val roomKey = roomCode.trim().uppercase()
        val cachedFile = File(context.cacheDir, "shared_room_${roomKey}.mp4")

        sendingJob?.cancel()
        sendingJob = scope.launch {
            try {
                Log.d(TAG_TRANSFER, "Starting file transfer for room $roomCode")
                withContext(Dispatchers.Main) {
                    listener?.onTransferProgress(0f, "Sending video to friend...")
                }

                val inputStream = if (cachedFile.exists() && cachedFile.length() > 0L) {
                    cachedFile.inputStream()
                } else {
                    context.contentResolver.openInputStream(uri)
                }

                if (inputStream == null) {
                    Log.e(TAG_ERROR, "Failed to open input stream for URI: $uri")
                    withContext(Dispatchers.Main) {
                        listener?.onError("Failed to open video file")
                    }
                    return@launch
                }

                inputStream.use { stream ->
                    val actualSize = if (cachedFile.exists() && cachedFile.length() > 0L) {
                        cachedFile.length()
                    } else if (hostFileSize > 0L) {
                        hostFileSize
                    } else {
                        stream.available().toLong().coerceAtLeast(0L)
                    }

                    val totalChunks = if (actualSize > 0L) {
                        Math.ceil(actualSize.toDouble() / CHUNK_SIZE).toInt().coerceAtLeast(1)
                    } else {
                        1
                    }

                    // 1. Send transfer start announcement
                    val startMsg = SignalingMessage.createFileTransferStart(
                        roomCode = roomCode,
                        fileName = hostFileName,
                        fileSize = actualSize,
                        mimeType = hostMimeType,
                        totalChunks = totalChunks,
                        chunkSize = CHUNK_SIZE
                    )
                    signalingClient.send(startMsg)

                    // 2. Read and stream chunks sequentially
                    val buffer = ByteArray(CHUNK_SIZE)
                    var chunkIndex = 0

                    while (isActive) {
                        val bytesRead = stream.read(buffer)
                        if (bytesRead == -1) break

                        val chunkData = if (bytesRead == CHUNK_SIZE) buffer else buffer.copyOf(bytesRead)
                        val base64Data = Base64.encodeToString(chunkData, Base64.NO_WRAP)

                        val chunkMsg = SignalingMessage.createFileTransferChunk(
                            roomCode = roomCode,
                            chunkIndex = chunkIndex,
                            totalChunks = totalChunks,
                            dataBase64 = base64Data
                        )
                        signalingClient.send(chunkMsg)

                        chunkIndex++
                        val progress = if (totalChunks > 0) {
                            (chunkIndex.toFloat() / totalChunks).coerceIn(0f, 1f)
                        } else {
                            0.5f
                        }
                        withContext(Dispatchers.Main) {
                            listener?.onTransferProgress(progress, "Sharing video... ${(progress * 100).toInt()}%")
                        }

                        // Flow control delay to avoid overwhelming socket buffers
                        delay(15)
                    }

                    // 3. Send transfer complete announcement
                    val completeMsg = SignalingMessage.createFileTransferComplete(
                        roomCode = roomCode,
                        fileName = hostFileName,
                        totalChunks = chunkIndex
                    )
                    signalingClient.send(completeMsg)
                    Log.d(TAG_TRANSFER, "Transfer completed: sent $chunkIndex chunks")

                    withContext(Dispatchers.Main) {
                        listener?.onTransferProgress(1f, "Video shared with viewer")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG_ERROR, "Error during file transfer: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    listener?.onError("Transfer interrupted: ${e.message}")
                }
            }
        }
    }

    /**
     * Handle incoming file transfer messages (Viewer or Host).
     */
    fun handleSignalingMessage(message: SignalingMessage) {
        when (message.type) {
            SignalingMessage.TYPE_REQUEST_FILE -> {
                if (activeRole == RoomRole.HOST && hostLocalMediaUri != null) {
                    Log.d(TAG_TRANSFER, "[HOST] Viewer requested video file. Starting transfer...")
                    startHostFileTransfer(activeRoomCode)
                }
            }

            SignalingMessage.TYPE_FILE_TRANSFER_START -> {
                if (activeRole == RoomRole.VIEWER) {
                    handleTransferStart(message)
                }
            }

            SignalingMessage.TYPE_FILE_TRANSFER_CHUNK -> {
                if (activeRole == RoomRole.VIEWER) {
                    handleTransferChunk(message)
                }
            }

            SignalingMessage.TYPE_FILE_TRANSFER_COMPLETE -> {
                if (activeRole == RoomRole.VIEWER) {
                    handleTransferComplete(message)
                }
            }
        }
    }

    private fun handleTransferStart(message: SignalingMessage) {
        resetReceivingState()
        val payload = message.payload
        receivingFileName = payload.optString("fileName", "movie.mp4")
        val fileSize = payload.optLong("fileSize", 0L)
        receivingMimeType = payload.optString("mimeType", "video/mp4")
        expectedTotalChunks = payload.optInt("totalChunks", 1)
        receivedChunks.clear()

        Log.d(TAG_TRANSFER, "Starting transfer")
        Log.d(TAG_VIEWER, "Receiving file: $receivingFileName ($fileSize bytes, $expectedTotalChunks chunks, $receivingMimeType)")

        try {
            val ext = getExtensionForMimeType(receivingMimeType, receivingFileName)
            val tempFile = File(context.cacheDir, "shared_${System.currentTimeMillis()}.$ext")
            receivingFile = tempFile
            receivingRaf = RandomAccessFile(tempFile, "rw")
            if (fileSize > 0L) {
                receivingRaf?.setLength(fileSize)
            }
            isTransferInProgress = true

            scope.launch(Dispatchers.Main) {
                listener?.onTransferProgress(0f, "Receiving video from host...")
            }
        } catch (e: Exception) {
            Log.e(TAG_ERROR, "Failed to create destination file: ${e.message}")
            scope.launch(Dispatchers.Main) {
                listener?.onError("Failed to create destination file: ${e.message}")
            }
        }
    }

    private fun handleTransferChunk(message: SignalingMessage) {
        if (!isTransferInProgress || receivingRaf == null) return

        val payload = message.payload
        val chunkIndex = payload.optInt("chunkIndex", 0)
        val totalChunks = payload.optInt("totalChunks", expectedTotalChunks)
        if (totalChunks > expectedTotalChunks) {
            expectedTotalChunks = totalChunks
        }
        val base64Data = payload.optString("data", "")
        if (base64Data.isEmpty()) return

        try {
            val bytes = Base64.decode(base64Data, Base64.NO_WRAP)
            val raf = receivingRaf ?: return

            synchronized(raf) {
                val offset = chunkIndex.toLong() * CHUNK_SIZE
                raf.seek(offset)
                raf.write(bytes)
            }

            receivedChunks.add(chunkIndex)
            receivedChunkCount = receivedChunks.size

            val progress = (receivedChunkCount.toFloat() / expectedTotalChunks.coerceAtLeast(1)).coerceIn(0f, 1f)
            scope.launch(Dispatchers.Main) {
                listener?.onTransferProgress(progress, "Receiving video... ${(progress * 100).toInt()}%")
            }
        } catch (e: Exception) {
            Log.e(TAG_ERROR, "Error writing chunk $chunkIndex: ${e.message}")
        }
    }

    private fun handleTransferComplete(message: SignalingMessage) {
        if (!isTransferInProgress || receivingFile == null) return
        val totalReported = message.payload.optInt("totalChunks", expectedTotalChunks)
        if (totalReported > 0) {
            expectedTotalChunks = totalReported
        }

        isTransferInProgress = false

        try {
            val raf = receivingRaf
            if (raf != null) {
                try {
                    raf.fd.sync()
                } catch (_: Exception) {}
                raf.close()
            }
            receivingRaf = null

            val file = receivingFile ?: return
            if (!file.exists() || file.length() == 0L) {
                Log.e(TAG_ERROR, "Received file is empty")
                scope.launch(Dispatchers.Main) {
                    listener?.onError("Received file is empty")
                }
                return
            }

            // Cache for room so future joins/reconnects don't re-transfer
            try {
                val roomKey = activeRoomCode.ifEmpty { "DEFAULT" }.uppercase()
                val cachedTarget = File(context.cacheDir, "shared_room_${roomKey}.mp4")
                file.copyTo(cachedTarget, overwrite = true)
            } catch (_: Exception) {}

            val localUri = Uri.fromFile(file)
            Log.d(TAG_VIEWER, "Setting video source: $localUri (size=${file.length()}, chunks=${receivedChunks.size}/$expectedTotalChunks)")

            scope.launch(Dispatchers.Main) {
                listener?.onTransferProgress(1f, "Video ready")
                listener?.onFileReceived(localUri, receivingFileName, receivingMimeType)
            }
        } catch (e: Exception) {
            Log.e(TAG_ERROR, "Failed finalizing received file: ${e.message}")
            scope.launch(Dispatchers.Main) {
                listener?.onError("Failed to reconstruct video: ${e.message}")
            }
        }
    }

    private fun resetReceivingState() {
        isTransferInProgress = false
        try {
            receivingRaf?.close()
        } catch (_: Exception) {}
        receivingRaf = null
        receivingFile = null
        receivedChunkCount = 0
        expectedTotalChunks = 0
        receivedChunks.clear()
    }

    private fun queryFileName(uri: Uri, defaultName: String): String {
        return try {
            val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) return it.getString(idx)
                }
            }
            uri.lastPathSegment ?: defaultName
        } catch (e: Exception) {
            uri.lastPathSegment ?: defaultName
        }
    }

    private fun queryFileSize(uri: Uri): Long {
        return try {
            val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.SIZE)
                    if (idx != -1) return it.getLong(idx)
                }
            }
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun queryMimeType(uri: Uri, fileName: String): String {
        val type = context.contentResolver.getType(uri)
        if (!type.isNullOrEmpty()) return type

        val ext = fileName.substringAfterLast('.', "")
        if (ext.isNotEmpty()) {
            val guessed = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
            if (!guessed.isNullOrEmpty()) return guessed
        }
        return "video/mp4"
    }

    private fun getExtensionForMimeType(mimeType: String, fileName: String): String {
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        if (!ext.isNullOrEmpty()) return ext

        val fileExt = fileName.substringAfterLast('.', "")
        if (fileExt.isNotEmpty() && fileExt.length in 2..5) {
            return fileExt
        }
        return "mp4"
    }
}
