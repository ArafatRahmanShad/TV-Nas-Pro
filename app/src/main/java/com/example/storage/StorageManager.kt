package com.example.storage

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import java.io.File
import java.text.DecimalFormat

data class StorageLocation(
    val id: String,
    val name: String,
    val path: String,
    val isExternal: Boolean,
    val isUsb: Boolean,
    val totalBytes: Long,
    val freeBytes: Long
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedPercentage: Float get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()) else 0f
    val formattedTotal: String get() = StorageManager.formatBytes(totalBytes)
    val formattedFree: String get() = StorageManager.formatBytes(freeBytes)
    val formattedUsed: String get() = StorageManager.formatBytes(usedBytes)
}

object StorageManager {
    private const val PREF_DOWNLOAD_PATH = "download_path"
    private const val DEFAULT_FOLDER_NAME = "TVNAS_Downloads"

    fun getAvailableStorages(context: Context): List<StorageLocation> {
        val storages = mutableListOf<StorageLocation>()

        // 1. Internal Private App Files
        val internalDir = File(context.filesDir, DEFAULT_FOLDER_NAME)
        if (!internalDir.exists()) internalDir.mkdirs()
        val internalStat = getStatFs(internalDir)
        storages.add(
            StorageLocation(
                id = "internal",
                name = "Internal App Storage",
                path = internalDir.absolutePath,
                isExternal = false,
                isUsb = false,
                totalBytes = internalStat.first,
                freeBytes = internalStat.second
            )
        )

        // 2. Primary External Public Downloads
        val externalPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val tvDownloads = File(externalPublicDir, DEFAULT_FOLDER_NAME)
        if (!tvDownloads.exists()) tvDownloads.mkdirs()
        val pubStat = getStatFs(tvDownloads)
        storages.add(
            StorageLocation(
                id = "shared_downloads",
                name = "Device Downloads (Public)",
                path = tvDownloads.absolutePath,
                isExternal = true,
                isUsb = false,
                totalBytes = pubStat.first,
                freeBytes = pubStat.second
            )
        )

        // 3. App-Specific External Dirs (SD Card / USB Storage attached)
        try {
            val externalDirs = context.getExternalFilesDirs(null)
            externalDirs.forEachIndexed { index, dir ->
                if (dir != null) {
                    val targetDir = File(dir, DEFAULT_FOLDER_NAME)
                    if (!targetDir.exists()) targetDir.mkdirs()
                    val stat = getStatFs(targetDir)
                    val isRemovable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        try {
                            Environment.isExternalStorageRemovable(dir)
                        } catch (e: Exception) {
                            index > 0
                        }
                    } else index > 0

                    val label = if (isRemovable) {
                        "USB Drive / SD Card ${if (index > 0) "#$index" else ""}"
                    } else {
                        "External App Media Storage"
                    }

                    // Avoid duplicate path entries
                    if (storages.none { it.path == targetDir.absolutePath }) {
                        storages.add(
                            StorageLocation(
                                id = "ext_$index",
                                name = label,
                                path = targetDir.absolutePath,
                                isExternal = true,
                                isUsb = isRemovable,
                                totalBytes = stat.first,
                                freeBytes = stat.second
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return storages
    }

    private fun getStatFs(file: File): Pair<Long, Long> {
        return try {
            val stat = StatFs(file.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            Pair(totalBlocks * blockSize, availableBlocks * blockSize)
        } catch (e: Exception) {
            Pair(100L * 1024 * 1024 * 1024, 60L * 1024 * 1024 * 1024) // Fallback default
        }
    }

    private const val PREFS_NAME = "tv_nas_preferences"

    fun getDownloadPath(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(PREF_DOWNLOAD_PATH, null)
        if (!saved.isNullOrEmpty()) {
            val f = File(saved)
            if (f.exists() || f.mkdirs()) {
                return f.absolutePath
            }
        }
        val defaultDir = File(context.filesDir, DEFAULT_FOLDER_NAME)
        if (!defaultDir.exists()) defaultDir.mkdirs()
        return defaultDir.absolutePath
    }

    fun setDownloadPath(context: Context, path: String) {
        val f = File(path)
        if (!f.exists()) {
            f.mkdirs()
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_DOWNLOAD_PATH, path)
            .apply()

        Toast.makeText(context, "Download location set to: $path", Toast.LENGTH_SHORT).show()
    }

    fun openSAFPicker(activity: Activity) {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            activity.startActivityForResult(intent, 999)
        } catch (e: Exception) {
            Toast.makeText(activity, "Storage picker not supported on this TV device", Toast.LENGTH_SHORT).show()
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val clampedGroup = digitGroups.coerceIn(0, units.size - 1)
        val df = DecimalFormat("#,##0.#")
        return "${df.format(bytes / Math.pow(1024.0, clampedGroup.toDouble()))} ${units[clampedGroup]}"
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        val kb = bytesPerSec / 1024.0
        return if (kb >= 1024) {
            val mb = kb / 1024.0
            String.format("%.2f MB/s", mb)
        } else {
            String.format("%.1f KB/s", kb)
        }
    }
}
