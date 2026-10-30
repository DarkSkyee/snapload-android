package com.example.mediadownloader.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class SpotifyTrack(
    val title: String,
    val artist: String,
    val durationSeconds: Long = 0L,
    val thumbnailUrl: String? = null,
    val searchQuery: String = "ytsearch1:$artist - $title audio"
)

data class SpotifyPlaylist(
    val title: String,
    val author: String,
    val thumbnailUrl: String?,
    val tracks: List<SpotifyTrack>
)

object SpotifyHelper {
    private const val TAG = "SpotifyHelper"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    suspend fun fetchTrack(url: String): SpotifyTrack? = withContext(Dispatchers.IO) {
        try {
            val oembedUrl = "https://open.spotify.com/oembed?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
            val jsonStr = httpGet(oembedUrl) ?: return@withContext null
            val json = JSONObject(jsonStr)

            val fullTitle = json.optString("title", "Spotify Track")
            val thumb = json.optString("thumbnail_url").takeIf { it.isNotBlank() }

            // Extract artist if possible, oembed title is often "Title" or "Artist - Title"
            var title = fullTitle
            var artist = "Spotify"

            val embedUrl = toEmbedUrl(url)
            val embedHtml = embedUrl?.let { httpGet(it) }
            if (embedHtml != null) {
                val nextData = extractNextDataJson(embedHtml)
                if (nextData != null) {
                    val entity = nextData.optJSONObject("props")
                        ?.optJSONObject("pageProps")
                        ?.optJSONObject("state")
                        ?.optJSONObject("data")
                        ?.optJSONObject("entity")

                    if (entity != null) {
                        title = entity.optString("name", title).ifBlank { entity.optString("title", title) }
                        val artistsArray = entity.optJSONArray("artists")
                        if (artistsArray != null && artistsArray.length() > 0) {
                            val names = mutableListOf<String>()
                            for (i in 0 until artistsArray.length()) {
                                val aObj = artistsArray.optJSONObject(i)
                                if (aObj != null) {
                                    val n = aObj.optString("name")
                                    if (n.isNotBlank()) names.add(n)
                                }
                            }
                            if (names.isNotEmpty()) {
                                artist = names.joinToString(", ")
                            }
                        } else {
                            val subtitle = entity.optString("subtitle")
                            if (subtitle.isNotBlank()) artist = subtitle
                        }
                    }
                }
            }

            SpotifyTrack(
                title = title,
                artist = artist,
                durationSeconds = 0L,
                thumbnailUrl = thumb,
                searchQuery = "ytsearch1:$artist - $title audio"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve Spotify track $url: ${e.message}", e)
            null
        }
    }

    suspend fun fetchPlaylist(url: String): SpotifyPlaylist? = withContext(Dispatchers.IO) {
        try {
            val embedUrl = toEmbedUrl(url) ?: return@withContext null
            val html = httpGet(embedUrl) ?: return@withContext null

            var playlistTitle = "Spotify Playlist"
            var author = "Spotify"
            var thumbnail: String? = null
            val tracks = mutableListOf<SpotifyTrack>()

            val nextData = extractNextDataJson(html)
            if (nextData != null) {
                val entity = nextData.optJSONObject("props")
                    ?.optJSONObject("pageProps")
                    ?.optJSONObject("state")
                    ?.optJSONObject("data")
                    ?.optJSONObject("entity")

                if (entity != null) {
                    playlistTitle = entity.optString("name", playlistTitle)
                    val visual = entity.optJSONObject("visualIdentity")
                    thumbnail = visual?.optJSONObject("image")?.optJSONArray("sources")?.optJSONObject(0)?.optString("url")

                    val trackList = entity.optJSONArray("trackList")
                    if (trackList != null) {
                        for (i in 0 until trackList.length()) {
                            val tObj = trackList.optJSONObject(i) ?: continue
                            val tTitle = tObj.optString("title", "Track ${i + 1}")
                            val tArtist = tObj.optString("subtitle", "Unknown Artist")
                            val durationMs = tObj.optLong("duration", 0L)
                            tracks.add(
                                SpotifyTrack(
                                    title = tTitle,
                                    artist = tArtist,
                                    durationSeconds = durationMs / 1000L,
                                    thumbnailUrl = thumbnail,
                                    searchQuery = "ytsearch1:$tArtist - $tTitle audio"
                                )
                            )
                        }
                    }
                }
            }

            // Fallback to oembed if title is still default
            if (playlistTitle == "Spotify Playlist" || thumbnail == null) {
                try {
                    val oembedUrl = "https://open.spotify.com/oembed?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
                    val oembedStr = httpGet(oembedUrl)
                    if (oembedStr != null) {
                        val ojson = JSONObject(oembedStr)
                        if (playlistTitle == "Spotify Playlist") {
                            playlistTitle = ojson.optString("title", playlistTitle)
                        }
                        if (thumbnail == null) {
                            thumbnail = ojson.optString("thumbnail_url").takeIf { it.isNotBlank() }
                        }
                    }
                } catch (_: Exception) {}
            }

            SpotifyPlaylist(
                title = playlistTitle,
                author = author,
                thumbnailUrl = thumbnail,
                tracks = tracks
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve Spotify playlist $url: ${e.message}", e)
            null
        }
    }

    private fun toEmbedUrl(url: String): String? {
        val clean = url.trim()
        val matcher = Pattern.compile("open\\.spotify\\.com/(track|playlist|album)/([a-zA-Z0-9]+)").matcher(clean)
        return if (matcher.find()) {
            val type = matcher.group(1)
            val id = matcher.group(2)
            "https://open.spotify.com/embed/$type/$id"
        } else {
            null
        }
    }

    private fun extractNextDataJson(html: String): JSONObject? {
        val pattern = Pattern.compile("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>")
        val matcher = pattern.matcher(html)
        return if (matcher.find()) {
            try {
                JSONObject(matcher.group(1) ?: "")
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    private fun httpGet(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json")
                instanceFollowRedirects = true
            }
            if (connection.responseCode in 200..299) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    reader.readText()
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
