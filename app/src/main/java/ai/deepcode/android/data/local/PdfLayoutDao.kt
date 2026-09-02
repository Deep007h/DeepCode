package ai.deepcode.android.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfLayoutDao {
    @Query("SELECT * FROM pdf_layouts ORDER BY createdAt DESC")
    fun getAllLayouts(): Flow<List<PdfLayoutEntity>>

    @Query("SELECT * FROM pdf_layouts")
    suspend fun getAllLayoutsList(): List<PdfLayoutEntity>

    @Query("SELECT * FROM pdf_layouts WHERE id = :id")
    suspend fun getLayoutById(id: String): PdfLayoutEntity?

    @Query("SELECT * FROM pdf_layouts WHERE name = :name COLLATE NOCASE")
    suspend fun getLayoutByName(name: String): PdfLayoutEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLayout(layout: PdfLayoutEntity)

    @Query("DELETE FROM pdf_layouts WHERE id = :id AND isBuiltin = 0")
    suspend fun deleteLayout(id: String)

    @Query("SELECT COUNT(*) FROM pdf_layouts")
    suspend fun getLayoutCount(): Int
}
