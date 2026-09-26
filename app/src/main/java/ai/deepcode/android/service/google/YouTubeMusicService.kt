package ai.deepcode.android.service.google

import ai.deepcode.android.service.tools.ToolExecutor
import ai.deepcode.android.util.AppLogger
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import java.net.URLEncoder

class YouTubeMusicService(private val context: Context) {

    data class ResolvedMusic(
        val song: String,
        val artist: String?,
        val wasReasoned: Boolean,
        val reasoningSummary: String? = null
    )

    private val knownArtistLatestTracks = mapOf(
        "karan aujla" to "Winning Speech",
        "drake" to "Nokia",
        "taylor swift" to "Fortnight",
        "diljit dosanjh" to "Hass Hass",
        "sidhu moose wala" to "Drippy",
        "the weeknd" to "Dancing in the Flames",
        "kendrick lamar" to "Not Like Us",
        "ap dhillon" to "Old Money",
        "eminem" to "Houdini",
        "post malone" to "I Had Some Help",
        "billie eilish" to "Birds of a Feather",
        "ariana grande" to "We Can't Be Friends"
    )

    fun resolveMusicQuery(rawSong: String, rawArtist: String?): ResolvedMusic {
        var clean = rawSong.trim()
            .replace(Regex("""^play\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(?:on|with|in|via|through|using)\s+(?:youtube\s*music|yt\s*music|youtube|yt)\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(?:on|for)\s+me\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+in\s+app\s*$""", RegexOption.IGNORE_CASE), "")
            .replace("[", " ").replace("]", " ")
            .trim()

        var artist = rawArtist?.trim()?.takeIf { it.isNotBlank() }

        // Extract "by <Artist>"
        val byMatch = Regex("""\s+by\s+""", RegexOption.IGNORE_CASE).find(clean)
        if (byMatch != null) {
            val songPart = clean.substring(0, byMatch.range.first).trim()
            val artistPart = clean.substring(byMatch.range.last + 1).trim().removeSuffix(".").removeSuffix(",")
            if (artistPart.isNotEmpty()) {
                clean = songPart
                if (artist == null) artist = artistPart
            }
        }

        // Extract "new <Artist> song" pattern e.g. "new karan aujla song"
        if (artist == null) {
            val artistSongRegex = Regex("""^(?:new|latest|recent|trending|popular|best|top|hits)?\s*([a-zA-Z0-9\s]{2,40}?)\s+(?:song|songs|track|tracks|music)\s*$""", RegexOption.IGNORE_CASE)
            val match = artistSongRegex.find(clean)
            if (match != null) {
                val candidateArtist = match.groupValues[1].trim()
                val candLower = candidateArtist.lowercase()
                val nonArtists = setOf("a", "the", "some", "any", "punjabi", "hindi", "english", "bhojpuri", "tamil", "telugu")
                if (candLower !in nonArtists) {
                    artist = candidateArtist
                }
            }
        }

        val lowerClean = clean.lowercase()
        val reasoningKeywords = listOf("new", "latest", "recent", "trending", "popular", "best", "top", "hits", "song", "songs", "track", "tracks")
        val isDescriptive = reasoningKeywords.any { lowerClean.contains(it) } || lowerClean == "music"

        if (!isDescriptive) {
            return ResolvedMusic(clean, artist, wasReasoned = false)
        }

        // Reasoning required: Find the latest release!
        val artistQuery = artist ?: clean
        val searchQuery = "latest $artistQuery song release track title 2024 2025 2026"
        val searchSnippet = fetchSearchSnippet(searchQuery)
        val extractedTitle = if (!searchSnippet.isNullOrBlank()) extractTrackTitle(searchSnippet, artist) else null

        val finalSong = when {
            !extractedTitle.isNullOrBlank() -> extractedTitle
            artist != null && knownArtistLatestTracks.containsKey(artist.lowercase()) -> knownArtistLatestTracks[artist.lowercase()]!!
            artist != null -> "$artist latest song"
            else -> clean
        }

        val summary = if (!extractedTitle.isNullOrBlank()) {
            "Found latest track **$finalSong**${if (artist != null) " by $artist" else ""}"
        } else if (artist != null && knownArtistLatestTracks.containsKey(artist.lowercase())) {
            "Selected newest hit **$finalSong** by $artist"
        } else {
            "Searching latest releases for ${artist ?: clean}"
        }

        return ResolvedMusic(finalSong, artist, wasReasoned = true, reasoningSummary = summary)
    }

