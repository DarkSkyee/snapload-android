package com.example.mediadownloader.util

import java.net.URI

object LinkParser {
    // Regex matching standard HTTP / HTTPS URLs within text
    private val URL_REGEX = "(https?://[a-zA-Z0-9\\-_.~:/?#\\[\\]@!$&'()*+,;=%]+)".toRegex()

    // Punctuation and bracket characters that commonly wrap URLs in chat/social messages
    private val TRAILING_JUNK = charArrayOf('.', ',', '!', '?', ')', ']', '}', '>', '"', '\'', ';', ':', '`')

    enum class Platform {
        YOUTUBE,
        TIKTOK,
        OTHER,
        UNKNOWN
    }

    /**
     * Extracts the first valid HTTP/HTTPS URL from any shared text.
     * Trims common trailing punctuation and wrapping characters that might get attached (like ., !, ?, ), >, etc.)
     */
    fun extractUrl(sharedText: String?): String? {
        if (sharedText.isNullOrBlank()) return null
        val match = URL_REGEX.find(sharedText) ?: return null
        var url = match.value.trim()

        // Strip trailing punctuation and closing brackets/quotes
        while (url.isNotEmpty() && url.last() in TRAILING_JUNK) {
            url = url.substring(0, url.length - 1)
        }

        return if (isValidUrl(url)) url else null
    }

    /**
     * Identifies the platform of the given URL.
     */
    fun detectPlatform(url: String?): Platform {
        if (url.isNullOrBlank()) return Platform.UNKNOWN
        val cleanUrl = extractUrl(url) ?: url.trim()
        val uri = try {
            URI(cleanUrl)
        } catch (_: Exception) {
            return Platform.UNKNOWN
        }
        val host = uri.host?.lowercase().orEmpty()
        return when {
            host == "youtu.be" || host.endsWith(".youtu.be") ||
                    host == "youtube.com" || host.endsWith(".youtube.com") -> Platform.YOUTUBE
            host == "tiktok.com" || host.endsWith(".tiktok.com") -> Platform.TIKTOK
            isValidUrl(cleanUrl) -> Platform.OTHER
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
            val uri = URI(urlString.trim())
            val scheme = uri.scheme?.lowercase()
            (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }
}
