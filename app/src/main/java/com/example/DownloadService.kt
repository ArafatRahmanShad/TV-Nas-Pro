package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.downloader.DownloadRepository
import com.example.downloader.DownloadStatus
import com.example.server.LocalWebServer
import com.example.storage.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "tv_nas_downloads_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.ACTION_START"
        const val ACTION_ADD_LINK = "com.example.ACTION_ADD_LINK"
        const val EXTRA_LINK = "extra_link"
        const val EXTRA_THREADS = "extra_threads"

        fun startService(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun enqueueLink(context: Context, link: String, threads: Int = 8) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_ADD_LINK
                putExtra(EXTRA_LINK, link)
                putExtra(EXTRA_THREADS, threads)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var notificationUpdateJob: Job? = null
    private var webServer: LocalWebServer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Initialize repositories
        DownloadRepository.init(this)

        // Start Local Remote HTTP Server on Port 8080 for Phone & Web Control
        webServer = LocalWebServer.start(this, 8080)

        // Start Foreground immediately
        startInForeground()

        // Periodically refresh notification with live speeds
        startNotificationSpeedMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ADD_LINK -> {
                val link = intent.getStringExtra(EXTRA_LINK)
                val threads = intent.getIntExtra(EXTRA_THREADS, 8)
                if (!link.isNullOrEmpty()) {
                    DownloadRepository.addDownload(this, link, threads)
                }
            }
        }
        return START_STICKY
    }

    private fun startInForeground() {
        val notification = buildNotification("TV NAS Pro Active", "Remote Web Server running on port 8080")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startNotificationSpeedMonitor() {
        notificationUpdateJob?.cancel()
        notificationUpdateJob = serviceScope.launch {
            while (isActive) {
                delay(2000)
                val downloads = DownloadRepository.downloads.value
                val active = downloads.filter { it.status == DownloadStatus.DOWNLOADING }
                val totalSpeed = active.sumOf { it.speedBytesPerSec }
                val ip = LocalWebServer.getDeviceIpAddress()

                val title = if (active.isNotEmpty()) {
                    "TV NAS Pro • ${StorageManager.formatSpeed(totalSpeed)} (${active.size} active)"
                } else {
                    "TV NAS Pro • Idle"
                }

                val content = "Phone Remote: http://$ip:8080 • Storage: ${StorageManager.getDownloadPath(this@DownloadService)}"

                val notification = buildNotification(title, content)
                val nm = getSystemService(NotificationManager::class.java)
                nm?.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TV NAS Background Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps TV NAS downloading and remote HTTP server running 24x7"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        notificationUpdateJob?.cancel()
        LocalWebServer.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
