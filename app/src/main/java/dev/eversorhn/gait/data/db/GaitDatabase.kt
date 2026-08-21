package dev.eversorhn.gait.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.eversorhn.gait.data.db.dao.SessionDao
import dev.eversorhn.gait.data.db.dao.TwinProfileDao
import dev.eversorhn.gait.data.db.entity.SessionEntity
import dev.eversorhn.gait.data.db.entity.TwinProfileEntity

@Database(
    entities = [SessionEntity::class, TwinProfileEntity::class],
    version = 6,
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
                    // Real migrations from v5 on -- there is installed data on real devices
                    // now. Destructive fallback stays only for pre-v5 leftovers nobody has.
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }

        /** v0.5.0: per-session opponent message + Composure state (Direct Channel log), duel columns. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN twinLine TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN composureState TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN isDuel INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN duelWon INTEGER")
            }
        }
    }
}
