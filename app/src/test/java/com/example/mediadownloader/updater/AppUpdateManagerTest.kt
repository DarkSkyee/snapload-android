package com.example.mediadownloader.updater

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

        // With 'v' prefix
        assertTrue(AppUpdateManager.isNewerVersion("v1.2", "1.1"))
        assertTrue(AppUpdateManager.isNewerVersion("V2.0", "v1.9"))

        // With suffix (e.g. beta, rc)
        assertTrue(AppUpdateManager.isNewerVersion("1.1-beta", "1.0"))
        assertTrue(AppUpdateManager.isNewerVersion("2.0.0-rc1", "1.9.9"))

        // Equal version
        assertFalse(AppUpdateManager.isNewerVersion("1.0", "1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.0", "1.0"))

        // Older version
        assertFalse(AppUpdateManager.isNewerVersion("0.9", "1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0", "1.1"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.1", "1.0.2"))
        assertFalse(AppUpdateManager.isNewerVersion("1.9.0", "1.10.0"))
    }
}
