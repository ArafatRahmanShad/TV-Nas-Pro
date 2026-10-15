package com.example.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.example.DownloadService
import com.example.downloader.DownloadItem
import com.example.downloader.DownloadRepository
import com.example.downloader.DownloadStatus
import com.example.server.LocalWebServer
import com.example.storage.StorageLocation
import com.example.storage.StorageManager
import com.example.ui.components.MediaPlaybackView
import com.example.ui.components.QrCodeView

@Composable
fun TvHomeScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloads by DownloadRepository.downloads.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }
    var showQrPairDialog by remember { mutableStateOf(false) }
    var activeMediaItem by remember { mutableStateOf<DownloadItem?>(null) }
    var isPhoneMode by remember { mutableStateOf(false) }

    val currentStoragePath = remember(downloads, showStorageDialog) {
        StorageManager.getDownloadPath(context)
    }

    val availableStorages = remember(showStorageDialog) {
        StorageManager.getAvailableStorages(context)
    }

    val activeStorage = availableStorages.find { it.path == currentStoragePath }
        ?: availableStorages.firstOrNull()

    val downloadingItems = downloads.filter {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING || it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.PAUSED
    }
    val completedItems = downloads.filter { it.status == DownloadStatus.COMPLETED }

    val tvIp = remember { LocalWebServer.getDeviceIpAddress() }
    val serverUrl = "http://$tvIp:8080"

    // If media playback is active, display the full-screen TV player
    if (activeMediaItem != null) {
        MediaPlaybackView(
            item = activeMediaItem!!,
            onClose = { activeMediaItem = null }
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            // TV Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // TV NAS Pro Logo & Status
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "Logo",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "TV NAS Pro",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.5).sp
                                    ),
                                    color = Color.White
                                )
                                Spacer(Modifier.width(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF0F2E22)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(Color(0xFF10B981), CircleShape)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "ONLINE $serverUrl",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF34D399),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "High-Speed IDM Engine • Multi-Thread Parallel • 24x7 Remote Server",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Action Buttons Row (Remote-friendly)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Storage Selector Chip
                    TvActionButton(
                        onClick = { showStorageDialog = true },
                        icon = if (activeStorage?.isUsb == true) Icons.Default.Usb else Icons.Default.Storage,
                        label = if (activeStorage != null) {
                            "${if (activeStorage.isUsb) "USB" else "Drive"}: ${activeStorage.formattedFree} Free"
                        } else "Select Storage",
                        color = Color(0xFF1E293B)
                    )

                    // Phone QR Pair Button
                    TvActionButton(
                        onClick = { showQrPairDialog = true },
                        icon = Icons.Default.QrCode,
                        label = "Pair Phone",
                        color = Color(0xFF0284C7)
                    )

                    // Add Download Button
                    TvActionButton(
                        onClick = { showAddDialog = true },
                        icon = Icons.Default.Add,
                        label = "+ Add Link",
                        color = MaterialTheme.colorScheme.primary,
                        isPrimary = true
                    )
                }
            }

            // IDM Navigation Tabs
            val tabs = listOf(
                "Downloading (${downloadingItems.size})",
                "Completed (${completedItems.size})",
                "Storage & Drives (${availableStorages.size})",
                "Phone Remote"
            )

            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White,
                edgePadding = 0.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .padding(bottom = 16.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTabIndex == index
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF94A3B8)
                            )
                        },
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }

            // Tab Content
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTabIndex) {
                    0 -> DownloadingTabContent(
                        items = downloadingItems,
                        onPause = { DownloadRepository.pauseDownload(it) },
                        onResume = { DownloadRepository.resumeDownload(it) },
                        onCancel = { DownloadRepository.cancelDownload(it, true) },
                        onAddNew = { showAddDialog = true }
                    )
                    1 -> CompletedTabContent(
                        items = completedItems,
                        onPlay = { activeMediaItem = it },
                        onDelete = { DownloadRepository.cancelDownload(it.id, true) },
                        onClearAll = { DownloadRepository.clearCompleted() }
                    )
                    2 -> StorageTabContent(
                        storages = availableStorages,
                        activePath = currentStoragePath,
                        onSelectPath = { path ->
                            StorageManager.setDownloadPath(context, path)
                            Toast.makeText(context, "Storage updated", Toast.LENGTH_SHORT).show()
                        },
                        onOpenSAF = {
                            (context as? Activity)?.let { StorageManager.openSAFPicker(it) }
                        }
                    )
                    3 -> PhoneRemoteTabContent(
                        serverUrl = serverUrl,
                        onSendSample = { url ->
                            DownloadRepository.addDownload(context, url, 8)
                            Toast.makeText(context, "Link queued from Phone Remote!", Toast.LENGTH_SHORT).show()
                            selectedTabIndex = 0
                        }
                    )
                }
            }
        }
    }

    // Dialog: Add Torrent / Link
    if (showAddDialog) {
        AddDownloadDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { url, threads ->
                DownloadRepository.addDownload(context, url, threads)
                showAddDialog = false
                selectedTabIndex = 0
            }
        )
    }

    // Dialog: Storage Selector
    if (showStorageDialog) {
        StorageDialog(
            storages = availableStorages,
            activePath = currentStoragePath,
            onDismiss = { showStorageDialog = false },
            onSelect = { path ->
                StorageManager.setDownloadPath(context, path)
                showStorageDialog = false
            },
            onOpenSAF = {
                (context as? Activity)?.let { StorageManager.openSAFPicker(it) }
            }
        )
    }

    // Dialog: QR Pair Phone
    if (showQrPairDialog) {
        QrPairDialog(
            serverUrl = serverUrl,
            onDismiss = { showQrPairDialog = false }
        )
    }
}

