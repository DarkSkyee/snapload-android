package com.example.mediadownloader.util

import java.net.URI

object LinkParser {
    // Regex matching standard HTTP / HTTPS URLs within text
    private val URL_REGEX = "(https?://[a-zA-Z0-9\\-_.~:/?#\\[\\]@!$&'()*+,;=%]+)".toRegex()

    // Punctuation and bracket characters that commonly wrap URLs in chat/social messages
    private val TRAILING_JUNK = charArrayOf('.', ',', '!', '?', ')', ']', '}', '>', '"', '\'', ';', ':', '`')

    enum class Platform {
        YOUTUBE,
        YOUTUBE_MUSIC,
        SPOTIFY,
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
        if (cleanUrl.startsWith("spotify:", ignoreCase = true)) {
            return Platform.SPOTIFY
        }
        val uri = try {
            URI(cleanUrl)
        } catch (_: Exception) {
            return Platform.UNKNOWN
        }
        val host = uri.host?.lowercase().orEmpty()
        return when {
            host == "music.youtube.com" -> Platform.YOUTUBE_MUSIC
            host == "youtu.be" || host.endsWith(".youtu.be") ||
                    host == "youtube.com" || host.endsWith(".youtube.com") -> Platform.YOUTUBE
            host == "spotify.com" || host.endsWith(".spotify.com") ||
                    host == "spotify.link" || host.endsWith(".spotify.link") -> Platform.SPOTIFY
            host == "tiktok.com" || host.endsWith(".tiktok.com") -> Platform.TIKTOK
            isValidUrl(cleanUrl) -> Platform.OTHER
            else -> Platform.UNKNOWN
        }
    }

    /**
     * Determines whether the given URL points to a playlist or album.
     */
    fun isPlaylist(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val cleanUrl = extractUrl(url) ?: url.trim()
        val platform = detectPlatform(cleanUrl)
        return when (platform) {
            Platform.YOUTUBE, Platform.YOUTUBE_MUSIC -> {
                cleanUrl.contains("list=", ignoreCase = true) ||
                        cleanUrl.contains("/playlist", ignoreCase = true)
            }
            Platform.SPOTIFY -> {
                cleanUrl.contains("/playlist/", ignoreCase = true) ||
                        cleanUrl.contains("/album/", ignoreCase = true)
            }
            else -> cleanUrl.contains("playlist", ignoreCase = true)
        }
    }

    /**
     * Checks whether the given URL / platform is exclusively an audio service (e.g. Spotify, YT Music audio).
     */
    fun isAudioOnlyPlatform(url: String?): Boolean {
        val platform = detectPlatform(url)
        return platform == Platform.SPOTIFY || platform == Platform.YOUTUBE_MUSIC
    }

    /**
     * Checks if URL is supported (YouTube, YT Music, Spotify, TikTok, or other yt-dlp supported media URL)
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
