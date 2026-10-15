package com.example.ui.components

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.downloader.DownloadItem
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun MediaPlaybackView(
    item: DownloadItem,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val target = item.targetFile
            val mediaItem = if (target.exists() && target.length() > 0) {
                MediaItem.fromUri(Uri.fromFile(target))
            } else {
                // If local file is demo or streaming fallback, stream from source URL
                MediaItem.fromUri(Uri.parse(item.url))
            }
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // TV Top Bar Overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = "TV NAS Pro ExoPlayer • ${item.totalFormatted}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .background(Color(0x88000000), RoundedCornerShape(50))
                    .focusable()
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Player",
                    tint = Color.White
                )
            }
        }

        // TV Quick Controls at bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
                .background(Color(0xAA0F172A), RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val newPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                    exoPlayer.seekTo(newPos)
                },
                modifier = Modifier.focusable()
            ) {
                Icon(Icons.Default.FastRewind, contentDescription = "Rewind 10s", tint = Color.White)
            }

            Button(
                onClick = {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                        isPlaying = false
                    } else {
                        exoPlayer.play()
                        isPlaying = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.focusable()
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play"
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isPlaying) "Pause" else "Play")
            }

            IconButton(
                onClick = {
                    val newPos = (exoPlayer.currentPosition + 10000)
                    exoPlayer.seekTo(newPos)
                },
                modifier = Modifier.focusable()
            ) {
                Icon(Icons.Default.FastForward, contentDescription = "Forward 10s", tint = Color.White)
            }
        }
    }
}
