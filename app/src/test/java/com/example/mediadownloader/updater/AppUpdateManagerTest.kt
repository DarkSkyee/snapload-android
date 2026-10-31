package com.example.mediadownloader.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun isNewerVersion_detectsNewerMajorMinorPatch() {
        // Higher minor version
        assertTrue(AppUpdateManager.isNewerVersion("1.1", "1.0"))
        assertTrue(AppUpdateManager.isNewerVersion("1.0.1", "1.0"))
        assertTrue(AppUpdateManager.isNewerVersion("2.0", "1.9.9"))

        // Multi-digit versions
        assertTrue(AppUpdateManager.isNewerVersion("1.10.0", "1.9.0"))
        assertTrue(AppUpdateManager.isNewerVersion("3.1.2", "3.1.1"))
        assertTrue(AppUpdateManager.isNewerVersion("3.1.2", "3.1.0"))

        // With 'v' prefix
        assertTrue(AppUpdateManager.isNewerVersion("v1.2", "1.1"))
        assertTrue(AppUpdateManager.isNewerVersion("V2.0", "v1.9"))
        assertTrue(AppUpdateManager.isNewerVersion("v3.1.2", "v3.1.1"))

        // With suffix (e.g. beta, rc)
        assertTrue(AppUpdateManager.isNewerVersion("1.1-beta", "1.0"))
        assertTrue(AppUpdateManager.isNewerVersion("2.0.0-rc1", "1.9.9"))

        // Equal version
        assertFalse(AppUpdateManager.isNewerVersion("1.0", "1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.0", "1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("3.1.2", "3.1.2"))

        // Older version
        assertFalse(AppUpdateManager.isNewerVersion("0.9", "1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0", "1.1"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.1", "1.0.2"))
        assertFalse(AppUpdateManager.isNewerVersion("1.9.0", "1.10.0"))
        assertFalse(AppUpdateManager.isNewerVersion("3.1.1", "3.1.2"))
    }

    @Test
    fun cleanVersionTag_handlesPrefixesAndWhitespace() {
        assertEquals("3.0.8", AppUpdateManager.cleanVersionTag("v3.0.8"))
        assertEquals("3.0.8", AppUpdateManager.cleanVersionTag("V3.0.8"))
        assertEquals("3.0.8", AppUpdateManager.cleanVersionTag("  3.0.8  "))
        assertEquals("3.1.2", AppUpdateManager.cleanVersionTag("v3.1.2"))
        assertEquals("3.1.2", AppUpdateManager.cleanVersionTag("  v3.1.2  "))
        assertEquals("3.1.2-beta1", AppUpdateManager.cleanVersionTag("  v3.1.2-beta1  "))
        assertEquals("3.1.2", AppUpdateManager.cleanVersionTag("V  3.1.2".replace(" ", "")))
    }

    @Test
    fun isNewerVersion_handlesComplexSuffixes() {
        // Semantic release comparisons
        assertTrue(AppUpdateManager.isNewerVersion("3.1.3-patch1", "3.1.2"))
        assertTrue(AppUpdateManager.isNewerVersion("v3.2.0-alpha", "3.1.9"))
        assertFalse(AppUpdateManager.isNewerVersion("v3.1.2-final", "3.1.2"))

        // Differing component lengths
        assertTrue(AppUpdateManager.isNewerVersion("3.1.2.1", "3.1.2"))
        assertFalse(AppUpdateManager.isNewerVersion("3.1.2", "3.1.2.1"))
        assertFalse(AppUpdateManager.isNewerVersion("3.1.2.0", "3.1.2"))

        // Leading zeroes
        assertFalse(AppUpdateManager.isNewerVersion("03.01.02", "3.1.2"))
        assertTrue(AppUpdateManager.isNewerVersion("03.01.03", "3.1.2"))

        // Empty / malformed inputs
        assertFalse(AppUpdateManager.isNewerVersion("", "3.1.2"))
        assertFalse(AppUpdateManager.isNewerVersion("invalid", "3.1.2"))
        assertFalse(AppUpdateManager.isNewerVersion("", ""))
    }
}
