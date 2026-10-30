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
        "the", "a", "an", "and", "or", "of", "in", "on", "at", "to", "for", "with", "feat", "ft", "prod", "by"
    )

    // Unwanted tags that alter the audio compared to original track
    private val AUDIO_ALTERING_TAGS = listOf(
        "remix", "mashup", "vixa", "instrumental", "karaoke", "slowed", "reverb",
        "cover", "tribute", "parodia", "1 hour", "10 hour", "loop", "bass boosted",
        "reaction", "live", "acoustic"
    )

    fun cleanTitleForSearch(title: String): String {
        // Remove (feat. ...), (with ...), (prod. ...), [feat. ...], etc.
        var cleaned = title.replace(Regex("""[\(\[\{]\s*(?:feat\.|ft\.|with|prod\.)\s*[^)\]\}]*[\)\]\}]""", RegexOption.IGNORE_CASE), "")
        // Remove (Official Video), (Official Audio), (Lyrics), etc.
        cleaned = cleaned.replace(Regex("""[\(\[\{]\s*official\s*(?:video|audio|music video|lyric video)?\s*[\)\]\}]""", RegexOption.IGNORE_CASE), "")
        // Remove quotes, commas, brackets
        cleaned = cleaned.replace(Regex("""[,\"'“”„]"""), " ")
        cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()
        return cleaned.ifBlank { title.trim() }
    }

    fun cleanArtistForSearch(artist: String): String {
        var cleaned = artist.replace(Regex("""[,\"'“”„]"""), " ")
        cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()
        return cleaned.ifBlank { artist.trim() }
    }

    fun scoreCandidate(
        candidate: CandidateVideo,
        trackTitle: String,
        artists: List<String>,
        targetDurationSeconds: Long
    ): Double {
        val cleanTarget = cleanTitleForSearch(trackTitle).lowercase()
        val candTitleLower = candidate.title.lowercase()
        val candUploaderLower = candidate.uploader.lowercase()
        val targetLower = trackTitle.lowercase()

        val targetWords = cleanTarget.split(Regex("""[\s\-_.,!?:;'"()\[\]{}]+"""))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in NOISE_WORDS }

        var score = 0.0

        // 1. Title matching
        if (cleanTarget.isNotBlank() && candTitleLower.contains(cleanTarget)) {
            score += 60.0
        } else if (targetWords.isNotEmpty()) {
            val matches = targetWords.count { candTitleLower.contains(it) }
            if (matches == targetWords.size) {
                score += 50.0
            } else if (matches > 0) {
                score += (matches.toDouble() / targetWords.size) * 40.0
            } else {
                score -= 100.0
            }
        } else if (cleanTarget.isNotBlank() && candTitleLower.contains(cleanTarget)) {
            score += 40.0
        } else {
            score -= 80.0
        }

        // 2. Artist matching
        val primaryArtist = artists.firstOrNull()?.lowercase() ?: ""
        if (primaryArtist.isNotBlank()) {
            val pWords = primaryArtist.split(Regex("""[\s\-_.,!?:;'"()\[\]{}]+"""))
                .map { it.trim() }
                .filter { it.length >= 2 && it !in NOISE_WORDS }

            if (candTitleLower.contains(primaryArtist) || candUploaderLower.contains(primaryArtist)) {
                score += 35.0
            } else if (pWords.isNotEmpty() && pWords.all { candTitleLower.contains(it) || candUploaderLower.contains(it) }) {
                score += 30.0
            } else if (pWords.isNotEmpty() && pWords.any { candTitleLower.contains(it) || candUploaderLower.contains(it) }) {
                score += 15.0
            }
        }

        // Secondary artists bonus
        for (sec in artists.drop(1).take(2)) {
            val sLow = sec.lowercase()
            if (sLow.length >= 3 && (candTitleLower.contains(sLow) || candUploaderLower.contains(sLow))) {
                score += 10.0
            }
        }

        // 3. Duration match (CRITICAL)
        if (targetDurationSeconds > 0 && candidate.durationSeconds > 0) {
            val diff = Math.abs(candidate.durationSeconds - targetDurationSeconds)
            when {
                diff <= 3 -> score += 45.0
                diff <= 7 -> score += 35.0
                diff <= 15 -> score += 20.0
                diff <= 30 -> score += 5.0
                diff <= 60 -> score -= 15.0
                else -> score -= 40.0
            }
        }

        // 4. Modifiers penalty (remix, instrumental, karaoke, slowed, vixa, etc.)
        for (tag in AUDIO_ALTERING_TAGS) {
            if (candTitleLower.contains(tag) && !targetLower.contains(tag)) {
                score -= 40.0
            }
        }

        // 5. Official / Topic bonus
        if (candTitleLower.contains("official") || candUploaderLower.endsWith("- topic")) {
            score += 10.0
        }

        return score
    }

    fun parseCandidates(jsonOutput: String): List<CandidateVideo> {
        return try {
            val json = JSONObject(jsonOutput)
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
