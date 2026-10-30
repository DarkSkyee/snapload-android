package com.example.mediadownloader.util

import android.content.Intent

object ShareIntentExtractor {

    /**
     * Extracts candidate text strings from all standard share fields in the Intent.
     */
    fun extractCandidates(intent: Intent?): List<String> {
        if (intent == null) return emptyList()
        val candidates = mutableListOf<String>()

        // 1. Extra Text
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
            if (it.isNotBlank()) candidates.add(it)
        }

        // 2. ClipData items (text & uri)
        intent.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                val item = clip.getItemAt(i)
                item.text?.toString()?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
                item.uri?.toString()?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
            }
        }

        // 3. Extra Subject
        intent.getStringExtra(Intent.EXTRA_SUBJECT)?.let {
            if (it.isNotBlank()) candidates.add(it)
        }

        // 4. Data URI
        intent.dataString?.takeIf { it.isNotBlank() }?.let {
            candidates.add(it)
        }

        return candidates
    }

    /**
     * Resolves the first valid HTTP/HTTPS URL found among the given text candidates.
     */
    fun extractUrlFromCandidates(candidates: List<String?>): String? {
        for (candidate in candidates) {
            if (candidate.isNullOrBlank()) continue
            val foundUrl = LinkParser.extractUrl(candidate)
            if (!foundUrl.isNullOrBlank()) {
                return foundUrl
            }
        }
        return null
    }

    /**
     * Extracts the first downloadable URL found across any share payload fields.
     */
    fun extractUrlFromIntent(intent: Intent?): String? {
        val candidates = extractCandidates(intent)
        return extractUrlFromCandidates(candidates)
    }

    /**
     * Extracts the best raw text payload for fallback handling.
     */
    fun extractRawSharedText(intent: Intent?): String? {
        val candidates = extractCandidates(intent)
        return candidates.firstOrNull { !it.isBlank() }
    }
}
