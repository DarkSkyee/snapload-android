package com.example.mediadownloader.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMatcherTest {

    @Test
    fun cleanTitleForSearch_removesFeaturedArtistsAndModifiers() {
        val title1 = "TESTO (feat. Majki)"
        assertEquals("TESTO", TrackMatcher.cleanTitleForSearch(title1))

        val title2 = "TESTO [feat. Majki, Funky Beatz]"
        assertEquals("TESTO", TrackMatcher.cleanTitleForSearch(title2))

        val title3 = "TESTO (Official Video)"
        assertEquals("TESTO", TrackMatcher.cleanTitleForSearch(title3))

        val title4 = "Major SPZ - \"TESTO\""
        assertEquals("Major SPZ - TESTO", TrackMatcher.cleanTitleForSearch(title4))
    }

    @Test
    fun cleanArtistForSearch_removesPunctuation() {
        val artist = "Major SPZ, \"The Producer\""
        assertEquals("Major SPZ The Producer", TrackMatcher.cleanArtistForSearch(artist))
    }

    @Test
    fun scoreCandidate_differentiatesTargetSongFromWrongSong() {
        val targetTitle = "TESTO"
        val artists = listOf("Major SPZ", "Majki", "Funky Beatz")
        val targetDuration = 121L

        val correctCandidate = CandidateVideo(
            id = "b5MQUiVB9Fo",
            title = "Major SPZ  - TESTO\"  ft.Majki",
            uploader = "Major SPZ",
            durationSeconds = 121L
        )

        val wrongCandidateJepJep = CandidateVideo(
            id = "RPcGUMvk3bg",
            title = "Major SPZ x Majki - Jep Jep Jep  ft.Diho (Official Video)",
            uploader = "Major SPZ",
            durationSeconds = 165L
        )

        val remixCandidate = CandidateVideo(
            id = "x_7TX9GOhWI",
            title = "MAJOR SPZ X MAJKI - TESTO ALE TO VIXA",
            uploader = "Vixa Channel",
            durationSeconds = 154L
        )

        val correctScore = TrackMatcher.scoreCandidate(correctCandidate, targetTitle, artists, targetDuration)
        val wrongScore = TrackMatcher.scoreCandidate(wrongCandidateJepJep, targetTitle, artists, targetDuration)
        val remixScore = TrackMatcher.scoreCandidate(remixCandidate, targetTitle, artists, targetDuration)

        assertTrue("Correct track score should be high ($correctScore)", correctScore >= 120.0)
        assertTrue("Wrong track 'Jep Jep Jep' should score negative ($wrongScore)", wrongScore < 0.0)
        assertTrue("Correct candidate should beat remix candidate ($correctScore vs $remixScore)", correctScore > remixScore)
    }

    @Test
    fun findBestCandidate_selectsCorrectCandidate() {
        val targetTitle = "TESTO"
        val artists = listOf("Major SPZ", "Majki", "Funky Beatz")
        val targetDuration = 121L

        val candidates = listOf(
            CandidateVideo("RPcGUMvk3bg", "Major SPZ x Majki - Jep Jep Jep  ft.Diho (Official Video)", "Major SPZ", 165L),
            CandidateVideo("N0cGVaG5i_4", "Majki x Kumi ft. Major SPZ - Uno Momento [Official Video]", "Majki", 138L),
            CandidateVideo("b5MQUiVB9Fo", "Major SPZ  - TESTO\"  ft.Majki", "Major SPZ", 121L),
            CandidateVideo("x_7TX9GOhWI", "MAJOR SPZ X MAJKI - TESTO ALE TO VIXA", "Major SPZ", 154L)
        )

        val best = TrackMatcher.findBestCandidate(candidates, targetTitle, artists, targetDuration)
        assertNotNull(best)
        assertEquals("b5MQUiVB9Fo", best?.id)
        assertEquals("Major SPZ  - TESTO\"  ft.Majki", best?.title)
    }

    @Test
    fun findBestCandidate_returnsNullWhenAllScoresBelowThreshold() {
        val candidates = listOf(
            CandidateVideo("RPcGUMvk3bg", "Major SPZ x Majki - Jep Jep Jep  ft.Diho (Official Video)", "Major SPZ", 165L),
            CandidateVideo("N0cGVaG5i_4", "Majki x Kumi ft. Major SPZ - Uno Momento [Official Video]", "Majki", 138L)
        )

        val best = TrackMatcher.findBestCandidate(candidates, "TESTO", listOf("Major SPZ"), 121L)
        assertNull("Should reject candidates when none match title", best)
    }

    @Test
    fun parseCandidates_extractsFromJsonOutput() {
        val jsonOutput = """
            {
              "entries": [
                {
                  "id": "b5MQUiVB9Fo",
                  "title": "Major SPZ - TESTO",
                  "uploader": "Major SPZ",
                  "duration": 121
                },
                {
                  "id": "x_7TX9GOhWI",
                  "title": "MAJOR SPZ X MAJKI - TESTO ALE TO VIXA",
                  "uploader": "Vixa Channel",
                  "duration": 154
                }
              ]
            }
        """.trimIndent()

        val parsed = TrackMatcher.parseCandidates(jsonOutput)
        assertEquals(2, parsed.size)
        assertEquals("b5MQUiVB9Fo", parsed[0].id)
        assertEquals(121L, parsed[0].durationSeconds)
        assertEquals("Major SPZ", parsed[0].uploader)
    }

    @Test
    fun buildSearchQuery_formatsCleanQueryWithoutCommasOrAudioKeyword() {
        val query = SpotifyHelper.buildSearchQuery(
            title = "TESTO",
            artists = listOf("Major SPZ", "Majki", "Funky Beatz"),
            artist = "Major SPZ, Majki, Funky Beatz"
        )

        assertEquals("ytsearch1:Major SPZ Majki TESTO", query)
        assertTrue(!query.contains(","))
        assertTrue(!query.contains("audio"))
    }

    @Test
    fun scoreCandidate_handlesZeroTargetDuration() {
        val targetTitle = "Blinding Lights"
        val artists = listOf("The Weeknd")

        val match = CandidateVideo("id1", "The Weeknd - Blinding Lights (Official Audio)", "The Weeknd", 204L)
        val mismatch = CandidateVideo("id2", "The Weeknd - Starboy", "The Weeknd", 230L)

        val matchScore = TrackMatcher.scoreCandidate(match, targetTitle, artists, 0L)
        val mismatchScore = TrackMatcher.scoreCandidate(mismatch, targetTitle, artists, 0L)

        assertTrue("Should have high score even without duration ($matchScore)", matchScore >= 80.0)
        assertTrue("Mismatch should have negative score ($mismatchScore)", mismatchScore < 0.0)
    }

    @Test
    fun scoreCandidate_penalizesInstrumentalAndAcousticWhenNotTarget() {
        val targetTitle = "Szef Piekarni"
        val artists = listOf("Majki", "Diho")

        val original = CandidateVideo("id1", "Majki x Diho - \"Szef Piekarni\"", "Majki", 119L)
        val instrumental = CandidateVideo("id2", "Majki x Diho - \"Szef Piekarni\" (Instrumental)", "Majki", 119L)
        val acoustic = CandidateVideo("id3", "Majki x Diho - \"Szef Piekarni\" (Acoustic Version)", "Majki", 119L)

        val scoreOrig = TrackMatcher.scoreCandidate(original, targetTitle, artists, 119L)
        val scoreInst = TrackMatcher.scoreCandidate(instrumental, targetTitle, artists, 119L)
        val scoreAcoustic = TrackMatcher.scoreCandidate(acoustic, targetTitle, artists, 119L)

        assertTrue(scoreOrig > scoreInst)
        assertTrue(scoreOrig > scoreAcoustic)
    }

    @Test
    fun scoreCandidate_allowsRemixWhenTargetIsRemix() {
        val targetTitle = "Szef Piekarni (Wavzo Remix)"
        val artists = listOf("Majki", "Diho")

        val remixCand = CandidateVideo("id1", "Majki x Diho - Szef Piekarni (Wavzo Remix)", "Majki", 130L)
        val score = TrackMatcher.scoreCandidate(remixCand, targetTitle, artists, 130L)

        assertTrue("Remix candidate should score high when target itself is a remix ($score)", score >= 120.0)
    }

    @Test
    fun findBestCandidate_prefersCloserDuration() {
        val targetTitle = "TESTO"
        val artists = listOf("Major SPZ", "Majki")
        val targetDuration = 121L

        val candidates = listOf(
            CandidateVideo("extended", "Major SPZ - TESTO (Extended Cut)", "Major SPZ", 240L),
            CandidateVideo("exact", "Major SPZ - TESTO", "Major SPZ", 121L),
            CandidateVideo("short", "Major SPZ - TESTO (Snippet)", "Major SPZ", 45L)
        )

        val best = TrackMatcher.findBestCandidate(candidates, targetTitle, artists, targetDuration)
        assertEquals("exact", best?.id)
    }

    @Test
    fun cleanTitleForSearch_handlesPolishCharactersAndWhitespace() {
        val title = "  Życie na 100% (feat. Ktoś)  "
        val cleaned = TrackMatcher.cleanTitleForSearch(title)
        assertEquals("Życie na 100%", cleaned)
    }
}
