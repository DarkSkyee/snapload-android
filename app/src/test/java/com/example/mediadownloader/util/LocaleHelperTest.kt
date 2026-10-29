package com.example.mediadownloader.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Locale

class LocaleHelperTest {

    @Test
    fun localeForLanguageTag_enResolvesCorrectly() {
        val locale = Locale.forLanguageTag("en")
        assertEquals("en", locale.language)
        assertNotNull(locale)
    }

    @Test
    fun localeForLanguageTag_plResolvesCorrectly() {
        val locale = Locale.forLanguageTag("pl")
        assertEquals("pl", locale.language)
        assertNotNull(locale)
    }
}
