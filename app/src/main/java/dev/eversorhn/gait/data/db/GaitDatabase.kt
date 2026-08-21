package dev.eversorhn.gait.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.eversorhn.gait.data.db.dao.PlannedDayOffDao
import dev.eversorhn.gait.data.db.dao.SessionDao
import dev.eversorhn.gait.data.db.dao.TwinMessageDao
import dev.eversorhn.gait.data.db.dao.TwinProfileDao
import dev.eversorhn.gait.data.db.entity.PlannedDayOffEntity
import dev.eversorhn.gait.data.db.entity.SessionEntity
import dev.eversorhn.gait.data.db.entity.TwinMessageEntity
import dev.eversorhn.gait.data.db.entity.TwinProfileEntity

@Database(
    entities = [SessionEntity::class, TwinProfileEntity::class, TwinMessageEntity::class, PlannedDayOffEntity::class],
    version = 8,
    exportSchema = true,
)
abstract class GaitDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun twinProfileDao(): TwinProfileDao
    abstract fun twinMessageDao(): TwinMessageDao
    abstract fun plannedDayOffDao(): PlannedDayOffDao

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
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
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

        /** v0.8.0: the Rest & Vacation calendar — days marked off in advance. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS planned_days_off (" +
                        "epochDay INTEGER PRIMARY KEY NOT NULL, " +
                        "createdAtEpochMillis INTEGER NOT NULL)"
                )
            }
        }

        /** v0.6.0: ledger stakes per round, the opponent's open wager on the profile, and an inbox table. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN stake INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE twin_profiles ADD COLUMN wagerStake INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE twin_profiles ADD COLUMN wagerCalled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE twin_profiles ADD COLUMN wagerEpochDay INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE twin_profiles ADD COLUMN wagerClaim TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS twin_messages (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "epochMillis INTEGER NOT NULL, " +
                        "kind TEXT NOT NULL, " +
                        "line TEXT NOT NULL, " +
                        "composureState TEXT)"
                )
            }
        }
    }
}
