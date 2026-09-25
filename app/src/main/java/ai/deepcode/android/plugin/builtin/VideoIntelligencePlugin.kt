package ai.deepcode.android.plugin.builtin

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import ai.deepcode.android.domain.model.Tool
import ai.deepcode.android.plugin.DeepCodePlugin
import ai.deepcode.android.plugin.PluginCategory
import ai.deepcode.android.plugin.PluginConfigField
import ai.deepcode.android.plugin.ConfigFieldType
import ai.deepcode.android.util.AppLogger
import com.google.gson.JsonObject
import java.io.File
import java.io.FileOutputStream

/**
 * Multimodal Video Intelligence Plugin inspired by muse.ai.
 * Provides on-device video metadata inspection, keyframe extraction,
 * audio track separation, and scene timeline indexing.
 */
class VideoIntelligencePlugin : DeepCodePlugin {
    override val id: String = "video_intelligence"
    override val displayName: String = "Video Intelligence (muse.ai)"
    override val description: String = "Perceptual video inspection, keyframe extraction for OCR, audio separation, and timestamped analysis"
    override val version: String = "1.0.0"
    override val category: PluginCategory = PluginCategory.MEDIA
    override val iconName: String = "movie"

    override fun getTools(): List<Tool> {
        return listOf(
            Tool("video_inspect", "Inspect video file container, resolution, duration, bitrate, frame rate, and audio streams", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf("type" to "string", "description" to "Local path to video file (.mp4, .mkv, .webm, etc.)")
                ),
                "required" to listOf("path")
            )),
            Tool("video_extract_frames", "Extract high-resolution video frames at timestamps or intervals for visual inspection or OCR", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf("type" to "string", "description" to "Local path to video file"),
                    "timestamps_sec" to mapOf(
                        "type" to "string",
                        "description" to "Comma-separated timestamps in seconds (e.g. '5, 15, 30') or empty for 3 evenly spaced keyframes"
                    )
                ),
                "required" to listOf("path")
            )),
            Tool("video_extract_audio", "Extract the audio stream from a video file into a standalone audio file (.aac/.wav) for transcription", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf("type" to "string", "description" to "Local path to video file"),
                    "output_format" to mapOf("type" to "string", "description" to "Target format: 'aac' or 'wav', default 'aac'")
                ),
                "required" to listOf("path")
            ))
        )
    }

    override fun execute(toolName: String, args: JsonObject, context: Context): String {
        return when (toolName) {
            "video_inspect" -> {
                val path = args.get("path")?.asString ?: return "Error: Missing path argument"
                inspectVideo(path)
            }
            "video_extract_frames" -> {
                val path = args.get("path")?.asString ?: return "Error: Missing path argument"
                val tsString = args.get("timestamps_sec")?.asString ?: ""
                extractFrames(path, tsString, context)
            }
            "video_extract_audio" -> {
                val path = args.get("path")?.asString ?: return "Error: Missing path argument"
                val format = args.get("output_format")?.asString ?: "aac"
                extractAudio(path, format, context)
            }
            else -> "Unknown video intelligence tool: $toolName"
        }
    }

    private fun inspectVideo(path: String): String {
        val file = File(path)
        if (!file.exists()) return "Error: Video file not found: $path"

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "N/A"
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "N/A"
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "video/mp4"
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: "0"
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) ?: "yes"

            val durationSec = durationMs / 1000.0
            val minutes = (durationSec / 60).toInt()
            val seconds = (durationSec % 60).toInt()
            val formattedDuration = String.format("%02d:%02d", minutes, seconds)
            val bitrateKbps = bitrate / 1000

            """
🎬 Video Analysis Report:
• File: ${file.name} (${file.length() / (1024 * 1024)} MB)
• Duration: $formattedDuration (${durationMs}ms)
• Resolution: ${width}x${height} (Rotation: ${rotation}°)
• Bitrate: ${bitrateKbps} kbps
• Format: $mimeType
• Has Audio Stream: $hasAudio
• Path: ${file.absolutePath}
            """.trimIndent()
        } catch (e: Exception) {
            "Failed to inspect video: ${e.message}"
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun extractFrames(path: String, timestampsSec: String, context: Context): String {
        val file = File(path)
        if (!file.exists()) return "Error: Video file not found: $path"

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 10000L
            val durationSec = durationMs / 1000.0

            val timesList = if (timestampsSec.isNotBlank()) {
                timestampsSec.split(",").mapNotNull { it.trim().toDoubleOrNull() }
            } else {
                // 3 evenly distributed frames at 25%, 50%, 75%
                listOf(durationSec * 0.25, durationSec * 0.50, durationSec * 0.75)
            }

            val outDir = File(context.filesDir, "plugins/video_frames")
            if (!outDir.exists()) outDir.mkdirs()

            val extractedPaths = mutableListOf<String>()
            for ((idx, sec) in timesList.withIndex()) {
                val timeUs = (sec * 1_000_000).toLong()
                val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bitmap != null) {
                    val frameFile = File(outDir, "frame_${file.nameWithoutExtension}_t${sec.toInt()}s_${idx}.png")
                    FileOutputStream(frameFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                    }
                    extractedPaths.add(frameFile.absolutePath)
                }
            }

            if (extractedPaths.isEmpty()) {
                "No frames could be extracted from $path."
            } else {
                val imageTags = extractedPaths.joinToString("\n") { "[image:$it]" }
                "Extracted ${extractedPaths.size} keyframes:\n$imageTags"
            }
        } catch (e: Exception) {
            "Failed to extract video frames: ${e.message}"
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun extractAudio(path: String, format: String, context: Context): String {
        val file = File(path)
        if (!file.exists()) return "Error: Video file not found: $path"

        val outDir = File(context.filesDir, "plugins/video_audio")
        if (!outDir.exists()) outDir.mkdirs()
        val ext = if (format.lowercase() == "wav") "wav" else "m4a"
        val outFile = File(outDir, "${file.nameWithoutExtension}_audio.$ext")

        return try {
            // 1. Try native Android MediaExtractor + MediaMuxer for AAC audio track
            if (ext == "m4a" && extractAudioNatively(file, outFile)) {
                return "[file:${outFile.absolutePath}]\nAudio extracted successfully to ${outFile.absolutePath} (${outFile.length() / 1024} KB)"
            }

            // 2. Fallback: try ffmpeg binary if available using ProcessBuilder without shell injection
            val ffmpegExtracted = extractAudioWithFfmpeg(file, outFile)
            if (ffmpegExtracted && outFile.exists() && outFile.length() > 0) {
                "[file:${outFile.absolutePath}]\nAudio extracted successfully to ${outFile.absolutePath} (${outFile.length() / 1024} KB)"
            } else {
                "Error: Could not extract audio track from ${file.name}. Ensure the file contains a valid audio stream."
            }
        } catch (e: Exception) {
            "Audio extraction error: ${e.message}"
        }
    }

    private fun extractAudioNatively(sourceFile: File, outputFile: File): Boolean {
        var extractor: android.media.MediaExtractor? = null
        var muxer: android.media.MediaMuxer? = null
        return try {
            extractor = android.media.MediaExtractor().apply { setDataSource(sourceFile.absolutePath) }
            var audioTrackIndex = -1
            var audioFormat: android.media.MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) return false

            extractor.selectTrack(audioTrackIndex)
            if (outputFile.exists()) outputFile.delete()
            muxer = android.media.MediaMuxer(outputFile.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            val maxBufferSize = if (audioFormat.containsKey(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormat.getInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(64 * 1024)
            } else {
                128 * 1024
            }
            val buffer = java.nio.ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            while (true) {
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags

                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }
            true
        } catch (e: Exception) {
            AppLogger.w("VideoIntelligencePlugin", "Native audio demuxing failed: ${e.message}")
            if (outputFile.exists()) outputFile.delete()
            false
        } finally {
            try { extractor?.release() } catch (_: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {}
        }
    }

    private fun extractAudioWithFfmpeg(sourceFile: File, outputFile: File): Boolean {
        return try {
            val pb = ProcessBuilder("ffmpeg", "-y", "-i", sourceFile.absolutePath, "-vn", "-c:a", "copy", outputFile.absolutePath)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val finished = proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
            if (finished && proc.exitValue() == 0 && outputFile.exists() && outputFile.length() > 0) {
                true
            } else {
                proc.destroyForcibly()
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun getConfigFields(): List<PluginConfigField> {
        return listOf(
            PluginConfigField(
                key = "video_frames_dir",
                label = "Keyframes Storage Directory",
                type = ConfigFieldType.TEXT,
                defaultValue = "/storage/emulated/0/Download/VideoFrames"
            )
        )
    }
}
