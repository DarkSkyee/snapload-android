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
        val utf8Bytes = sanitized.toByteArray(Charsets.UTF_8).size
        org.junit.Assert.assertTrue("Expected <= 150 bytes, got $utf8Bytes", utf8Bytes <= 150)
        assertEquals("A".repeat(150), sanitized)
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
        val utf8Bytes = sanitized.toByteArray(Charsets.UTF_8).size
        org.junit.Assert.assertTrue("Expected <= 150 bytes, got $utf8Bytes", utf8Bytes <= 150)
        org.junit.Assert.assertFalse(sanitized.endsWith("."))
        org.junit.Assert.assertFalse(sanitized.endsWith(" "))
    }

    @Test
    fun sanitizeFileName_truncatesEmojisSafely_neverSplitsSurrogatePairs() {
        val emojiTitle = "🎬🎵🔥🚀✨🎉".repeat(15) // Multi-byte UTF-8 4-byte emojis
        val sanitized = MediaStoreSaver.sanitizeFileName(emojiTitle)
        val utf8Bytes = sanitized.toByteArray(Charsets.UTF_8).size
        org.junit.Assert.assertTrue("Expected <= 150 bytes, got $utf8Bytes", utf8Bytes <= 150)
        // Ensure no lone high surrogate is left at the end
        org.junit.Assert.assertFalse(Character.isHighSurrogate(sanitized.last()))
    }

    @Test
    fun sanitizeFileName_stripsControlAndNullCharacters() {
        val dirtyTitle = "Cool\u0000Video\r\nTitle\tWith|Bad*Chars?"
        val sanitized = MediaStoreSaver.sanitizeFileName(dirtyTitle)
        org.junit.Assert.assertFalse(sanitized.contains('\u0000'))
        org.junit.Assert.assertFalse(sanitized.contains('\r'))
        org.junit.Assert.assertFalse(sanitized.contains('\n'))
        org.junit.Assert.assertFalse(sanitized.contains('\t'))
        org.junit.Assert.assertFalse(sanitized.contains('|'))
        org.junit.Assert.assertFalse(sanitized.contains('*'))
        org.junit.Assert.assertFalse(sanitized.contains('?'))
    }
}
