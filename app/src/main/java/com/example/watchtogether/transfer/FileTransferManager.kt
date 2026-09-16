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
     * Host registers local media to share with viewer.
     */
    fun setHostLocalMedia(uri: Uri, name: String, durationMs: Long) {
        this.hostLocalMediaUri = uri
        this.hostDurationMs = durationMs
        this.hostFileName = queryFileName(uri, name)
        this.hostFileSize = queryFileSize(uri)
        this.hostMimeType = queryMimeType(uri, hostFileName)

        Log.d(TAG_VIDEO, "Host selected file")
        Log.d(TAG_VIDEO, "File name: $hostFileName")
        Log.d(TAG_VIDEO, "File size: $hostFileSize bytes")
        Log.d(TAG_VIDEO, "MIME type: $hostMimeType")
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

        sendingJob?.cancel()
        sendingJob = scope.launch {
            try {
                Log.d(TAG_TRANSFER, "Starting transfer")
                listener?.onTransferProgress(0f, "Sending video to friend...")

                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    Log.e(TAG_ERROR, "Failed to open input stream for URI: $uri")
                    listener?.onError("Failed to open video file")
                    return@launch
                }

                inputStream.use { stream ->
                    val totalChunks = if (hostFileSize > 0) {
                        Math.ceil(hostFileSize.toDouble() / CHUNK_SIZE).toInt().coerceAtLeast(1)
                    } else {
                        1
                    }

                    // 1. Send transfer start announcement
                    val startMsg = SignalingMessage.createFileTransferStart(
                        roomCode = roomCode,
                        fileName = hostFileName,
                        fileSize = hostFileSize,
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
                        Log.d(TAG_TRANSFER, "Chunk sent: $chunkIndex / $totalChunks")

                        chunkIndex++
                        val progress = (chunkIndex.toFloat() / totalChunks).coerceIn(0f, 1f)
                        withContext(Dispatchers.Main) {
                            listener?.onTransferProgress(progress, "Sharing video... ${(progress * 100).toInt()}%")
                        }

                        // Gentle yield for network framing flow control
                        delay(10)
                    }

                    // 3. Send transfer complete announcement
                    val completeMsg = SignalingMessage.createFileTransferComplete(
                        roomCode = roomCode,
                        fileName = hostFileName,
                        totalChunks = chunkIndex
                    )
                    signalingClient.send(completeMsg)
                    Log.d(TAG_TRANSFER, "Transfer completed")

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

        Log.d(TAG_TRANSFER, "Starting transfer")
        Log.d(TAG_VIEWER, "Receiving file: $receivingFileName ($fileSize bytes, $expectedTotalChunks chunks, $receivingMimeType)")

        try {
            val ext = getExtensionForMimeType(receivingMimeType, receivingFileName)
            val tempFile = File(context.cacheDir, "shared_${System.currentTimeMillis()}.$ext")
            receivingFile = tempFile
            receivingRaf = RandomAccessFile(tempFile, "rw")
            if (fileSize > 0) {
                receivingRaf?.setLength(fileSize)
            }
            isTransferInProgress = true

            scope.launch(Dispatchers.Main) {
                listener?.onTransferProgress(0f, "Receiving video from host...")
            }
        } catch (e: Exception) {
            Log.e(TAG_ERROR, "Failed to create destination file: ${e.message}")
            listener?.onError("Failed to create destination file: ${e.message}")
        }
    }

    private fun handleTransferChunk(message: SignalingMessage) {
        if (!isTransferInProgress || receivingRaf == null) return

        val payload = message.payload
        val chunkIndex = payload.optInt("chunkIndex", 0)
        val totalChunks = payload.optInt("totalChunks", expectedTotalChunks)
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

            receivedChunkCount++
            Log.d(TAG_TRANSFER, "Chunk received: $chunkIndex / $totalChunks")

            val progress = (receivedChunkCount.toFloat() / totalChunks.coerceAtLeast(1)).coerceIn(0f, 1f)
            scope.launch(Dispatchers.Main) {
                listener?.onTransferProgress(progress, "Receiving video... ${(progress * 100).toInt()}%")
            }
        } catch (e: Exception) {
            Log.e(TAG_ERROR, "Error writing chunk $chunkIndex: ${e.message}")
        }
    }

    private fun handleTransferComplete(message: SignalingMessage) {
        if (!isTransferInProgress || receivingFile == null) return
        isTransferInProgress = false

        try {
            receivingRaf?.close()
            receivingRaf = null

            val file = receivingFile ?: return
            Log.d(TAG_TRANSFER, "Transfer completed")
            Log.d(TAG_VIEWER, "Reconstructing video")
            Log.d(TAG_VIEWER, "Blob created")

            val localUri = Uri.fromFile(file)
            Log.d(TAG_VIEWER, "Object URL created: $localUri")
            Log.d(TAG_VIEWER, "Setting video source")

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
