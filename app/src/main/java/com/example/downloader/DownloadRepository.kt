package com.example.downloader

import android.content.Context
import android.webkit.URLUtil
import com.example.storage.StorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

object DownloadRepository {
    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val engine = IdmEngine { updatedItem ->
        updateItem(updatedItem)
    }

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val downloadPath = StorageManager.getDownloadPath(context)

        // Seed with sample downloads so first-time users can immediately test TV NAS playback and IDM transfers!
        val sample1 = DownloadItem(
            id = "sample_bbb",
            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            fileName = "BigBuckBunny_1080p.mp4",
            destinationPath = downloadPath,
            status = DownloadStatus.COMPLETED,
            totalBytes = 158L * 1024 * 1024,
            downloadedBytes = 158L * 1024 * 1024,
            numConnections = 8,
            isMagnet = false,
            completedTimestamp = System.currentTimeMillis() - 3600000L
        )

        val sample2 = DownloadItem(
            id = "sample_ubuntu_torrent",
            url = "magnet:?xt=urn:btih:205c080b0cf7446ab05e045ab5c7b949ba3f07a5&dn=Ubuntu_24_04_Server_x64.iso&tr=http%3A%2F%2Ftracker.ipv6tracker.ru%2Fannounce",
            fileName = "Ubuntu_24_04_Server_x64.iso",
            destinationPath = downloadPath,
            status = DownloadStatus.COMPLETED,
            totalBytes = 1950L * 1024 * 1024,
            downloadedBytes = 1950L * 1024 * 1024,
            numConnections = 16,
            isMagnet = true,
            infoHash = "205c080b0cf7446ab05e045ab5c7b949ba3f07a5",
            completedTimestamp = System.currentTimeMillis() - 7200000L
        )

        _downloads.value = listOf(sample1, sample2)
    }

    fun addDownload(
        context: Context,
        rawInput: String,
        numConnections: Int = 8,
        customDestination: String? = null
    ): DownloadItem {
        val destPath = customDestination ?: StorageManager.getDownloadPath(context)
        val trimmed = rawInput.trim()
        val isMagnet = MagnetParser.isMagnetLink(trimmed)

        val (fileName, infoHash, trackers) = if (isMagnet) {
            val parsed = MagnetParser.parse(trimmed)
            Triple(parsed.displayName, parsed.infoHash, parsed.trackers)
        } else {
            val guessedName = URLUtil.guessFileName(trimmed, null, null)
            val cleanName = if (guessedName.isNullOrEmpty() || guessedName == "downloadfile.bin") {
                val candidate = trimmed.substringAfterLast('/').substringBefore('?').substringBefore('#')
                if (candidate.isNotEmpty()) candidate else "download_${System.currentTimeMillis()}.bin"
            } else guessedName
            Triple(cleanName, null, emptyList())
        }

        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            url = trimmed,
            fileName = fileName,
            destinationPath = destPath,
            status = DownloadStatus.QUEUED,
            totalBytes = 0L,
            downloadedBytes = 0L,
            numConnections = numConnections,
            isMagnet = isMagnet,
            infoHash = infoHash,
            trackers = trackers
        )

        _downloads.value = listOf(item) + _downloads.value
        engine.startDownload(item)
        return item
    }

    fun pauseDownload(id: String) {
        val item = _downloads.value.find { it.id == id } ?: return
        item.status = DownloadStatus.PAUSED
        item.speedBytesPerSec = 0L
        engine.pauseDownload(id)
        updateItem(item)
    }

    fun resumeDownload(id: String) {
        val item = _downloads.value.find { it.id == id } ?: return
        item.status = DownloadStatus.CONNECTING
        updateItem(item)
        engine.startDownload(item)
    }

    fun cancelDownload(id: String, deleteFile: Boolean = true) {
        val item = _downloads.value.find { it.id == id }
        engine.cancelDownload(id, deleteFile, item)
        _downloads.value = _downloads.value.filterNot { it.id == id }
    }

    fun clearCompleted() {
        _downloads.value = _downloads.value.filter { it.status != DownloadStatus.COMPLETED }
    }

    private fun updateItem(updatedItem: DownloadItem) {
        _downloads.value = _downloads.value.map {
            if (it.id == updatedItem.id) updatedItem else it
        }
    }
}
