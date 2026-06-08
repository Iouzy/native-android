package com.pauta.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pauta.app.data.dao.*
import com.pauta.app.data.entity.*

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
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun intentionDao(): IntentionDao
    abstract fun focusBlockDao(): FocusBlockDao
    abstract fun habitDao(): HabitDao
    abstract fun dayDao(): DayDao
    abstract fun goalDao(): GoalDao
    abstract fun routineDao(): RoutineDao
    abstract fun planDao(): PlanDao
    abstract fun prefsDao(): PrefsDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pauta.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
