package com.example.mediadownloader.util

import org.json.JSONObject

data class CandidateVideo(
    val id: String,
    val title: String,
    val uploader: String = "",
    val durationSeconds: Long = 0L
)

object TrackMatcher {
    private val NOISE_WORDS = setOf(
        "the", "a", "an", "and", "or", "of", "in", "on", "at", "to", "for", "with",
        "feat", "ft", "prod", "by", "x", "w"
    )

    // Unwanted tags that alter the audio or indicate non-song content
    private val AUDIO_ALTERING_TAGS = listOf(
        "remix", "mashup", "vixa", "instrumental", "karaoke", "slowed", "reverb",
        "cover", "tribute", "parodia", "parody", "1 hour", "10 hour", "loop",
        "bass boosted", "bassboosted", "bass boost", "reaction", "live", "acoustic",
        "sped up", "speed up", "nightcore", "8d audio", "type beat", "acapella",
        "a cappella", "snippet", "teaser", "preview", "leak"
    )

    fun cleanTitleForSearch(title: String): String {
        var cleaned = title
        // Remove (feat. ...), (with ...), (prod. ...), [feat. ...], etc.
        cleaned = cleaned.replace(Regex("""[\(\[\{]\s*(?:feat\.|ft\.|with|prod\.)\s*[^)\]\}]*[\)\]\}]""", RegexOption.IGNORE_CASE), "")
        // Remove trailing "feat. ..." or "- feat. ..." not in brackets
        cleaned = cleaned.replace(Regex("""\s+(?:feat\.|ft\.|with|prod\.)\s+.*$""", RegexOption.IGNORE_CASE), "")
        // Remove (Official Video), (Official Audio), (Lyrics), (Visualizer), (Teledysk), etc.
        cleaned = cleaned.replace(Regex("""[\(\[\{]\s*(?:official\s*(?:video|audio|music video|lyric video|visualizer)?|lyrics?|audio|video|clip|teledysk|hd|4k)\s*[\)\]\}]""", RegexOption.IGNORE_CASE), "")
        // Strip apostrophes so contractions (don't -> dont) are consistent with normalizeText
        cleaned = cleaned.replace("'", "").replace("’", "").replace("`", "")
        // Remove quotes, commas, brackets
        cleaned = cleaned.replace(Regex("""[,\"'“”„«»]"""), " ")
        cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()
        return cleaned.ifBlank { title.trim() }
    }

    fun cleanArtistForSearch(artist: String): String {
        var cleaned = artist.replace("'", "").replace("’", "").replace("`", "")
        cleaned = cleaned.replace(Regex("""[,\"'“”„«»]"""), " ")
        cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()
        return cleaned.ifBlank { artist.trim() }
    }

