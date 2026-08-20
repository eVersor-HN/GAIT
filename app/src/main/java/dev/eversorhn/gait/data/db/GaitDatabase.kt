package dev.eversorhn.gait.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.eversorhn.gait.data.db.dao.SessionDao
import dev.eversorhn.gait.data.db.dao.TwinProfileDao
import dev.eversorhn.gait.data.db.entity.SessionEntity
import dev.eversorhn.gait.data.db.entity.TwinProfileEntity

@Database(
    entities = [SessionEntity::class, TwinProfileEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class GaitDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun twinProfileDao(): TwinProfileDao

    companion object {
        @Volatile private var instance: GaitDatabase? = null

        fun getInstance(context: Context): GaitDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GaitDatabase::class.java,
                    "gait.db",
                )
                    // Pre-release: no installed base to migrate yet, so destructive
                    // migration beats hand-writing Migration objects for every schema tweak.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
