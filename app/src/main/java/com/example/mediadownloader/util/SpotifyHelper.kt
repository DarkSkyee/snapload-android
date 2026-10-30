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
    val artists: List<String> = emptyList(),
    val durationSeconds: Long = 0L,
    val thumbnailUrl: String? = null,
    val spotifyUrl: String? = null,
    val searchQuery: String = ""
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

    private val trackCache = object : LinkedHashMap<String, SpotifyTrack>(30, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SpotifyTrack>?): Boolean {
            return size > 200
        }
    }

    fun cacheTrack(key: String, track: SpotifyTrack) {
        if (key.isBlank()) return
        synchronized(trackCache) {
            trackCache[key] = track
            track.spotifyUrl?.let { if (it.isNotBlank()) trackCache[it] = track }
        }
    }

    fun getCachedTrack(key: String): SpotifyTrack? {
        if (key.isBlank()) return null
        synchronized(trackCache) {
            return trackCache[key]
        }
    }

    fun parseArtistList(artistString: String): List<String> {
        if (artistString.isBlank()) return emptyList()
        return artistString.split(Regex("""[,;&/]|\s+(?:feat\.|ft\.|x|with)\s+""", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    fun buildSearchQuery(title: String, artists: List<String> = emptyList(), artist: String = ""): String {
        val cleanTitle = TrackMatcher.cleanTitleForSearch(title)
        val allArtists = if (artists.isNotEmpty()) artists else parseArtistList(artist)

        val primaryArtist = allArtists.firstOrNull()?.let { TrackMatcher.cleanArtistForSearch(it) }
            ?.takeIf { it.isNotBlank() }
            ?: ""

        val secondArtist = allArtists.getOrNull(1)?.let { TrackMatcher.cleanArtistForSearch(it) }
            ?.takeIf { it.isNotBlank() && it.length < 25 }

        val artistPart = if (secondArtist != null && secondArtist != primaryArtist) {
            "$primaryArtist $secondArtist"
        } else {
            primaryArtist
        }

        val searchTerms = if (artistPart.isNotBlank()) "$artistPart $cleanTitle" else cleanTitle
        return "ytsearch1:$searchTerms"
    }

    suspend fun fetchTrack(url: String): SpotifyTrack? = withContext(Dispatchers.IO) {
        val cleanUrl = resolveSpotifyUrl(url)
        getCachedTrack(cleanUrl)?.let { return@withContext it }
        getCachedTrack(url)?.let { return@withContext it }

        try {
            val oembedUrl = "https://open.spotify.com/oembed?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
            val jsonStr = httpGet(oembedUrl) ?: return@withContext null
            val json = JSONObject(jsonStr)

            val fullTitle = json.optString("title", "Spotify Track")
            val thumb = json.optString("thumbnail_url").takeIf { it.isNotBlank() }

            // Extract artist if possible, oembed title is often "Title" or "Artist - Title"
            var title = fullTitle
            var artist = "Spotify"
            val artistList = mutableListOf<String>()
            var durationSeconds = 0L

            var thumbnail = thumb
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
                            for (i in 0 until artistsArray.length()) {
                                val aObj = artistsArray.optJSONObject(i)
                                if (aObj != null) {
                                    val n = aObj.optString("name")
                                    if (n.isNotBlank()) artistList.add(n)
                                }
                            }
                            if (artistList.isNotEmpty()) {
                                artist = artistList.joinToString(", ")
                            }
                        } else {
                            val subtitle = entity.optString("subtitle")
                            if (subtitle.isNotBlank()) {
                                artist = subtitle
                                artistList.add(subtitle)
                            }
                        }

                        val durMs = entity.optLong("duration", 0L).takeIf { it > 0L }
                            ?: entity.optLong("duration_ms", 0L).takeIf { it > 0L }
                            ?: entity.optLong("durationMs", 0L)
                        if (durMs > 0L) {
                            durationSeconds = durMs / 1000L
                        }

                        if (thumbnail.isNullOrBlank()) {
                            thumbnail = extractThumbnail(entity)
                        }
                    }
                }
            }

            val query = buildSearchQuery(title, artistList, artist)
            val cleanUrl = resolveSpotifyUrl(url)

            val track = SpotifyTrack(
                title = title,
                artist = artist,
                artists = artistList,
                durationSeconds = durationSeconds,
                thumbnailUrl = thumbnail,
                spotifyUrl = cleanUrl,
                searchQuery = query
            )
            cacheTrack(url, track)
            cacheTrack(cleanUrl, track)
            track
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
                    val subtitle = entity.optString("subtitle")
                    if (subtitle.isNotBlank()) author = subtitle

                    thumbnail = extractThumbnail(entity)

                    val trackList = entity.optJSONArray("trackList")
                    if (trackList != null) {
                        for (i in 0 until trackList.length()) {
                            val tObj = trackList.optJSONObject(i) ?: continue
                            val tTitle = tObj.optString("title", "Track ${i + 1}")
                            val tArtist = tObj.optString("subtitle", author)
                            val durationMs = tObj.optLong("duration", 0L).takeIf { it > 0L }
                                ?: tObj.optLong("duration_ms", 0L).takeIf { it > 0L }
                                ?: tObj.optLong("durationMs", 0L)
                            val uriStr = tObj.optString("uri")
                            val rawId = tObj.optString("id").takeIf { it.isNotBlank() }
                                ?: if (uriStr.startsWith("spotify:track:")) uriStr.removePrefix("spotify:track:") else null
                            val spotifyUrl = if (!rawId.isNullOrBlank()) "https://open.spotify.com/track/$rawId" else null
                            val artistsList = if (tArtist.isNotBlank()) {
                                parseArtistList(tArtist)
                            } else emptyList()
                            val query = buildSearchQuery(tTitle, artistsList, tArtist)
                            val spTrack = SpotifyTrack(
                                title = tTitle,
                                artist = tArtist,
                                artists = artistsList,
                                durationSeconds = durationMs / 1000L,
                                thumbnailUrl = thumbnail,
                                spotifyUrl = spotifyUrl,
                                searchQuery = query
                            )
                            tracks.add(spTrack)
                            spotifyUrl?.let { cacheTrack(it, spTrack) }
                            cacheTrack("sp_item_${i}_${spTrack.title}", spTrack)
                        }
                    }
                }
            }

            // Fallback to oembed if title is still default or thumbnail missing
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

    private fun extractThumbnail(entity: JSONObject): String? {
        val visual = entity.optJSONObject("visualIdentity")
        val imgArray = visual?.optJSONArray("image")
        if (imgArray != null && imgArray.length() > 0) {
            val url = imgArray.optJSONObject(0)?.optString("url")
            if (!url.isNullOrBlank()) return url
        }
        val sources = entity.optJSONObject("coverArt")?.optJSONArray("sources")
        if (sources != null && sources.length() > 0) {
            val url = sources.optJSONObject(0)?.optString("url")
            if (!url.isNullOrBlank()) return url
        }
        return null
    }

    fun resolveSpotifyUrl(url: String): String {
        val clean = url.trim()
        if (clean.contains("spotify.link", ignoreCase = true)) {
            try {
                val conn = (URL(clean).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "HEAD"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                val redirect = conn.getHeaderField("Location")
                conn.disconnect()
                if (!redirect.isNullOrBlank()) {
                    return redirect
                }
            } catch (_: Exception) {}
        }
        return clean
    }

    fun toEmbedUrl(url: String): String? {
        val clean = resolveSpotifyUrl(url)
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
