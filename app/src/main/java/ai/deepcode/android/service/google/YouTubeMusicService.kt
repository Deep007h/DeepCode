package ai.deepcode.android.service.google

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URLEncoder

class YouTubeMusicService(private val context: Context) {

    fun play(song: String, artist: String?): String {
        val query = buildQuery(song, artist)
        val searchUrl = "https://music.youtube.com/search?q=${encode(query)}"

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val pm = context.packageManager
            val ytMusic = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
                setPackage("com.google.android.apps.youtube.music")
            }
            val app = if (ytMusic.resolveActivity(pm) != null) "YouTube Music" else "browser"
            return "Opened $app searching for '$query'."
        } catch (e: Exception) {
            return "Failed to open: ${e.message}"
        }
    }

    fun search(song: String, artist: String?, open: Boolean): String {
        val query = buildQuery(song, artist)
        if (!open) return buildResult(query)
        return play(song, artist)
    }

    private fun buildQuery(song: String, artist: String?): String {
        return if (artist != null) "$song $artist" else song
    }

    private fun buildResult(query: String): String {
        return "Searched YouTube Music for '$query'. Use play action to open and play."
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
