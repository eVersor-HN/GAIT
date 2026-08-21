package dev.eversorhn.gait.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An asset another user exported and this user took into their division. The payload is the
 * GAIT-ASSET text block verbatim; the roster re-parses it and simulates the asset from
 * [importedEpochDay] on. If the division eliminates it, it stays here as a record (the row is
 * never rehired).
 */
@Entity(tableName = "imported_assets")
data class ImportedAssetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val payload: String,
    val importedEpochDay: Long,
    val importedAtEpochMillis: Long,
)
