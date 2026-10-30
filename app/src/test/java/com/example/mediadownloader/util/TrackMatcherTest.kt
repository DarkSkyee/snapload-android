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

    @Test
    fun scoreCandidate_preventsSubstringWordFalsePositives() {
        val targetTitle = "Nie Ma Mnie"
        val artists = listOf("Major SPZ")
        val targetDuration = 180L

        // Candidate has "Nie", "Zapomnij", "Mnie". "Ma" is ONLY a substring of "Major", not a discrete word!
        val falseCandidate = CandidateVideo(
            id = "false_cand",
            title = "Major SPZ - Nie Zapomnij Mnie",
            uploader = "Major SPZ",
            durationSeconds = 180L
        )

        val exactCandidate = CandidateVideo(
            id = "exact_cand",
            title = "Major SPZ - Nie Ma Mnie",
            uploader = "Major SPZ",
            durationSeconds = 180L
        )

        val falseScore = TrackMatcher.scoreCandidate(falseCandidate, targetTitle, artists, targetDuration)
        val exactScore = TrackMatcher.scoreCandidate(exactCandidate, targetTitle, artists, targetDuration)

        assertTrue("Exact match score ($exactScore) must be significantly higher than partial ($falseScore)", exactScore > falseScore + 30.0)
        assertEquals(exactCandidate, TrackMatcher.findBestCandidate(listOf(falseCandidate, exactCandidate), targetTitle, artists, targetDuration))
    }

    @Test
    fun scoreCandidate_penalizesArtistMismatch() {
        val targetTitle = "Hello"
        val artists = listOf("Adele")
        val targetDuration = 295L

        val lionelRichie = CandidateVideo(
            id = "lionel",
            title = "Lionel Richie - Hello (Official Music Video)",
            uploader = "LionelRichieVEVO",
            durationSeconds = 295L
        )

        val adele = CandidateVideo(
            id = "adele",
            title = "Adele - Hello (Official Music Video)",
            uploader = "AdeleVEVO",
            durationSeconds = 295L
        )

        val lionelScore = TrackMatcher.scoreCandidate(lionelRichie, targetTitle, artists, targetDuration)
        val adeleScore = TrackMatcher.scoreCandidate(adele, targetTitle, artists, targetDuration)

        assertTrue("Adele score ($adeleScore) must crush Lionel Richie ($lionelScore)", adeleScore > 130.0)
        assertTrue("Lionel Richie should receive severe artist mismatch penalty ($lionelScore)", lionelScore <= 45.0)
        assertEquals("adele", TrackMatcher.findBestCandidate(listOf(lionelRichie, adele), targetTitle, artists, targetDuration)?.id)
    }

    @Test
    fun scoreCandidate_handlesShortAndNoiseWordTitles() {
        // BTS - "ON"
        val btsTitle = "ON"
        val btsArtists = listOf("BTS")
        val onCand = CandidateVideo("on_id", "BTS - 'ON' Official MV", "HYBE LABELS", 246L)
        val fakeLoveCand = CandidateVideo("fl_id", "BTS - Fake Love (Extended Version)", "HYBE LABELS", 246L)

        val onScore = TrackMatcher.scoreCandidate(onCand, btsTitle, btsArtists, 246L)
        val fakeLoveScore = TrackMatcher.scoreCandidate(fakeLoveCand, btsTitle, btsArtists, 246L)
        assertTrue("ON should score much higher than Fake Love ($onScore vs $fakeLoveScore)", onScore > fakeLoveScore + 40.0)

        // Kendrick Lamar - "i"
        val kTitle = "i"
        val kArtists = listOf("Kendrick Lamar")
        val iCand = CandidateVideo("i_id", "Kendrick Lamar - i (Official Video)", "KendrickLamarVEVO", 230L)
        val humbleCand = CandidateVideo("humble_id", "Kendrick Lamar - HUMBLE. (Official Video)", "KendrickLamarVEVO", 180L)

        val iScore = TrackMatcher.scoreCandidate(iCand, kTitle, kArtists, 230L)
        val humbleScore = TrackMatcher.scoreCandidate(humbleCand, kTitle, kArtists, 230L)
        assertTrue("'i' should score higher than HUMBLE ($iScore vs $humbleScore)", iScore > humbleScore)
    }

    @Test
    fun scoreCandidate_penalizesTypeBeatsAndSnippets() {
        val targetTitle = "TESTO"
        val artists = listOf("Major SPZ", "Majki")
        val duration = 121L

        val realSong = CandidateVideo("real", "Major SPZ - TESTO", "Major SPZ", 121L)
        val typeBeat = CandidateVideo("beat", "[FREE] Major SPZ x Majki Type Beat - TESTO", "Producer", 121L)
        val snippet = CandidateVideo("snip", "Major SPZ - TESTO (Snippet)", "Major SPZ", 35L)

        val realScore = TrackMatcher.scoreCandidate(realSong, targetTitle, artists, duration)
        val beatScore = TrackMatcher.scoreCandidate(typeBeat, targetTitle, artists, duration)
        val snipScore = TrackMatcher.scoreCandidate(snippet, targetTitle, artists, duration)

        assertTrue(realScore > beatScore + 40.0)
        assertTrue(realScore > snipScore + 60.0)
    }

    @Test
    fun scoreCandidate_normalizesApostrophesAndPunctuation() {
        val targetTitle = "Don't Stop Believin'"
        val artists = listOf("Journey")
        val duration = 250L

        val candidate = CandidateVideo("journey_id", "Journey - Don't Stop Believin' (Official Audio)", "Journey", 250L)
        val score = TrackMatcher.scoreCandidate(candidate, targetTitle, artists, duration)

        assertTrue("Exact title match with apostrophes should score >= 140 ($score)", score >= 140.0)
    }

    @Test
    fun parseCandidates_handlesLeadingWarningsInOutput() {
        val messyOutput = """
            WARNING: [youtube] Some extractor warning message
            [youtube] Downloading webpage
            {
              "entries": [
                {
                  "id": "vid123",
                  "title": "Clean Track",
                  "uploader": "Artist",
                  "duration": 200
                }
              ]
            }
        """.trimIndent()

        val parsed = TrackMatcher.parseCandidates(messyOutput)
        assertEquals(1, parsed.size)
        assertEquals("vid123", parsed[0].id)
        assertEquals(200L, parsed[0].durationSeconds)
    }

    @Test
    fun spotifyHelper_parseArtistList_extractsArtistsAccurately() {
        val list1 = SpotifyHelper.parseArtistList("Major SPZ, Majki, Funky Beatz")
        assertEquals(listOf("Major SPZ", "Majki", "Funky Beatz"), list1)

        val list2 = SpotifyHelper.parseArtistList("Majki feat. Diho")
        assertEquals(listOf("Majki", "Diho"), list2)

        val list3 = SpotifyHelper.parseArtistList("Major SPZ x Majki")
        assertEquals(listOf("Major SPZ", "Majki"), list3)
    }

    @Test
    fun spotifyHelper_cacheTrack_storesAndRetrieves() {
        val track = SpotifyTrack(
            title = "Test Track",
            artist = "Test Artist",
            spotifyUrl = "https://open.spotify.com/track/test12345"
        )
        SpotifyHelper.cacheTrack("https://open.spotify.com/track/test12345", track)

        val cached = SpotifyHelper.getCachedTrack("https://open.spotify.com/track/test12345")
        assertNotNull(cached)
        assertEquals("Test Track", cached?.title)
    }
}
