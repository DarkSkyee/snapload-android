package com.example.mediadownloader.util

import java.net.URI

object LinkParser {
    // Regex matching standard HTTP / HTTPS URLs within text
    private val URL_REGEX = "(https?://[a-zA-Z0-9\\-_.~:/?#\\[\\]@!$&'()*+,;=%]+)".toRegex()

    enum class Platform {
        YOUTUBE,
        TIKTOK,
        OTHER,
        UNKNOWN
    }

    /**
     * Extracts the first valid HTTP/HTTPS URL from any shared text.
     * Trims common trailing punctuation that might get attached (like ., !, ?)
     */
    fun extractUrl(sharedText: String?): String? {
        if (sharedText.isNullOrBlank()) return null
        val match = URL_REGEX.find(sharedText) ?: return null
        var url = match.value.trim()
        
        // Remove trailing punctuation that often attaches from chat/social apps
        while (url.isNotEmpty() && (url.endsWith(".") || url.endsWith(",") || url.endsWith("!") || url.endsWith("?"))) {
            url = url.substring(0, url.length - 1)
        }
        
        return if (isValidUrl(url)) url else null
    }

    /**
     * Identifies the platform of the given URL.
     */
    fun detectPlatform(url: String?): Platform {
        if (url.isNullOrBlank()) return Platform.UNKNOWN
        val lower = url.lowercase()
        return when {
            lower.contains("youtube.com") || lower.contains("youtu.be") -> Platform.YOUTUBE
            lower.contains("tiktok.com") -> Platform.TIKTOK
            isValidUrl(url) -> Platform.OTHER
            else -> Platform.UNKNOWN
        }
    }

    /**
     * Checks if URL is supported (YouTube, TikTok, or other yt-dlp supported media URL)
     */
    fun isSupported(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val platform = detectPlatform(url)
        return platform != Platform.UNKNOWN
    }

    private fun isValidUrl(urlString: String): Boolean {
        return try {
            val uri = URI(urlString)
            val scheme = uri.scheme?.lowercase()
            (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }
}
