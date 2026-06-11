package com.pauta.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pauta.app.data.dao.DayDao
import com.pauta.app.data.dao.FocusBlockDao
import com.pauta.app.data.dao.FocusSessionDao
import com.pauta.app.data.dao.GoalDao
import com.pauta.app.data.dao.HabitDao
import com.pauta.app.data.dao.HabitMarkDao
import com.pauta.app.data.dao.IntentionDao
import com.pauta.app.data.dao.PlannedIntentionDao
import com.pauta.app.data.dao.PrefsDao
import com.pauta.app.data.dao.RoutineDao
import com.pauta.app.data.entity.DayEntity
import com.pauta.app.data.entity.FocusBlockEntity
import com.pauta.app.data.entity.FocusSessionEntity
import com.pauta.app.data.entity.GoalEntity
import com.pauta.app.data.entity.HabitCountEntity
import com.pauta.app.data.entity.HabitEntity
import com.pauta.app.data.entity.HabitLogEntity
import com.pauta.app.data.entity.HabitRespiroEntity
import com.pauta.app.data.entity.IntentionEntity
import com.pauta.app.data.entity.MilestoneEntity
import com.pauta.app.data.entity.PlannedIntentionEntity
import com.pauta.app.data.entity.PrefsEntity
import com.pauta.app.data.entity.RoutineEntity
import com.pauta.app.data.entity.RoutineItemEntity

/**
 * The local SQLite database — the single source of truth, fully offline. Every
 * table mirrors a slice of the web `pauta.v4` schema. // PT: base de dados local
 * SQLite — fonte única, totalmente offline.
 */
@Database(
    entities = [
        DayEntity::class,
        IntentionEntity::class,
        FocusBlockEntity::class,
        FocusSessionEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        HabitRespiroEntity::class,
        HabitCountEntity::class,
        GoalEntity::class,
        MilestoneEntity::class,
        RoutineEntity::class,
        RoutineItemEntity::class,
        PlannedIntentionEntity::class,
        PrefsEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dayDao(): DayDao
    abstract fun intentionDao(): IntentionDao
    abstract fun focusBlockDao(): FocusBlockDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun habitDao(): HabitDao
    abstract fun habitMarkDao(): HabitMarkDao
    abstract fun goalDao(): GoalDao
    abstract fun routineDao(): RoutineDao
    abstract fun plannedIntentionDao(): PlannedIntentionDao
    abstract fun prefsDao(): PrefsDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE prefs ADD COLUMN lastAutoBackupMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE prefs ADD COLUMN pinHash TEXT")
                db.execSQL("ALTER TABLE prefs ADD COLUMN pinSalt TEXT")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pauta.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
