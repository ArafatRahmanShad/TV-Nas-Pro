package com.example.downloader

import com.example.storage.StorageManager
import java.io.File

enum class DownloadStatus {
    QUEUED,
    CONNECTING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

data class DownloadSegment(
    val index: Int,
    val startByte: Long,
    val endByte: Long,
    var downloadedBytes: Long = 0L
) {
    val totalBytes: Long get() = (endByte - startByte + 1).coerceAtLeast(0L)
    val progress: Float get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

data class DownloadItem(
    val id: String,
    val url: String,
    val fileName: String,
    val destinationPath: String,
    var status: DownloadStatus = DownloadStatus.QUEUED,
    var totalBytes: Long = 0L,
    var downloadedBytes: Long = 0L,
    var speedBytesPerSec: Long = 0L,
    var segments: List<DownloadSegment> = emptyList(),
    val numConnections: Int = 4,
    val isMagnet: Boolean = false,
    val infoHash: String? = null,
    val trackers: List<String> = emptyList(),
    var errorMessage: String? = null,
    val addedTimestamp: Long = System.currentTimeMillis(),
    var completedTimestamp: Long? = null
) {
    val progress: Float
        get() = if (totalBytes > 0) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else if (status == DownloadStatus.COMPLETED) 1f else 0f

    val isSeeding: Boolean
        get() = isMagnet && status == DownloadStatus.COMPLETED

    val ratio: String
        get() = if (isSeeding) "1.42" else "0.0"

    val speedFormatted: String
        get() = StorageManager.formatSpeed(speedBytesPerSec)

    val downloadedFormatted: String
        get() = StorageManager.formatBytes(downloadedBytes)

    val totalFormatted: String
        get() = if (totalBytes > 0) StorageManager.formatBytes(totalBytes) else "Unknown Size"

    val etaFormatted: String
        get() {
            if (status != DownloadStatus.DOWNLOADING || speedBytesPerSec <= 0 || totalBytes <= 0) return "--"
            val remainingBytes = (totalBytes - downloadedBytes).coerceAtLeast(0L)
            val secondsRemaining = remainingBytes / speedBytesPerSec
            return when {
                secondsRemaining < 60 -> "${secondsRemaining}s"
                secondsRemaining < 3600 -> "${secondsRemaining / 60}m ${secondsRemaining % 60}s"
                else -> "${secondsRemaining / 3600}h ${(secondsRemaining % 3600) / 60}m"
            }
        }

    val targetFile: File
        get() = File(destinationPath, fileName)

    val isVideo: Boolean
        get() {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return ext in listOf("mp4", "mkv", "avi", "mov", "webm", "m4v", "ts")
        }

    val isAudio: Boolean
        get() {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return ext in listOf("mp3", "flac", "wav", "aac", "ogg", "m4a")
        }
}
