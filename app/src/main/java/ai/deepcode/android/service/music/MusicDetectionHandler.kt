package ai.deepcode.android.service.music

import ai.deepcode.android.service.google.YouTubeMusicService
import ai.deepcode.android.util.AppLogger
import android.content.Context

class MusicDetectionHandler(private val context: Context) {

    data class MusicRequest(val song: String, val artist: String?)

    fun parse(text: String): MusicRequest? {
        val lower = text.lowercase().trim()
        if (!lower.startsWith("play")) return null

        // Skip if it's a scheduling command (has schedule keywords)
        val scheduleWords = listOf("daily", "weekly", "hourly", "every", "recurring", "schedule", "automation")
        if (scheduleWords.any { lower.contains(it) }) return null

        val raw = text.trim()
        var query = raw
            .replace(Regex("play\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(?:on|with)\\s+youtube\\s*music\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(?:on|with)\\s+youtube\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(?:on|for)\\s+me\\s*$", RegexOption.IGNORE_CASE), "")
            .replace("[", " ").replace("]", " ")
            .replace("(", " ").replace(")", " ")
            .trim()
            .replace(Regex("\\s+"), " ")
            .trim()

        // Extract artist after "by" if present
        var song = query
        var artist: String? = null
        val byMatch = Regex("\\s+by\\s+", RegexOption.IGNORE_CASE).find(query)
        if (byMatch != null) {
            val idx = byMatch.range.first
            song = query.substring(0, idx).trim()
            artist = query.substring(byMatch.range.last + 1).trim().removeSuffix(",").removeSuffix(".").trim()
            if (artist.isEmpty()) artist = null
        }

        if (song.isEmpty()) return null
        return MusicRequest(song, artist)
    }

    fun play(text: String): String {
        val request = parse(text) ?: return ""
        val yt = YouTubeMusicService(context)
        val result = yt.play(request.song, request.artist)
        AppLogger.d("MusicHandler", "Result: $result")
        val msg = "🎵 Opening **${request.song}**${if (request.artist != null) " by ${request.artist}" else ""} on YouTube Music..."
        AppLogger.d("MusicHandler", msg)
        return msg
    }
}
