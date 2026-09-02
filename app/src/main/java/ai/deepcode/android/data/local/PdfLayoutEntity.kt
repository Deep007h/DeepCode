package ai.deepcode.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_layouts")
data class PdfLayoutEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val layoutJson: String,
    val isBuiltin: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
