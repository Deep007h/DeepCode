package ai.deepcode.android.memory

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Fts4
@Entity(tableName = "memory_chunks")
data class MemoryChunk(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Int? = null,
    val id: String, // UUID string
    val title: String,
    val content: String,
    val source: String,
    val tags: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "embedding_hint") val embeddingHint: String
)
