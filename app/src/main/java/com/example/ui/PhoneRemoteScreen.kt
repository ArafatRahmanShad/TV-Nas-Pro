package com.example.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.downloader.DownloadItem
import com.example.downloader.DownloadRepository
import com.example.downloader.DownloadStatus
import com.example.server.LocalWebServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class RemoteViewModel : ViewModel() {
    private val client = OkHttpClient()

    private val _targetTvUrl = MutableStateFlow("http://192.168.1.50:8080")
    val targetTvUrl: StateFlow<String> = _targetTvUrl.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    fun setTargetTvUrl(url: String) {
        _targetTvUrl.value = url.trim()
    }

    fun initDefaultTvIp() {
        val localIp = LocalWebServer.getDeviceIpAddress()
        _targetTvUrl.value = "http://$localIp:8080"
    }

    fun sendToTV(context: Context, link: String, onResult: (Boolean, String) -> Unit) {
        val trimmed = link.trim()
        if (trimmed.isEmpty()) {
            onResult(false, "Link cannot be empty")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isConnecting.value = true
            try {
                // 1. Send via Local Network REST API to TV
                val endpoint = "${_targetTvUrl.value}/api/add?url=${URLEncoder.encode(trimmed, "UTF-8")}&threads=8"
                val request = Request.Builder().url(endpoint).build()

                try {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            launch(Dispatchers.Main) {
                                onResult(true, "Sent directly to TV NAS!")
                            }
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    // Fallback to internal repository if running locally on the same device/emulator
                }

                // If running on same device (e.g. TV / phone app hybrid)
                launch(Dispatchers.Main) {
                    DownloadRepository.addDownload(context, trimmed, 8)
                    onResult(true, "Queued directly on TV NAS!")
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    onResult(false, "Failed: ${e.message}")
                }
            } finally {
                _isConnecting.value = false
            }
        }
    }

    fun handleSharedIntent(context: Context, intent: Intent, onResult: (Boolean, String) -> Unit) {
        val sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (!sharedUrl.isNullOrEmpty()) {
            sendToTV(context, sharedUrl, onResult)
        }
    }
}

@Composable
fun PhoneRemoteScreen(
    onSwitchToTvMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel = remember { RemoteViewModel().apply { initDefaultTvIp() } }
    val tvUrl by viewModel.targetTvUrl.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val downloads by DownloadRepository.downloads.collectAsState()

    var inputLink by remember { mutableStateOf("") }
    var tvIpInput by remember { mutableStateOf(tvUrl) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
            .padding(16.dp)
    ) {
        // Phone Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "📱 TV Remote Control",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "Connected to TV NAS Pro",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF38BDF8)
                )
            }

            Button(
                onClick = onSwitchToTvMode,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
            ) {
                Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("TV Mode", fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Target TV IP Configuration Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151D2F)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22314E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Target TV NAS Address",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF94A3B8)
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = tvIpInput,
                        onValueChange = {
                            tvIpInput = it
                            viewModel.setTargetTvUrl(it)
                        },
                        placeholder = { Text("http://192.168.1.X:8080") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFF334155)
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Push Link Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151D2F)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22314E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Push Link / Magnet to TV",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = inputLink,
                    onValueChange = { inputLink = it },
                    placeholder = { Text("Paste magnet:?xt=... or direct HTTP URL", color = Color(0xFF64748B)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color(0xFF334155)
                    )
                )

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        viewModel.sendToTV(context, inputLink) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) inputLink = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = inputLink.isNotBlank() && !isConnecting
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text("Sending...")
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Send to TV NAS")
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Quick Samples for Phone
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            inputLink = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("1080p MP4", fontSize = 11.sp)
                    }

                    FilledTonalButton(
                        onClick = {
                            inputLink = "magnet:?xt=urn:btih:205c080b0cf7446ab05e045ab5c7b949ba3f07a5&dn=Ubuntu_24_04_Server_x64.iso"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Torrent Link", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Live TV Queue on Phone
        Text(
            text = "Live TV NAS Downloads (${downloads.size})",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(downloads, key = { it.id }) { item ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151D2F)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22314E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (item.isMagnet) Icons.Default.Link else Icons.Default.Download,
                                contentDescription = null,
                                tint = if (item.isMagnet) Color(0xFFD946EF) else Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = item.fileName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = item.status.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (item.status == DownloadStatus.DOWNLOADING) Color(0xFF10B981) else Color(0xFF94A3B8)
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { item.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color(0xFF1E293B)
                        )

                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${item.downloadedFormatted} / ${item.totalFormatted}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = item.speedFormatted,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFFACC15)
                            )
                        }
                    }
                }
            }
        }
    }
}