    private fun fetchSearchSnippet(query: String): String? {
        return try {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                ToolExecutor(context).fetchDDGLite(query, 4)
            } else {
                var res: String? = null
                val t = Thread {
                    res = try { ToolExecutor(context).fetchDDGLite(query, 4) } catch (_: Exception) { null }
                }
                t.start()
                t.join(3500)
                res
            }
        } catch (e: Exception) {
            AppLogger.e("YouTubeMusicService", "Search failed: ${e.message}")
            null
        }
    }

    private fun extractTrackTitle(searchOutput: String, artist: String?): String? {
        val artistLower = artist?.lowercase()?.trim() ?: ""
        val quoteRegex = Regex("""["'“]([A-Za-z0-9\s'’–-]{2,40}?)["'”]""")
        val quoteMatches = quoteRegex.findAll(searchOutput).map { it.groupValues[1].trim() }.toList()
        for (candidate in quoteMatches) {
            val candLower = candidate.lowercase()
            if (candLower.contains("youtube") || candLower.contains("music") || candLower.contains("official") ||
                candLower.contains("video") || candLower.contains("lyrics") || candLower.contains("song") ||
                candLower.contains("audio") || candLower.contains("album") || candLower == artistLower) {
                continue
            }
            if (candidate.length in 2..35) {
                return candidate
            }
        }

        val phraseRegex = Regex("""(?:latest\s+single|new\s+song|latest\s+track|latest\s+song|new\s+single|hit\s+track)\s+([A-Za-z0-9\s'’–-]{2,30}?)(?:,|\.|\s+by|\s+from|\s+is|\s+was|\s+released|\s+with)""", RegexOption.IGNORE_CASE)
        val phraseMatch = phraseRegex.find(searchOutput)
        if (phraseMatch != null) {
            val candidate = phraseMatch.groupValues[1].trim()
            val candLower = candidate.lowercase()
            if (!candLower.contains("youtube") && !candLower.contains("music") && candLower != artistLower && candidate.length in 2..35) {
                return candidate
            }
        }
        return null
    }

    fun play(song: String, artist: String? = null, platform: String = "youtube_music"): String {
        val resolved = resolveMusicQuery(song, artist)
        val targetSong = resolved.song
        val targetArtist = resolved.artist

        val query = buildQuery(targetSong, targetArtist)
        val encodedQuery = encode(query)
        val ytMusicUrl = "https://music.youtube.com/search?q=$encodedQuery"
        val ytUrl = "https://www.youtube.com/results?search_query=$encodedQuery"

        val pm = context.packageManager
        val isYtMusicInstalled = try {
            pm.getPackageInfo("com.google.android.apps.youtube.music", 0) != null
        } catch (_: Exception) { false }

        val isYtInstalled = try {
            pm.getPackageInfo("com.google.android.youtube", 0) != null
        } catch (_: Exception) { false }

        var launched = false
        var launchTarget = "YouTube Music"

        // 1. Try launching via root / shell 'am start' first if root is available.
        if (ai.deepcode.android.util.RootSystem.isRootAvailable.value) {
            try {
                val targetPkg = if (isYtMusicInstalled && platform != "youtube") {
                    "com.google.android.apps.youtube.music"
                } else if (isYtInstalled) {
                    "com.google.android.youtube"
                } else null

                val escapedQuery = query.replace("\"", "\\\"").replace("$", "\\$")
                val amCmd = if (targetPkg != null) {
                    "am start -a android.media.action.MEDIA_PLAY_FROM_SEARCH -p $targetPkg -e query \"$escapedQuery\" --activity-clear-top"
                } else {
                    "am start -a android.intent.action.VIEW -d \"$ytMusicUrl\""
                }
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", amCmd))
                p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                if (p.exitValue() == 0) {
                    launched = true
                    launchTarget = if (targetPkg == "com.google.android.apps.youtube.music") "YouTube Music" else if (targetPkg != null) "YouTube" else "browser"
                }
            } catch (e: Exception) {
                AppLogger.e("YouTubeMusicService", "Root am start failed", e)
            }
        }

        // 2. Standard Android Intent if not launched via root
        if (!launched) {
            try {
                // Try MediaPlayFromSearch on YouTube Music first (triggers direct playback)
                if (isYtMusicInstalled && platform != "youtube") {
                    val mediaIntent = Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                        setPackage("com.google.android.apps.youtube.music")
                        putExtra(android.provider.MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/audio")
                        putExtra(android.provider.MediaStore.EXTRA_MEDIA_TITLE, targetSong)
                        if (targetArtist != null) putExtra(android.provider.MediaStore.EXTRA_MEDIA_ARTIST, targetArtist)
                        putExtra(android.app.SearchManager.QUERY, query)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    if (mediaIntent.resolveActivity(pm) != null) {
                        context.startActivity(mediaIntent)
                        launched = true
                        launchTarget = "YouTube Music"
                    }
                }

                // If media intent didn't work, try VIEW url in YouTube Music
                if (!launched && isYtMusicInstalled && platform != "youtube") {
                    val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ytMusicUrl)).apply {
                        setPackage("com.google.android.apps.youtube.music")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    if (viewIntent.resolveActivity(pm) != null) {
                        context.startActivity(viewIntent)
                        launched = true
                        launchTarget = "YouTube Music"
                    }
                }

                // Fallback to regular YouTube app
                if (!launched && isYtInstalled) {
                    val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ytUrl)).apply {
                        setPackage("com.google.android.youtube")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    if (ytIntent.resolveActivity(pm) != null) {
                        context.startActivity(ytIntent)
                        launched = true
                        launchTarget = "YouTube"
                    }
                }

                // Fallback to browser
                if (!launched) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ytMusicUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(browserIntent)
                    launched = true
                    launchTarget = "browser"
                }
            } catch (e: Exception) {
                AppLogger.e("YouTubeMusicService", "Standard intent failed", e)
            }
        }

        val artistText = if (!targetArtist.isNullOrBlank()) " by **$targetArtist**" else ""
        return buildString {
            if (resolved.wasReasoned && !resolved.reasoningSummary.isNullOrBlank()) {
                append("🔍 ${resolved.reasoningSummary}\n\n")
            }
            append("🎵 **Playing:** **$targetSong**$artistText on $launchTarget\n")
            append("🎧 [Open on YouTube Music]($ytMusicUrl)\n")
            append("▶️ [Open on YouTube]($ytUrl)")
        }
    }

    fun search(song: String, artist: String?, open: Boolean): String {
        val query = buildQuery(song, artist)
        if (!open) return buildResult(query)
        return play(song, artist)
    }

    private fun buildQuery(song: String, artist: String?): String {
        return if (!artist.isNullOrBlank()) "$song $artist" else song
    }

    private fun buildResult(query: String): String {
        return "Searched YouTube Music for '$query'. Use play action to open and play."
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

