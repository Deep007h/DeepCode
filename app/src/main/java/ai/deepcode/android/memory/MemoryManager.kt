package ai.deepcode.android.memory

import android.content.Context
import ai.deepcode.android.data.local.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MemoryManager(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val memoryDao = database.memoryDao()

    fun getAllMemoryChunksFlow(): Flow<List<MemoryChunk>> = memoryDao.getAllMemoryChunksFlow()

    suspend fun getAllMemoryChunks(): List<MemoryChunk> = withContext(Dispatchers.IO) {
        memoryDao.getAllMemoryChunks()
    }

    suspend fun searchMemory(query: String): List<MemoryChunk> = withContext(Dispatchers.IO) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return@withContext emptyList()
        val sanitized = cleaned
            .replace("'", "''")
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" AND ")
        try {
            memoryDao.searchMemory(sanitized)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun deleteMemoryChunk(id: String) = withContext(Dispatchers.IO) {
        memoryDao.deleteMemoryChunk(id)
    }

    suspend fun insertMemoryChunk(chunk: MemoryChunk) = withContext(Dispatchers.IO) {
        memoryDao.insertMemoryChunk(chunk)
    }

    suspend fun pruneIfNeeded(maxCount: Int = 1000) {
        withContext(Dispatchers.IO) {
            memoryDao.pruneOldChunks(maxCount)
        }
    }
}