/**
 * TV Focusable Action Button with high-contrast remote outline
 */
@Composable
fun TvActionButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    isPrimary: Boolean = false,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(10.dp),
        color = if (isFocused) MaterialTheme.colorScheme.primary else color,
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) Color.White else Color(0xFF334155)
        ),
        modifier = modifier.height(44.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isFocused) Color.Black else if (isPrimary) Color.Black else Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (isFocused) Color.Black else if (isPrimary) Color.Black else Color.White
            )
        }
    }
}

/**
 * Active Downloading Grid with IDM multi-thread cards
 */
@Composable
fun DownloadingTabContent(
    items: List<DownloadItem>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onAddNew: () -> Unit
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1E293B),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = "Empty",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Text(
                    text = "No Active Downloads",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = "Push a Magnet or URL from your phone, or add a link directly with your TV remote.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8)
                )
                Button(
                    onClick = onAddNew,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Torrent / Direct Link")
                }
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items, key = { it.id }) { item ->
                IdmDownloadCard(
                    item = item,
                    onPause = { onPause(item.id) },
                    onResume = { onResume(item.id) },
                    onCancel = { onCancel(item.id) }
                )
            }
        }
    }
}

/**
 * IDM-Style Download Card with Multi-Segment thread visualization
 */
@Composable
fun IdmDownloadCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151D2F)),
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) MaterialTheme.colorScheme.primary else Color(0xFF22314E)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusable(interactionSource = interactionSource)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header: Icon + File Name + Status Chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (item.isMagnet) Color(0xFF3B1E48) else Color(0xFF1E2E48),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (item.isMagnet) Icons.Default.Link else if (item.isVideo) Icons.Default.Movie else Icons.Default.Download,
                            contentDescription = null,
                            tint = if (item.isMagnet) Color(0xFFD946EF) else Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (item.isMagnet) "Torrent Swarm • ${item.numConnections} peers" else "Parallel Range HTTP • ${item.numConnections} Threads",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                }

                // Status Badge
                val statusColor = when (item.status) {
                    DownloadStatus.DOWNLOADING -> Color(0xFF10B981)
                    DownloadStatus.CONNECTING -> Color(0xFFF59E0B)
                    DownloadStatus.PAUSED -> Color(0xFF64748B)
                    DownloadStatus.FAILED -> Color(0xFFEF4444)
                    DownloadStatus.COMPLETED -> Color(0xFF06B6D4)
                    DownloadStatus.QUEUED -> Color(0xFF8B5CF6)
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = item.status.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Main Progress Bar
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color(0xFF1E293B)
            )

            // IDM Segment Visualization (Thread Chunks)
            if (item.segments.isNotEmpty() && item.status == DownloadStatus.DOWNLOADING) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    item.segments.forEach { seg ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    Color(0xFF38BDF8).copy(alpha = (0.25f + seg.progress * 0.75f).coerceIn(0.25f, 1f)),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Stats Row: Downloaded / Total • Speed • ETA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${item.downloadedFormatted} / ${item.totalFormatted}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color.White
                    )
                    Text(
                        text = "Progress: ${(item.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Speed",
                            tint = Color(0xFFFACC15),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = item.speedFormatted,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFFACC15)
                        )
                    }
                    Text(
                        text = "ETA: ${item.etaFormatted}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (item.status == DownloadStatus.DOWNLOADING) {
                    OutlinedButton(
                        onClick = onPause,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pause")
                    }
                } else if (item.status == DownloadStatus.PAUSED || item.status == DownloadStatus.FAILED) {
                    Button(
                        onClick = onResume,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Resume")
                    }
                }

                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }
}

