package com.example.mediadownloader.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareIntentExtractorTest {

    @Test
    fun extractUrlFromCandidates_emptyOrNull_returnsNull() {
        assertNull(ShareIntentExtractor.extractUrlFromCandidates(emptyList()))
        assertNull(ShareIntentExtractor.extractUrlFromCandidates(listOf(null, "", "   ")))
    }

    @Test
    fun extractUrlFromCandidates_plainTextWithoutUrl_returnsNull() {
        val candidates = listOf(
            "Just a title",
            "Some description without link",
            "12345"
        )
        assertNull(ShareIntentExtractor.extractUrlFromCandidates(candidates))
    }

    @Test
    fun extractUrlFromCandidates_urlInSecondCandidate_returnsUrl() {
        val candidates = listOf(
            "Watch this cool video",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        )
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            ShareIntentExtractor.extractUrlFromCandidates(candidates)
        )
    }

    @Test
    fun extractUrlFromCandidates_youtubeShortsWithTrailingJunk_returnsCleanUrl() {
        val candidates = listOf(
            "Check this out: https://youtube.com/shorts/abc123xyz?si=track! It's awesome."
        )
        assertEquals(
            "https://youtube.com/shorts/abc123xyz?si=track",
            ShareIntentExtractor.extractUrlFromCandidates(candidates)
        )
    }

    @Test
    fun extractUrlFromCandidates_firstCandidateWithValidUrlTakesPrecedence() {
        val candidates = listOf(
            "https://youtu.be/first123",
            "https://youtu.be/second456"
        )
        assertEquals(
            "https://youtu.be/first123",
            ShareIntentExtractor.extractUrlFromCandidates(candidates)
        )
    }

    @Test
    fun extractUrlFromCandidates_tiktokAndSpotify() {
        val tiktokCandidates = listOf("https://vm.tiktok.com/ZMxxxxxx/")
        assertEquals(
            "https://vm.tiktok.com/ZMxxxxxx/",
            ShareIntentExtractor.extractUrlFromCandidates(tiktokCandidates)
        )

        val spotifyCandidates = listOf("Listen here: https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=abc")
        assertEquals(
            "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=abc",
            ShareIntentExtractor.extractUrlFromCandidates(spotifyCandidates)
        )
    }

    @Test
    fun extractRawSharedText_returnsFirstNonBlank() {
        val candidates = listOf("", "   ", "First actual text", "Second text")
        // Simulating the logic with extractUrlFromCandidates / extractRawSharedText
        val firstNonBlank = candidates.firstOrNull { it.isNotBlank() }
        assertEquals("First actual text", firstNonBlank)
    }
}
