package com.example.mediadownloader.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkParserTest {

    @Test
    fun extractUrl_fromEmptyOrNullString() {
        assertNull(LinkParser.extractUrl(null))
        assertNull(LinkParser.extractUrl(""))
        assertNull(LinkParser.extractUrl("   "))
    }

    @Test
    fun extractUrl_fromPlainTextWithoutUrl() {
        assertNull(LinkParser.extractUrl("Hello this is just a regular sentence"))
    }

    @Test
    fun extractUrl_cleanUrl() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertEquals(url, LinkParser.extractUrl(url))
    }

    @Test
    fun extractUrl_fromYoutubeShortsSharedText() {
        val sharedText = "Check out this video: https://youtube.com/shorts/dQw4w9WgXcQ?si=abc123! It is awesome"
        assertEquals("https://youtube.com/shorts/dQw4w9WgXcQ?si=abc123", LinkParser.extractUrl(sharedText))
    }

    @Test
    fun extractUrl_fromTikTokMobileShare() {
        val sharedText = "Look at this TikTok: https://vm.tiktok.com/ZMxxxxxx/."
        assertEquals("https://vm.tiktok.com/ZMxxxxxx/", LinkParser.extractUrl(sharedText))
    }

    @Test
    fun extractUrl_fromParenthesizedUrl() {
        val sharedText = "Check this out (https://youtube.com/shorts/dQw4w9WgXcQ)"
        assertEquals("https://youtube.com/shorts/dQw4w9WgXcQ", LinkParser.extractUrl(sharedText))
    }

    @Test
    fun extractUrl_fromQuotedUrl() {
        val sharedText = "Watch: \"https://vm.tiktok.com/ZMxxxxxx/\""
        assertEquals("https://vm.tiktok.com/ZMxxxxxx/", LinkParser.extractUrl(sharedText))
    }

    @Test
    fun detectPlatform_youtubeVariants() {
        assertEquals(LinkParser.Platform.YOUTUBE, LinkParser.detectPlatform("https://www.youtube.com/watch?v=123"))
        assertEquals(LinkParser.Platform.YOUTUBE, LinkParser.detectPlatform("https://youtu.be/123"))
        assertEquals(LinkParser.Platform.YOUTUBE, LinkParser.detectPlatform("https://youtube.com/shorts/123"))
    }

    @Test
    fun detectPlatform_tiktokVariants() {
        assertEquals(LinkParser.Platform.TIKTOK, LinkParser.detectPlatform("https://www.tiktok.com/@user/video/12345"))
        assertEquals(LinkParser.Platform.TIKTOK, LinkParser.detectPlatform("https://vm.tiktok.com/ZMxxxx/"))
        assertEquals(LinkParser.Platform.TIKTOK, LinkParser.detectPlatform("https://vt.tiktok.com/ZSxxxx/"))
    }

    @Test
    fun detectPlatform_youtubeMusicVariants() {
        assertEquals(LinkParser.Platform.YOUTUBE_MUSIC, LinkParser.detectPlatform("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(LinkParser.Platform.YOUTUBE_MUSIC, LinkParser.detectPlatform("https://music.youtube.com/playlist?list=PL12345"))
    }

    @Test
    fun detectPlatform_spotifyVariants() {
        assertEquals(LinkParser.Platform.SPOTIFY, LinkParser.detectPlatform("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT"))
        assertEquals(LinkParser.Platform.SPOTIFY, LinkParser.detectPlatform("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"))
        assertEquals(LinkParser.Platform.SPOTIFY, LinkParser.detectPlatform("https://open.spotify.com/album/4LH4d3cOWNNXdsqFd4G71m"))
        assertEquals(LinkParser.Platform.SPOTIFY, LinkParser.detectPlatform("https://spotify.link/abc123xyz"))
    }

    @Test
    fun isPlaylist_detection() {
        assertTrue(LinkParser.isPlaylist("https://www.youtube.com/playlist?list=PL1234567890"))
        assertTrue(LinkParser.isPlaylist("https://www.youtube.com/watch?v=123&list=PL1234567890"))
        assertTrue(LinkParser.isPlaylist("https://music.youtube.com/playlist?list=PL1234567890"))
        assertTrue(LinkParser.isPlaylist("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"))
        assertTrue(LinkParser.isPlaylist("https://open.spotify.com/album/4LH4d3cOWNNXdsqFd4G71m"))
        assertFalse(LinkParser.isPlaylist("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(LinkParser.isPlaylist("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT"))
    }

    @Test
    fun isSupported_validPlatforms() {
        assertTrue(LinkParser.isSupported("https://www.youtube.com/watch?v=123"))
        assertTrue(LinkParser.isSupported("https://vm.tiktok.com/ZMxxxx/"))
        assertTrue(LinkParser.isSupported("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT"))
        assertTrue(LinkParser.isSupported("https://music.youtube.com/watch?v=123"))
        assertFalse(LinkParser.isSupported("not a url"))
        assertFalse(LinkParser.isSupported(null))
    }
}
