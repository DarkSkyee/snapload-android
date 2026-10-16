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
}
