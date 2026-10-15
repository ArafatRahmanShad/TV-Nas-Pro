package com.example.downloader

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class IdmEngine(
    private val onUpdate: (DownloadItem) -> Unit
) {
    companion object {
        private const val TAG = "IdmEngine"
        private const val BUFFER_SIZE = 64 * 1024
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val bitTorrentEngine = BitTorrentEngine(client, onUpdate)

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startDownload(item: DownloadItem) {
        if (activeJobs.containsKey(item.id)) {
            Log.d(TAG, "Download ${item.id} already active")
            return
        }

        val job = scope.launch {
            try {
                if (item.isMagnet) {
                    bitTorrentEngine.downloadTorrent(item)
                } else {
                    handleHttpDownload(item)
                }
            } catch (ce: CancellationException) {
                Log.d(TAG, "Download ${item.id} paused/cancelled")
                item.status = DownloadStatus.PAUSED
                item.speedBytesPerSec = 0L
                onUpdate(item)
            } catch (e: Exception) {
                Log.e(TAG, "Download ${item.id} error", e)
                item.status = DownloadStatus.FAILED
                item.errorMessage = e.message ?: "Download connection error"
                item.speedBytesPerSec = 0L
                onUpdate(item)
            } finally {
                activeJobs.remove(item.id)
            }
        }
        activeJobs[item.id] = job
    }

    fun pauseDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
    }

    fun cancelDownload(id: String, deleteFile: Boolean = false, item: DownloadItem? = null) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        if (deleteFile && item != null) {
            try {
                item.targetFile.delete()
                for (i in 0..16) {
                    File(item.destinationPath, "${item.fileName}.part$i").delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun handleHttpDownload(item: DownloadItem) {
        item.status = DownloadStatus.CONNECTING
        item.errorMessage = null
        onUpdate(item)

        val destinationDir = File(item.destinationPath)
        if (!destinationDir.exists()) destinationDir.mkdirs()
        val targetFile = item.targetFile

        // Probe server with range request: bytes=0-1023
        var totalLength = -1L
        var supportsRanges = false
        var initialChunkBytes: ByteArray? = null

        try {
            val probeReq = Request.Builder()
                .url(item.url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .header("Range", "bytes=0-1023")
                .build()

            client.newCall(probeReq).execute().use { response ->
                if (response.code == 206) {
                    supportsRanges = true
                    initialChunkBytes = response.body?.bytes()
                    val contentRange = response.header("Content-Range")
                    if (contentRange != null && contentRange.contains("/")) {
                        totalLength = contentRange.substringAfter("/").toLongOrNull() ?: -1L
                    }
                } else if (response.isSuccessful) {
                    // Range not supported, full body started
                    supportsRanges = false
                    totalLength = response.body?.contentLength() ?: -1L
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Probe request failed, attempting standard connection", e)
        }

        if (totalLength > 0) {
            item.totalBytes = totalLength
        }

        item.status = DownloadStatus.DOWNLOADING
        onUpdate(item)

        val threads = if (supportsRanges && totalLength > 1024 * 1024) {
            item.numConnections.coerceIn(2, 16)
        } else {
            1
        }

        if (threads > 1 && totalLength > 0) {
            runParallelSegmentDownload(item, targetFile, totalLength, threads)
        } else {
            runSequentialStreamDownload(item, targetFile, totalLength)
        }
    }

    /**
     * IDM Architecture: Downloads into separate .partX files in parallel, then merges.
     * Prevents lock collisions and works across all storage media.
     */
    private suspend fun runParallelSegmentDownload(
        item: DownloadItem,
        targetFile: File,
        totalSize: Long,
        threads: Int
    ) = coroutineScope {
        val chunkSize = totalSize / threads
        val segments = mutableListOf<DownloadSegment>()
        for (i in 0 until threads) {
            val start = i * chunkSize
            val end = if (i == threads - 1) totalSize - 1 else (i + 1) * chunkSize - 1
            val partFile = File(item.destinationPath, "${item.fileName}.part$i")
            val existingLen = if (partFile.exists()) partFile.length() else 0L
            segments.add(DownloadSegment(i, start, end, existingLen))
        }
        item.segments = segments

        val totalDownloadedAtomic = AtomicLong(segments.sumOf { it.downloadedBytes })
        var lastTime = System.currentTimeMillis()
        var lastBytes = totalDownloadedAtomic.get()

        val monitorJob = launch {
            while (isActive) {
                delay(500)
                val current = totalDownloadedAtomic.get()
                val now = System.currentTimeMillis()
                val diffTime = (now - lastTime).coerceAtLeast(1)
                item.speedBytesPerSec = ((current - lastBytes) * 1000L) / diffTime
                item.downloadedBytes = current
                lastTime = now
                lastBytes = current
                onUpdate(item)
            }
        }

        try {
            val segmentJobs = segments.map { seg ->
                async(Dispatchers.IO) {
                    val partFile = File(item.destinationPath, "${item.fileName}.part${seg.index}")
                    val startOffset = seg.startByte + seg.downloadedBytes
                    if (startOffset > seg.endByte) return@async // Already complete

                    val req = Request.Builder()
                        .url(item.url)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "*/*")
                        .header("Range", "bytes=$startOffset-${seg.endByte}")
                        .build()

                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful && resp.code != 206) {
                            throw Exception("Segment ${seg.index} HTTP error: ${resp.code}")
                        }
                        val body = resp.body ?: throw Exception("Segment ${seg.index} empty body")
                        val input = body.byteStream()
                        val buffer = ByteArray(BUFFER_SIZE)

                        FileOutputStream(partFile, true).use { fos ->
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                fos.write(buffer, 0, read)
                                seg.downloadedBytes += read
                                totalDownloadedAtomic.addAndGet(read.toLong())
                            }
                        }
                    }
                }
            }

            segmentJobs.awaitAll()

            // Merge all .partX files into targetFile
            FileOutputStream(targetFile).use { outStream ->
                for (i in 0 until threads) {
                    val partFile = File(item.destinationPath, "${item.fileName}.part$i")
                    if (partFile.exists()) {
                        FileInputStream(partFile).use { inStream ->
                            inStream.copyTo(outStream, BUFFER_SIZE)
                        }
                        partFile.delete()
                    }
                }
            }

            item.downloadedBytes = totalSize
            item.totalBytes = totalSize
            item.status = DownloadStatus.COMPLETED
            item.speedBytesPerSec = 0L
            item.completedTimestamp = System.currentTimeMillis()
            onUpdate(item)
        } finally {
            monitorJob.cancel()
        }
    }

    private suspend fun runSequentialStreamDownload(
        item: DownloadItem,
        targetFile: File,
        totalSize: Long
    ) = coroutineScope {
        val existingLen = if (targetFile.exists()) targetFile.length() else 0L
        val reqBuilder = Request.Builder()
            .url(item.url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")

        if (existingLen > 0) {
            reqBuilder.header("Range", "bytes=$existingLen-")
        }

        var lastTime = System.currentTimeMillis()
        var lastBytes = existingLen
        val totalDownloadedAtomic = AtomicLong(existingLen)

        val monitorJob = launch {
            while (isActive) {
                delay(500)
                val current = totalDownloadedAtomic.get()
                val now = System.currentTimeMillis()
                val diffTime = (now - lastTime).coerceAtLeast(1)
                item.speedBytesPerSec = ((current - lastBytes) * 1000L) / diffTime
                item.downloadedBytes = current
                lastTime = now
                lastBytes = current
                onUpdate(item)
            }
        }

        try {
            client.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    throw Exception("HTTP download error: ${response.code}")
                }
                val body = response.body ?: throw Exception("Empty body")
                if (item.totalBytes <= 0) {
                    val len = body.contentLength()
                    if (len > 0) item.totalBytes = if (response.code == 206) len + existingLen else len
                }

                val input = body.byteStream()
                val buffer = ByteArray(BUFFER_SIZE)
                val append = response.code == 206 && existingLen > 0

                FileOutputStream(targetFile, append).use { fos ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        fos.write(buffer, 0, read)
                        totalDownloadedAtomic.addAndGet(read.toLong())
                    }
                }
            }

            item.downloadedBytes = targetFile.length()
            if (item.totalBytes <= 0) item.totalBytes = item.downloadedBytes
            item.status = DownloadStatus.COMPLETED
            item.speedBytesPerSec = 0L
            item.completedTimestamp = System.currentTimeMillis()
            onUpdate(item)
        } finally {
            monitorJob.cancel()
        }
    }
}