/**
 * Completed Files Tab with 1-click "Play in TV Player"
 */
@Composable
fun CompletedTabContent(
    items: List<DownloadItem>,
    onPlay: (DownloadItem) -> Unit,
    onDelete: (DownloadItem) -> Unit,
    onClearAll: () -> Unit
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No completed downloads yet", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${items.size} Completed Files Available on TV",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                TextButton(onClick = onClearAll) {
                    Text("Clear List History", color = Color(0xFF94A3B8))
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items, key = { it.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF151D2F)),
                        border = BorderStroke(1.dp, Color(0xFF22314E))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0F2E22),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (item.isVideo) Icons.Default.Movie else Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.fileName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${item.totalFormatted} • Saved in ${item.destinationPath.substringAfterLast('/')}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onPlay(item) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Play on TV")
                                }

                                IconButton(
                                    onClick = { onDelete(item) }
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Storage & USB Tab: Switch between Internal and USB Drive
 */
@Composable
fun StorageTabContent(
    storages: List<StorageLocation>,
    activePath: String,
    onSelectPath: (String) -> Unit,
    onOpenSAF: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Storage & USB Drives",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = "Choose where TV NAS Pro saves incoming movies, torrents, and files.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8)
                )
            }

            Button(
                onClick = onOpenSAF,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
            ) {
                Icon(Icons.Default.DriveFileMove, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Custom USB Path (SAF)")
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(storages) { store ->
                val isActive = store.path == activePath
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) Color(0xFF16253B) else Color(0xFF151D2F)
                    ),
                    border = BorderStroke(
                        width = if (isActive) 2.dp else 1.dp,
                        color = if (isActive) MaterialTheme.colorScheme.primary else Color(0xFF22314E)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (store.isUsb) Color(0xFF0284C7) else Color(0xFF475569),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (store.isUsb) Icons.Default.Usb else Icons.Default.Storage,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = store.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = store.path,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isActive) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF0F2E22)
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF10B981),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        LinearProgressIndicator(
                            progress = { store.usedPercentage },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (store.usedPercentage > 0.9f) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
                            trackColor = Color(0xFF1E293B)
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Free: ${store.formattedFree}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF34D399)
                            )
                            Text(
                                text = "Total: ${store.formattedTotal}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        if (!isActive) {
                            Button(
                                onClick = { onSelectPath(store.path) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Set as Default Download Storage")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Currently Selected", color = Color(0xFF94A3B8))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Phone Remote Pairing Tab: QR code & Local WiFi web interface
 */
@Composable
fun PhoneRemoteTabContent(
    serverUrl: String,
    onSendSample: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: High-Res QR Code Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151D2F)),
            border = BorderStroke(1.dp, Color(0xFF22314E)),
            modifier = Modifier.padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Scan with Phone Camera",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(Modifier.height(12.dp))
                QrCodeView(
                    data = serverUrl,
                    sizeDp = 220,
                    primaryColor = Color(0xFF0F172A),
                    backgroundColor = Color.White
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = serverUrl,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Right Column: Step-by-Step Instructions & Test Buttons
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Control Your TV NAS from Any Device",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            Text(
                text = "No app installation required on your phone! Simply ensure your phone and TV are connected to the same Wi-Fi network, then scan the QR code to open the TV NAS Remote in Chrome or Safari.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF94A3B8)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StepRow("1", "Open Camera or Web Browser on your Phone / PC")
                StepRow("2", "Visit $serverUrl")
                StepRow("3", "Paste any Torrent Magnet or Direct Download Link")
                StepRow("4", "TV downloads 24x7 with IDM speeds even with screen off")
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Test Remote Push Right Now:",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        onSendSample("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Push Big Buck Bunny (1080p)")
                }

                Button(
                    onClick = {
                        onSendSample("magnet:?xt=urn:btih:205c080b0cf7446ab05e045ab5c7b949ba3f07a5&dn=Ubuntu_24_04_Server_x64.iso")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Push Ubuntu Torrent")
                }
            }
        }
    }
}

