package dev.eversorhn.gait.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.eversorhn.gait.data.db.entity.ImportedAssetEntity

@Dao
interface ImportedAssetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(asset: ImportedAssetEntity)

    @Query("SELECT * FROM imported_assets ORDER BY importedAtEpochMillis")
    suspend fun getAll(): List<ImportedAssetEntity>

    @Query("DELETE FROM imported_assets WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM imported_assets")
    suspend fun deleteAll()
}
