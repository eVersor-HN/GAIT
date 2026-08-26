package dev.eversorhn.momentum.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.eversorhn.momentum.data.db.dao.ImportedAssetDao
import dev.eversorhn.momentum.data.db.dao.PlannedDayOffDao
import dev.eversorhn.momentum.data.db.dao.SessionDao
import dev.eversorhn.momentum.data.db.dao.TwinMessageDao
import dev.eversorhn.momentum.data.db.dao.TwinProfileDao
import dev.eversorhn.momentum.data.db.entity.ImportedAssetEntity
import dev.eversorhn.momentum.data.db.entity.PlannedDayOffEntity
import dev.eversorhn.momentum.data.db.entity.SessionEntity
import dev.eversorhn.momentum.data.db.entity.TwinMessageEntity
import dev.eversorhn.momentum.data.db.entity.TwinProfileEntity

@Database(
    entities = [SessionEntity::class, TwinProfileEntity::class, TwinMessageEntity::class, PlannedDayOffEntity::class, ImportedAssetEntity::class],
    version = 13,
    exportSchema = true,
)
abstract class MomentumDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun twinProfileDao(): TwinProfileDao
    abstract fun twinMessageDao(): TwinMessageDao
    abstract fun plannedDayOffDao(): PlannedDayOffDao
    abstract fun importedAssetDao(): ImportedAssetDao

    companion object {
        @Volatile private var instance: MomentumDatabase? = null

        fun getInstance(context: Context): MomentumDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MomentumDatabase::class.java,
                    "momentum.db",
                )
                    // Real migrations from v5 on -- there is installed data on real devices
                    // now. Destructive fallback stays only for pre-v5 leftovers nobody has.
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
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

        /**
          * v0.17.0: profiles become first-class. Sessions, messages and planned days are scoped
          * to a profile id instead of an activity string; profiles get a user-visible name.
          * Existing rows are attached to the profile of their activity (or the first profile).
          */
        /** v0.23.0: heart rate, when a monitor was paired for the session. */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN avgHeartRate INTEGER")
                db.execSQL("ALTER TABLE sessions ADD COLUMN maxHeartRate INTEGER")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE twin_profiles ADD COLUMN profileName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sessions ADD COLUMN profileId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE twin_messages ADD COLUMN profileId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE sessions SET profileId = COALESCE((SELECT p.id FROM twin_profiles p WHERE p.activityType = sessions.activityType LIMIT 1), (SELECT MIN(id) FROM twin_profiles))")
                db.execSQL("UPDATE twin_messages SET profileId = COALESCE((SELECT MIN(id) FROM twin_profiles), 0)")
                db.execSQL("UPDATE twin_profiles SET profileName = activityType WHERE profileName = ''")
                // planned_days_off: new composite key (profileId, epochDay)
                db.execSQL("CREATE TABLE IF NOT EXISTS planned_days_off_new (profileId INTEGER NOT NULL, epochDay INTEGER NOT NULL, createdAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(profileId, epochDay))")
                db.execSQL("INSERT OR REPLACE INTO planned_days_off_new (profileId, epochDay, createdAtEpochMillis) SELECT COALESCE((SELECT MIN(id) FROM twin_profiles), 0), epochDay, createdAtEpochMillis FROM planned_days_off")
                db.execSQL("DROP TABLE planned_days_off")
                db.execSQL("ALTER TABLE planned_days_off_new RENAME TO planned_days_off")
            }
        }

        /** v0.16.0: the Decommission Trial deadline. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE twin_profiles ADD COLUMN trialDeadlineEpochDay INTEGER NOT NULL DEFAULT -1")
            }
        }

        /** v0.14.0: route, climb, steadiness, novelty per session — the dimensions beyond pace. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN route TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN elevationGainMeters REAL")
                db.execSQL("ALTER TABLE sessions ADD COLUMN consistency REAL")
                db.execSQL("ALTER TABLE sessions ADD COLUMN routeNovelty REAL")
                db.execSQL("ALTER TABLE sessions ADD COLUMN forecastConsistency REAL")
            }
        }

        /** v0.10.0: assets imported from other users' divisions. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS imported_assets (" +
                        "id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, payload TEXT NOT NULL, " +
                        "importedEpochDay INTEGER NOT NULL, importedAtEpochMillis INTEGER NOT NULL)"
                )
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
