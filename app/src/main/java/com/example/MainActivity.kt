package com.example

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import com.example.downloader.DownloadRepository
import com.example.ui.PhoneRemoteScreen
import com.example.ui.TvHomeScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize repository and start 24x7 background download service & HTTP server
        DownloadRepository.init(this)
        DownloadService.startService(this)

        // 2. Handle shared or deep-linked download URLs / magnets
        handleIntent(intent)

        setContent {
            MyApplicationTheme(darkTheme = true) {
                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val isTv = packageManager.hasSystemFeature("android.software.leanback")

                // Start in TV mode if on TV or landscape screen, otherwise allow toggling
                var currentModeIsTv by remember { mutableStateOf(isTv || isLandscape || configuration.screenWidthDp >= 600) }

                BackHandler(enabled = !currentModeIsTv) {
                    currentModeIsTv = true
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                    containerColor = Color(0xFF070B14)
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(Color(0xFF070B14))
                    ) {
                        if (currentModeIsTv) {
                            TvHomeScreen()
                        } else {
                            PhoneRemoteScreen(
                                onSwitchToTvMode = { currentModeIsTv = true }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrEmpty()) {
                    DownloadRepository.addDownload(this, sharedText, 8)
                    Toast.makeText(this, "Link received and added to TV NAS!", Toast.LENGTH_LONG).show()
                }
            }
            Intent.ACTION_VIEW -> {
                val dataUri = intent.dataString
                if (!dataUri.isNullOrEmpty()) {
                    DownloadRepository.addDownload(this, dataUri, 8)
                    Toast.makeText(this, "Magnet received and added to TV NAS!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
