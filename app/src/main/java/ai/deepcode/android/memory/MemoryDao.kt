package ai.deepcode.android.memory

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_chunks ORDER BY rowid DESC")
    fun getAllMemoryChunksFlow(): Flow<List<MemoryChunk>>

    @Query("SELECT * FROM memory_chunks")
    suspend fun getAllMemoryChunks(): List<MemoryChunk>

    @Query("SELECT * FROM memory_chunks WHERE content MATCH :query")
    suspend fun searchMemory(query: String): List<MemoryChunk>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemoryChunk(chunk: MemoryChunk)

    @Query("DELETE FROM memory_chunks WHERE id = :id")
    suspend fun deleteMemoryChunk(id: String)

    @Query("DELETE FROM memory_chunks WHERE rowid NOT IN (SELECT rowid FROM memory_chunks ORDER BY rowid DESC LIMIT :maxCount)")
    suspend fun pruneOldChunks(maxCount: Int)
}