@Composable
private fun StepRow(num: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(num, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}

/**
 * Add Download Dialog
 */
@Composable
fun AddDownloadDialog(
    onDismiss: () -> Unit,
    onAdd: (url: String, threads: Int) -> Unit
) {
    var urlText by remember { mutableStateOf("") }
    var selectedThreads by remember { mutableIntStateOf(8) }
    val threadOptions = listOf(1, 2, 4, 8, 16)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Torrent / Direct Download", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Paste a magnet link (magnet:?xt=...) or any direct HTTP/HTTPS file URL.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )

                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = { Text("magnet:?xt=... or https://example.com/file.mp4", color = Color(0xFF64748B)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    minLines = 3,
                    maxLines = 5
                )

                // Parallel Threads Selector
                Column {
                    Text(
                        text = "IDM Parallel Connections: $selectedThreads Threads",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        threadOptions.forEach { t ->
                            val isSelected = t == selectedThreads
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedThreads = t },
                                label = { Text("${t}x") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.Black,
                                    labelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Quick Samples
                Text(
                    text = "Quick 1-Click Samples:",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            urlText = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("1080p Video", fontSize = 12.sp)
                    }
                    FilledTonalButton(
                        onClick = {
                            urlText = "magnet:?xt=urn:btih:205c080b0cf7446ab05e045ab5c7b949ba3f07a5&dn=Ubuntu_24_04_Server_x64.iso"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Torrent Magnet", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (urlText.isNotBlank()) {
                        onAdd(urlText.trim(), selectedThreads)
                    }
                },
                enabled = urlText.isNotBlank()
            ) {
                Text("Start Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF151D2F)
    )
}

/**
 * Storage Selector Dialog
 */
@Composable
fun StorageDialog(
    storages: List<StorageLocation>,
    activePath: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onOpenSAF: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Switch Storage Drive", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                storages.forEach { store ->
                    val isActive = store.path == activePath
                    Surface(
                        onClick = { onSelect(store.path) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isActive) Color(0xFF1E2E48) else Color(0xFF111A2D),
                        border = BorderStroke(
                            1.dp,
                            if (isActive) MaterialTheme.colorScheme.primary else Color(0xFF22314E)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (store.isUsb) Icons.Default.Usb else Icons.Default.Storage,
                                contentDescription = null,
                                tint = if (store.isUsb) Color(0xFF38BDF8) else Color.White
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(store.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text("Free: ${store.formattedFree} / ${store.formattedTotal}", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
                            }
                            if (isActive) {
                                Icon(Icons.Default.Check, contentDescription = "Active", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onOpenSAF) {
                Text("Browse USB (SAF)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF151D2F)
    )
}

/**
 * QR Code Pairing Dialog
 */
@Composable
fun QrPairDialog(
    serverUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Pair Phone with TV NAS", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                QrCodeView(
                    data = serverUrl,
                    sizeDp = 200,
                    primaryColor = Color(0xFF0F172A),
                    backgroundColor = Color.White
                )

                Text(
                    text = serverUrl,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Scan with your phone camera on the same Wi-Fi, or type this address into Chrome or Safari to push downloads immediately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("TV URL", serverUrl))
                    Toast.makeText(context, "URL copied: $serverUrl", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Copy URL")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF151D2F)
    )
}
