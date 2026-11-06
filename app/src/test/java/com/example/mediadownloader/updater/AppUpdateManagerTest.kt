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
        assertEquals("3.2.0", AppUpdateManager.cleanVersionTag("SnapLoad v3.2.0"))
        assertEquals("3.2.1-alpha", AppUpdateManager.cleanVersionTag("SnapLoad-v3.2.1-alpha"))
        assertEquals("3.2.0", AppUpdateManager.cleanVersionTag("SnapLoad 3.2.0"))
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

    @Test
    fun isNewerVersion_handlesAlphaAndPrereleaseTransitions() {
        // Stable release is newer than pre-release of the same version (SemVer 1.0.0-alpha < 1.0.0)
        assertTrue(AppUpdateManager.isNewerVersion("3.2.0", "3.2.0-alpha"))
        assertTrue(AppUpdateManager.isNewerVersion("v3.2.0", "v3.2.0-alpha.1"))

        // Pre-release is NOT newer than stable release of the same version
        assertFalse(AppUpdateManager.isNewerVersion("3.2.0-alpha", "3.2.0"))
        assertFalse(AppUpdateManager.isNewerVersion("v3.2.0-alpha.1", "v3.2.0"))

        // Higher version alpha is newer than older stable
        assertTrue(AppUpdateManager.isNewerVersion("3.2.0-alpha", "3.1.2"))
        assertTrue(AppUpdateManager.isNewerVersion("v3.2.0-alpha.1", "3.1.2"))

        // Newer alpha over older alpha of the same base
        assertTrue(AppUpdateManager.isNewerVersion("3.2.0-alpha.2", "3.2.0-alpha.1"))
        assertTrue(AppUpdateManager.isNewerVersion("v3.2.0-alpha2", "v3.2.0-alpha1"))
        assertFalse(AppUpdateManager.isNewerVersion("3.2.0-alpha.1", "3.2.0-alpha.2"))

        // Higher base version alpha over lower base version alpha
        assertTrue(AppUpdateManager.isNewerVersion("3.3.0-alpha", "3.2.0-alpha"))
        assertFalse(AppUpdateManager.isNewerVersion("3.2.0-alpha", "3.3.0-alpha"))

        // Equal alphas
        assertFalse(AppUpdateManager.isNewerVersion("3.2.0-alpha", "3.2.0-alpha"))
        assertFalse(AppUpdateManager.isNewerVersion("v3.2.0-alpha.1", "3.2.0-alpha.1"))
    }

    @Test
    fun updateChannel_fromKey_parsesCorrectly() {
        assertEquals(UpdateChannel.ALPHA, UpdateChannel.fromKey("alpha"))
        assertEquals(UpdateChannel.ALPHA, UpdateChannel.fromKey("ALPHA"))
        assertEquals(UpdateChannel.ALPHA, UpdateChannel.fromKey("  Alpha  "))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromKey("stable"))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromKey("main"))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromKey(null))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromKey(""))
    }

    @Test
    fun isAlphaRelease_detectsAlphaReleases() {
        val alphaJson1 = org.json.JSONObject().apply {
            put("prerelease", true)
            put("tag_name", "v3.2.0")
        }
        assertTrue(AppUpdateManager.isAlphaRelease(alphaJson1))

        val alphaJson2 = org.json.JSONObject().apply {
            put("prerelease", false)
            put("target_commitish", "alpha")
            put("tag_name", "v3.2.0")
        }
        assertTrue(AppUpdateManager.isAlphaRelease(alphaJson2))

        val alphaJson3 = org.json.JSONObject().apply {
            put("prerelease", false)
            put("tag_name", "v3.2.0-alpha")
        }
        assertTrue(AppUpdateManager.isAlphaRelease(alphaJson3))

        val alphaJson4 = org.json.JSONObject().apply {
            put("prerelease", false)
            put("tag_name", "v3.2.0")
            put("name", "SnapLoad v3.2.0 Alpha Preview")
        }
        assertTrue(AppUpdateManager.isAlphaRelease(alphaJson4))

        val stableJson = org.json.JSONObject().apply {
            put("prerelease", false)
            put("target_commitish", "main")
            put("tag_name", "v3.2.0")
            put("name", "SnapLoad v3.2.0")
        }
        assertFalse(AppUpdateManager.isAlphaRelease(stableJson))
    }

    @Test
    fun hasApkAsset_detectsApkPresence() {
        val releaseWithApk = org.json.JSONObject().apply {
            put("assets", org.json.JSONArray().apply {
                put(org.json.JSONObject().apply {
                    put("name", "source.tar.gz")
                })
                put(org.json.JSONObject().apply {
                    put("name", "SnapLoad-v3.2.0.apk")
                    put("browser_download_url", "https://github.com/apk")
                })
            })
        }
        assertTrue(AppUpdateManager.hasApkAsset(releaseWithApk))

        val releaseWithoutApk = org.json.JSONObject().apply {
            put("assets", org.json.JSONArray().apply {
                put(org.json.JSONObject().apply {
                    put("name", "source.tar.gz")
                })
            })
        }
        assertFalse(AppUpdateManager.hasApkAsset(releaseWithoutApk))

        val releaseWithNoAssets = org.json.JSONObject()
        assertFalse(AppUpdateManager.hasApkAsset(releaseWithNoAssets))
    }
}
