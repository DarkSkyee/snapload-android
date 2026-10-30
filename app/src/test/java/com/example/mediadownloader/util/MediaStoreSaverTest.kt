package com.example.mediadownloader.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaStoreSaverTest {

    @Test
    fun sanitizeFileName_removesForbiddenCharacters() {
        val input = "Video: What? \"Awesome\" / <Cool> | Test * 100%"
        val expected = "Video_ What_ _Awesome_ _ _Cool_ _ Test _ 100%"
        assertEquals(expected, MediaStoreSaver.sanitizeFileName(input))
    }

    @Test
    fun sanitizeFileName_emptyOrBlankUsesFallback() {
        assertEquals("default_name", MediaStoreSaver.sanitizeFileName("", "default_name"))
        assertEquals("default_name", MediaStoreSaver.sanitizeFileName("   ", "default_name"))
    }

    @Test
    fun sanitizeFileName_normalNameUnchanged() {
        val input = "TikTok Dance 2026"
        assertEquals(input, MediaStoreSaver.sanitizeFileName(input))
    }

    @Test
    fun sanitizeFileName_handlesPolishCharactersAndEmojis() {
        val input = "Zażółć gęślą jaźń 🎬🎵"
        assertEquals("Zażółć gęślą jaźń 🎬🎵", MediaStoreSaver.sanitizeFileName(input))
    }

    @Test
    fun sanitizeFileName_collapsesConsecutiveWhitespace() {
        val input = "My     Epic      Shorts"
        assertEquals("My Epic Shorts", MediaStoreSaver.sanitizeFileName(input))
    }

    @Test
    fun resolveMimeType_mapsAllVideoAndAudioFormats() {
        // Video formats
        assertEquals("video/mp4", MediaStoreSaver.resolveMimeType("mp4"))
        assertEquals("video/mp4", MediaStoreSaver.resolveMimeType("MP4"))
        assertEquals("video/x-matroska", MediaStoreSaver.resolveMimeType("mkv"))
        assertEquals("video/webm", MediaStoreSaver.resolveMimeType("webm"))

        // Audio formats
        assertEquals("audio/mpeg", MediaStoreSaver.resolveMimeType("mp3"))
        assertEquals("audio/mp4", MediaStoreSaver.resolveMimeType("m4a"))
        assertEquals("audio/flac", MediaStoreSaver.resolveMimeType("flac"))
        assertEquals("audio/x-wav", MediaStoreSaver.resolveMimeType("wav"))
        assertEquals("audio/opus", MediaStoreSaver.resolveMimeType("opus"))

        // Fallback
        assertEquals("application/octet-stream", MediaStoreSaver.resolveMimeType("xyz"))
    }

    @Test
    fun sanitizeFileName_truncatesExcessivelyLongTitles() {
        val longTitle = "A".repeat(300)
        val sanitized = MediaStoreSaver.sanitizeFileName(longTitle)
        assertEquals(120, sanitized.length)
        assertEquals("A".repeat(120), sanitized)
    }

    @Test
    fun sanitizeFileName_trimsTrailingDotsAndSpaces() {
        val titleWithDots = "Interesting Video Title...."
        val sanitized = MediaStoreSaver.sanitizeFileName(titleWithDots)
        assertEquals("Interesting Video Title", sanitized)
    }

    @Test
    fun sanitizeFileName_truncatesLongPolishTitleSafely() {
        val longPolish = "Zażółć gęślą jaźń ".repeat(20)
        val sanitized = MediaStoreSaver.sanitizeFileName(longPolish)
        org.junit.Assert.assertTrue(sanitized.length <= 120)
        org.junit.Assert.assertFalse(sanitized.endsWith("."))
    }
}
