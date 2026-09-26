package ai.deepcode.android.service.google

import ai.deepcode.android.util.AppLogger
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

class YouTubeMusicService(private val context: Context) {

    fun play(song: String, artist: String? = null, platform: String = "youtube_music"): String {
        val query = buildQuery(song, artist)
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
        // This completely bypasses Android 10+ background activity start restrictions when called from background services!
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
                        putExtra(android.provider.MediaStore.EXTRA_MEDIA_TITLE, song)
                        if (artist != null) putExtra(android.provider.MediaStore.EXTRA_MEDIA_ARTIST, artist)
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

        val artistText = if (!artist.isNullOrBlank()) " by **$artist**" else ""
        return buildString {
            append("🎵 **Playing:** **$song**$artistText on $launchTarget\n")
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

