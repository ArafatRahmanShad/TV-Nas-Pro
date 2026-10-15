package com.example.server

import android.content.Context
import android.util.Log
import com.example.downloader.DownloadRepository
import com.example.storage.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class LocalWebServer(
    private val context: Context,
    val port: Int = 8080
) {
    companion object {
        private const val TAG = "LocalWebServer"
        private var instance: LocalWebServer? = null

        fun start(context: Context, port: Int = 8080): LocalWebServer {
            if (instance == null || !instance!!.isRunning) {
                instance = LocalWebServer(context.applicationContext, port).also { it.startServer() }
            }
            return instance!!
        }

        fun stop() {
            instance?.stopServer()
            instance = null
        }

        fun getDeviceIpAddress(): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isLoopback || !networkInterface.isUp) continue
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            return addr.hostAddress ?: "127.0.0.1"
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return "192.168.1.50"
        }
    }

    private var serverSocket: ServerSocket? = null
    var isRunning: Boolean = false
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startServer() {
        if (isRunning) return
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true
                Log.d(TAG, "LocalWebServer started on port $port")

                while (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
                    val clientSocket = serverSocket!!.accept()
                    scope.launch {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Server socket error", e)
                }
            } finally {
                isRunning = false
            }
        }
    }

    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val out = s.getOutputStream()

                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0]
                val fullPath = parts[1]
                val path = fullPath.substringBefore("?")
                val queryString = if (fullPath.contains("?")) fullPath.substringAfter("?") else ""

                // Read headers
                val headers = mutableMapOf<String, String>()
                var line: String?
                var contentLength = 0
                while (reader.readLine().also { line = it } != null && !line.isNullOrEmpty()) {
                    val idx = line!!.indexOf(":")
                    if (idx > 0) {
                        val key = line!!.substring(0, idx).trim().lowercase()
                        val value = line!!.substring(idx + 1).trim()
                        headers[key] = value
                        if (key == "content-length") {
                            contentLength = value.toIntOrNull() ?: 0
                        }
                    }
                }

                // Read body if present
                var body = ""
                if (contentLength > 0) {
                    val charArray = CharArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val count = reader.read(charArray, read, contentLength - read)
                        if (count == -1) break
                        read += count
                    }
                    body = String(charArray, 0, read)
                }

                // Route requests
                when {
                    path == "/" || path == "/remote" -> serveWebRemoteHtml(out)
                    path == "/api/status" -> serveStatusJson(out)
                    path == "/api/add" || path == "/add" -> handleAddDownload(queryString, body, out)
                    path == "/api/action" -> handleAction(queryString, body, out)
                    else -> sendResponse(out, 404, "text/plain", "Not Found")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client handle exception", e)
        }
    }

    private fun handleAddDownload(queryString: String, body: String, out: OutputStream) {
        val params = parseQueryParams(if (queryString.isNotEmpty()) queryString else body)
        val url = params["url"] ?: params["link"]

        if (!url.isNullOrEmpty()) {
            val connections = params["threads"]?.toIntOrNull() ?: 8
            val added = DownloadRepository.addDownload(context, url, connections)
            val json = """{"status":"ok","message":"Download queued","id":"${added.id}","file":"${added.fileName}"}"""
            sendResponse(out, 200, "application/json", json)
        } else {
            sendResponse(out, 400, "application/json", """{"status":"error","message":"Missing 'url' parameter"}""")
        }
    }

    private fun handleAction(queryString: String, body: String, out: OutputStream) {
        val params = parseQueryParams(if (queryString.isNotEmpty()) queryString else body)
        val id = params["id"]
        val action = params["action"]?.lowercase()

        if (!id.isNullOrEmpty() && !action.isNullOrEmpty()) {
            when (action) {
                "pause" -> DownloadRepository.pauseDownload(id)
                "resume" -> DownloadRepository.resumeDownload(id)
                "cancel", "delete" -> DownloadRepository.cancelDownload(id, true)
            }
            sendResponse(out, 200, "application/json", """{"status":"ok","action":"$action","id":"$id"}""")
        } else {
            sendResponse(out, 400, "application/json", """{"status":"error","message":"Missing id or action"}""")
        }
    }

    private fun serveStatusJson(out: OutputStream) {
        val downloads = DownloadRepository.downloads.value
        val storage = StorageManager.getAvailableStorages(context).firstOrNull()

        val itemsJson = downloads.joinToString(",") { d ->
            """
            {
                "id":"${d.id}",
                "fileName":"${escapeJson(d.fileName)}",
                "progress":${d.progress},
                "speed":"${d.speedFormatted}",
                "downloaded":"${d.downloadedFormatted}",
                "total":"${d.totalFormatted}",
                "status":"${d.status.name}",
                "isMagnet":${d.isMagnet},
                "eta":"${d.etaFormatted}"
            }
            """.trimIndent()
        }

        val json = """
        {
            "tvIp":"${getDeviceIpAddress()}",
            "storagePath":"${escapeJson(StorageManager.getDownloadPath(context))}",
            "storageFree":"${storage?.formattedFree ?: "Unknown"}",
            "downloads":[$itemsJson]
        }
        """.trimIndent()

        sendResponse(out, 200, "application/json", json)
    }

    private fun serveWebRemoteHtml(out: OutputStream) {
        val ip = getDeviceIpAddress()
        val html = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
            <title>TV NAS Pro - Remote Control</title>
            <style>
                :root {
                    --bg: #0b0f19;
                    --card: #151d2f;
                    --primary: #38bdf8;
                    --primary-hover: #0284c7;
                    --text: #f8fafc;
                    --text-sec: #94a3b8;
                    --border: #22314e;
                    --success: #10b981;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
                body { background: var(--bg); color: var(--text); padding: 16px; }
                .container { max-width: 600px; margin: 0 auto; }
                header { display: flex; align-items: center; justify-content: space-between; padding-bottom: 16px; border-bottom: 1px solid var(--border); margin-bottom: 20px; }
                .logo { font-size: 20px; font-weight: 700; color: var(--primary); display: flex; align-items: center; gap: 8px; }
                .badge { background: #064e3b; color: #34d399; font-size: 11px; padding: 4px 8px; border-radius: 12px; font-weight: 600; }
                .card { background: var(--card); border: 1px solid var(--border); border-radius: 14px; padding: 18px; margin-bottom: 20px; }
                h2 { font-size: 16px; font-weight: 600; margin-bottom: 12px; color: var(--text); }
                textarea, input { width: 100%; padding: 12px; border-radius: 8px; border: 1px solid var(--border); background: #0c1322; color: #fff; font-size: 14px; resize: none; margin-bottom: 12px; outline: none; }
                textarea:focus, input:focus { border-color: var(--primary); }
                .btn { display: inline-flex; align-items: center; justify-content: center; width: 100%; padding: 12px; border-radius: 8px; border: none; background: var(--primary); color: #0f172a; font-weight: 600; font-size: 15px; cursor: pointer; transition: 0.2s; }
                .btn:active { background: var(--primary-hover); transform: scale(0.98); }
                .item-card { background: #0f172a; border: 1px solid var(--border); border-radius: 10px; padding: 14px; margin-bottom: 12px; }
                .item-header { display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 14px; font-weight: 600; }
                .item-stats { display: flex; justify-content: space-between; font-size: 12px; color: var(--text-sec); margin-top: 6px; }
                .progress-bar-bg { width: 100%; height: 6px; background: #1e293b; border-radius: 3px; overflow: hidden; margin-top: 8px; }
                .progress-bar-fill { height: 100%; background: var(--primary); transition: width 0.3s; }
                .actions { display: flex; gap: 8px; margin-top: 10px; }
                .btn-sm { flex: 1; padding: 6px; border-radius: 6px; border: 1px solid var(--border); background: #1e293b; color: #fff; font-size: 12px; cursor: pointer; }
                .quick-btns { display: flex; gap: 8px; margin-top: 8px; }
                .quick-btn { flex: 1; padding: 6px 8px; font-size: 11px; border: 1px solid var(--border); border-radius: 6px; background: #111a2d; color: var(--text-sec); cursor: pointer; }
            </style>
        </head>
        <body>
            <div class="container">
                <header>
                    <div class="logo">⚡ TV NAS Pro</div>
                    <div class="badge">Connected to $ip:8080</div>
                </header>

                <div class="card">
                    <h2>Push Download to TV</h2>
                    <textarea id="urlInput" rows="3" placeholder="Paste Magnet URI or Direct HTTP/HTTPS Link..."></textarea>
                    <button class="btn" onclick="sendDownload()">📥 Send to TV NAS</button>
                    <div class="quick-btns">
                        <button class="quick-btn" onclick="fillSample(1)">+ Big Buck Bunny (1080p MP4)</button>
                        <button class="quick-btn" onclick="fillSample(2)">+ Ubuntu Server Torrent</button>
                    </div>
                </div>

                <div class="card">
                    <h2>Live TV Downloads</h2>
                    <div id="downloadsList">Loading status from TV...</div>
                </div>
            </div>

            <script>
                function fillSample(type) {
                    if (type === 1) {
                        document.getElementById('urlInput').value = 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4';
                    } else {
                        document.getElementById('urlInput').value = 'magnet:?xt=urn:btih:205c080b0cf7446ab05e045ab5c7b949ba3f07a5&dn=Ubuntu_24_04_Server_x64.iso';
                    }
                }

                async function sendDownload() {
                    const url = document.getElementById('urlInput').value.trim();
                    if (!url) return alert('Please paste a download or magnet link');
                    try {
                        const res = await fetch('/api/add?url=' + encodeURIComponent(url));
                        const json = await res.json();
                        alert('Queued on TV: ' + json.file);
                        document.getElementById('urlInput').value = '';
                        loadStatus();
                    } catch (e) {
                        alert('Error sending to TV: ' + e);
                    }
                }

                async function doAction(id, action) {
                    await fetch('/api/action?id=' + id + '&action=' + action);
                    loadStatus();
                }

                async function loadStatus() {
                    try {
                        const res = await fetch('/api/status');
                        const data = await res.json();
                        const list = document.getElementById('downloadsList');
                        if (!data.downloads || data.downloads.length === 0) {
                            list.innerHTML = '<div style="color:#64748b; font-size:13px; text-align:center; padding:12px;">No active downloads on TV. Paste a link above!</div>';
                            return;
                        }
                        list.innerHTML = data.downloads.map(function(d) {
                            var pct = Math.round(d.progress * 100);
                            var icon = d.isMagnet ? '🧲 ' : '📥 ';
                            var actions = '';
                            if (d.status === 'DOWNLOADING') {
                                actions += '<button class="btn-sm" onclick="doAction(\'' + d.id + '\',\'pause\')">⏸ Pause</button>';
                            } else if (d.status === 'PAUSED') {
                                actions += '<button class="btn-sm" onclick="doAction(\'' + d.id + '\',\'resume\')">▶ Resume</button>';
                            }
                            actions += '<button class="btn-sm" style="color:#f87171;" onclick="doAction(\'' + d.id + '\',\'cancel\')">✕ Cancel</button>';

                            return '<div class="item-card">' +
                                '<div class="item-header">' +
                                '<span style="overflow:hidden; text-overflow:ellipsis; white-space:nowrap; max-width:240px;">' + icon + d.fileName + '</span>' +
                                '<span style="color:var(--primary); font-size:12px;">' + d.status + '</span>' +
                                '</div>' +
                                '<div class="progress-bar-bg">' +
                                '<div class="progress-bar-fill" style="width:' + pct + '%"></div>' +
                                '</div>' +
                                '<div class="item-stats">' +
                                '<span>' + d.downloaded + ' / ' + d.total + ' (' + pct + '%)</span>' +
                                '<span>⚡ ' + d.speed + '</span>' +
                                '</div>' +
                                '<div class="actions">' + actions + '</div>' +
                                '</div>';
                        }).join('');
                    } catch (e) {
                        console.error(e);
                    }
                }
                loadStatus();
                setInterval(loadStatus, 2000);
            </script>
        </body>
        </html>
        """.trimIndent()

        sendResponse(out, 200, "text/html; charset=utf-8", html)
    }

    private fun sendResponse(out: OutputStream, code: Int, contentType: String, content: String) {
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        val statusText = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "Response"
        }

        val header = "HTTP/1.1 $code $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"

        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val pairs = query.split("&")
        for (pair in pairs) {
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                try {
                    val key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8.name())
                    val value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name())
                    map[key] = value
                } catch (e: Exception) {
                    map[parts[0]] = parts[1]
                }
            }
        }
        return map
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
