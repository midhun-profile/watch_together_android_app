package com.example

import com.example.player.PlayerState
import com.example.utils.Formatters
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun testFormatDuration() {
        assertEquals("00:00", Formatters.formatDuration(0))
        assertEquals("01:05", Formatters.formatDuration(65_000))
        assertEquals("01:01:05", Formatters.formatDuration(3_665_000))
    }

    @Test
    fun testFormatFileSize() {
        assertEquals("Unknown", Formatters.formatFileSize(0))
        assertEquals("500.0 MB", Formatters.formatFileSize(500L * 1024 * 1024))
        assertEquals("2.00 GB", Formatters.formatFileSize(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun testPlayerStateProgress() {
        val state = PlayerState(
            currentPositionMs = 30_000L,
            durationMs = 60_000L
        )
        assertEquals(0.5f, state.progress, 0.001f)
    }
}