    fun normalizeText(text: String): String {
        return text.lowercase()
            .replace("'", "")
            .replace("’", "")
            .replace("`", "")
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun extractWords(text: String): List<String> {
        val norm = normalizeText(text)
        if (norm.isBlank()) return emptyList()
        val allWords = norm.split(" ").filter { it.isNotBlank() }
        val nonNoise = allWords.filter { it !in NOISE_WORDS }
        return if (nonNoise.isNotEmpty()) nonNoise else allWords
    }

    fun scoreCandidate(
        candidate: CandidateVideo,
        trackTitle: String,
        artists: List<String>,
        targetDurationSeconds: Long
    ): Double {
        val cleanTargetTitle = cleanTitleForSearch(trackTitle)
        val normTarget = normalizeText(cleanTargetTitle)
        val normCandTitle = normalizeText(candidate.title)
        val normCandUploader = normalizeText(candidate.uploader)
        val candFullText = "$normCandTitle $normCandUploader"

        val targetWords = extractWords(cleanTargetTitle)
        val candTitleWords = extractWords(candidate.title)
        val candTitleWordSet = candTitleWords.toSet()

        var score = 0.0

        // 1. Title matching with whole-phrase and word-boundary accuracy
        val exactPhraseRegex = if (normTarget.isNotBlank()) {
            Regex("""(?:\b|^)${Regex.escape(normTarget)}(?:\b|$)""")
        } else null

        val isExactTitleMatch = exactPhraseRegex?.containsMatchIn(normCandTitle) == true

        if (isExactTitleMatch) {
            score += 65.0
        } else if (targetWords.isNotEmpty()) {
            val matchedCount = targetWords.count { it in candTitleWordSet }
            when {
                matchedCount == targetWords.size -> {
                    // All words present as discrete words in candidate title
                    score += 50.0
                }
                matchedCount > 0 -> {
                    // Partial word match - proportional
                    val ratio = matchedCount.toDouble() / targetWords.size
                    score += (ratio * 35.0)
                }
                else -> {
                    // No title words matched at all
                    score -= 100.0
                }
            }
        } else {
            score -= 80.0
        }

        // 2. Artist matching (CRITICAL: prevents matching wrong artist with same/similar title)
        val primaryArtist = artists.firstOrNull()?.trim() ?: ""
        var hasArtistMatch = false

        if (primaryArtist.isNotBlank()) {
            val normPrimary = normalizeText(primaryArtist)
            val primaryRegex = if (normPrimary.isNotBlank()) {
                Regex("""(?:\b|^)${Regex.escape(normPrimary)}(?:\b|$)""")
            } else null

            val primaryWords = extractWords(primaryArtist)
            val candAllWordSet = (candTitleWords + extractWords(candidate.uploader)).toSet()

            if (primaryRegex?.containsMatchIn(candFullText) == true) {
                score += 35.0
                hasArtistMatch = true
            } else if (primaryWords.isNotEmpty() && primaryWords.all { it in candAllWordSet }) {
                score += 30.0
                hasArtistMatch = true
            } else if (primaryWords.isNotEmpty() && primaryWords.any { it in candAllWordSet }) {
                score += 15.0
                hasArtistMatch = true
            }
        }

        // Check secondary artists
        for (sec in artists.drop(1).take(2)) {
            val normSec = normalizeText(sec)
            if (normSec.length >= 3) {
                val secRegex = Regex("""(?:\b|^)${Regex.escape(normSec)}(?:\b|$)""")
                if (secRegex.containsMatchIn(candFullText)) {
                    score += 10.0
                    hasArtistMatch = true
                }
            }
        }

        // Severe penalty if artists were specified but candidate has ZERO artist match
        if (!hasArtistMatch && artists.any { it.isNotBlank() }) {
            score -= 80.0
        }

        // 3. Duration match (CRITICAL: separates full song from snippets / extended sets / wrong tracks)
        if (targetDurationSeconds > 0 && candidate.durationSeconds > 0) {
            val diff = Math.abs(candidate.durationSeconds - targetDurationSeconds)
            when {
                diff <= 3 -> score += 45.0
                diff <= 7 -> score += 35.0
                diff <= 15 -> score += 20.0
                diff <= 30 -> score += 5.0
                diff <= 60 -> score -= 20.0
                else -> score -= 50.0
            }
        }

        // 4. Modifiers penalty (remix, type beat, instrumental, karaoke, slowed, vixa, nightcore, snippet, etc.)
        val targetLowerRaw = trackTitle.lowercase()
        val candTitleLowerRaw = candidate.title.lowercase()
        for (tag in AUDIO_ALTERING_TAGS) {
            if (candTitleLowerRaw.contains(tag) && !targetLowerRaw.contains(tag)) {
                if (tag in listOf("type beat", "snippet", "teaser", "preview", "leak")) {
                    score -= 60.0
                } else {
                    score -= 40.0
                }
            }
        }

        // 5. Official / Topic bonus
        if (normCandTitle.contains("official") || normCandUploader.endsWith("topic")) {
            score += 10.0
        }

        return score
    }

    fun parseCandidates(jsonOutput: String): List<CandidateVideo> {
        return try {
            val trimmed = jsonOutput.trim()
            val startIdx = trimmed.indexOf('{')
            val endIdx = trimmed.lastIndexOf('}')
            if (startIdx == -1 || endIdx == -1 || startIdx >= endIdx) return emptyList()
            val cleanJson = trimmed.substring(startIdx, endIdx + 1)

            val json = JSONObject(cleanJson)
            val entries = json.optJSONArray("entries") ?: return emptyList()
            val list = mutableListOf<CandidateVideo>()
            for (i in 0 until entries.length()) {
                val e = entries.optJSONObject(i) ?: continue
                val id = e.optString("id")
                val title = e.optString("title")
                if (id.isNotBlank() && title.isNotBlank()) {
                    val uploader = e.optString("uploader", e.optString("channel", ""))
                    val duration = e.optLong("duration", 0L)
                    list.add(CandidateVideo(id, title, uploader, duration))
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun findBestCandidate(
        candidates: List<CandidateVideo>,
        trackTitle: String,
        artists: List<String>,
        durationSeconds: Long,
        minScore: Double = 25.0
    ): CandidateVideo? {
        if (candidates.isEmpty()) return null
        val scored = candidates.map { candidate ->
            val s = scoreCandidate(candidate, trackTitle, artists, durationSeconds)
            candidate to s
        }
        val best = scored.maxByOrNull { it.second } ?: return null
        return if (best.second >= minScore) best.first else null
    }
}
