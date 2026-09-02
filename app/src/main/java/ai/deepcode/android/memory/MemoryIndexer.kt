package ai.deepcode.android.memory

import android.content.Context
import java.util.UUID

class MemoryIndexer(context: Context) {
    private val memoryManager = MemoryManager(context)

    suspend fun ingest(source: String, rawText: String) {
        if (rawText.trim().isEmpty()) return

        // 1. Strip HTML tags
        val cleanText = rawText.replace(Regex("<[^>]*>"), "")

        // 2. Remove duplicate lines
        val lines = cleanText.split("\n")
        val uniqueLines = lines.distinct()
        val deduplicatedText = uniqueLines.joinToString("\n")

        // 3. Chunk text at 2500 chars with 200-char overlap
        val chunkSize = 2500
        val overlap = 200
        val textLength = deduplicatedText.length

        var start = 0
        var chunkIndex = 1

        while (start < textLength) {
            val end = kotlin.math.min(start + chunkSize, textLength)
            val chunkText = deduplicatedText.substring(start, end)

            val chunk = MemoryChunk(
                id = UUID.randomUUID().toString(),
                title = "Indexed Block #$chunkIndex",
                content = chunkText,
                source = source,
                tags = "indexed,$source",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                embeddingHint = source
            )
            memoryManager.insertMemoryChunk(chunk)

            if (end == textLength) break

            start += (chunkSize - overlap)
            chunkIndex++
        }
    }
}
