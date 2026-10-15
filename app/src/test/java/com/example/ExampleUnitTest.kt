package com.example

import com.example.downloader.MagnetParser
import com.example.storage.StorageManager
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testMagnetParser_parsesValidMagnet() {
        val magnet = "magnet:?xt=urn:btih:c12fe1c06bba254a9d64a52086cb33e50e474fb8&dn=Ubuntu+24.04+LTS&tr=http%3A%2F%2Ftracker.example.com%2Fannounce"
        assertTrue(MagnetParser.isMagnetLink(magnet))
        val parsed = MagnetParser.parse(magnet)
        assertEquals("c12fe1c06bba254a9d64a52086cb33e50e474fb8", parsed.infoHash)
        assertEquals("Ubuntu 24.04 LTS", parsed.displayName)
        assertEquals(1, parsed.trackers.size)
        assertEquals("http://tracker.example.com/announce", parsed.trackers.first())
    }

    @Test
    fun testFormatBytes() {
        assertEquals("0 B", StorageManager.formatBytes(0))
        assertEquals("1.5 KB", StorageManager.formatBytes(1536))
        assertEquals("100 MB", StorageManager.formatBytes(100L * 1024 * 1024))
        assertEquals("1.5 GB", StorageManager.formatBytes((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun testFormatSpeed() {
        assertEquals("0 KB/s", StorageManager.formatSpeed(0))
        assertEquals("500.0 KB/s", StorageManager.formatSpeed(500 * 1024))
        assertEquals("2.50 MB/s", StorageManager.formatSpeed((2.5 * 1024 * 1024).toLong()))
    }
}
