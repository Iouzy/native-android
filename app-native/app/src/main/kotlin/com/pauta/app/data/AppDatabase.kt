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
import com.pauta.app.data.dao.SearchDao
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
    version = 7,
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
    abstract fun searchDao(): SearchDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE prefs ADD COLUMN lastAutoBackupMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE prefs ADD COLUMN pinHash TEXT")
                db.execSQL("ALTER TABLE prefs ADD COLUMN pinSalt TEXT")
            }
        }

        // A7: the native-only habit archive flag. Existing tides default to
        // not-archived. // PT: nova coluna "archived" — marés existentes ficam activas.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            }
        }

        // B1: the SAF folder the auto-backup also writes to (persisted tree URI).
        // Null for everyone until they pick one. // PT: pasta SAF da cópia automática.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE prefs ADD COLUMN backupFolderUri TEXT")
            }
        }

        // C3: the native-only biometric-unlock opt-in. Off for everyone until they
        // enable it (and only ever consulted when a PIN is set). // PT: opção de
        // desbloqueio biométrico — desligada até ser ativada.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE prefs ADD COLUMN biometricEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        // E1: the full-text search index over intention text, day reflections and
        // focus-block titles/reflections. Create the FTS table + its sync triggers,
        // then backfill every existing row (triggers only fire on future writes).
        // // PT: índice de pesquisa FTS — cria a tabela + triggers e reindexa o que
        // já existe.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createSearchIndex(db)
                createSearchTriggers(db)
                backfillSearchIndex(db)
            }
        }

        // E2: the native-only day key on which the "Memórias" card was last
        // dismissed (null = never). It hides only that day's memory, so it's plain
        // device UI state — not exported in v4. // PT: dia em que a memória foi
        // dispensada (só esconde a memória desse dia).
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE prefs ADD COLUMN memoriaDismissedDay TEXT")
            }
        }

        // E1: on a brand-new database Room has just created every entity table —
        // add the FTS index and its sync triggers (no data to backfill yet). The
        // upgrade path does the same in MIGRATION_5_6. // PT: numa BD nova, cria o
        // índice FTS + triggers (sem dados para reindexar).
        private val SEARCH_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                createSearchIndex(db)
                createSearchTriggers(db)
            }
        }

        // E1: a plain FTS4 virtual table, deliberately NOT a Room @Entity — so Room
        // doesn't try to create or validate it and we own the exact tokenizer. The
        // unicode61 tokenizer with remove_diacritics makes search accent-insensitive
        // for Portuguese ("maré" ↔ "mare", "intenção" ↔ "intencao"); the metadata
        // columns are `notindexed` so only `text` is matched. The `text` column
        // holds the searchable content (intention/reflection text, or block
        // "title reflection"); docId is `i:<id>` / `d:<dayKey>` / `b:<id>` and dayKey
        // is the day a hit belongs to. // PT: tabela virtual FTS4 (não é entidade
        // Room); unicode61 sem diacríticos → pesquisa insensível a acentos (PT).
        private fun createSearchIndex(db: SupportSQLiteDatabase) {
            // Three-tier fallback for FTS4 tokenizer compatibility:
            //  1. unicode61 + remove_diacritics=1 → full accent-insensitive PT search
            //  2. unicode61 alone → proper Unicode handling, no accent folding
            //  3. simple (built-in, always present) → basic search, no accent folding
            // Some OEM SQLite builds (e.g. MIUI/HyperOS) omit the remove_diacritics
            // option or the tokenizer entirely, causing a hard crash at DB creation.
            // // PT: três níveis de tokenizer — unicode61 com acentos, unicode61 sem,
            // ou simple — para não crashar em builds OEM com SQLite restrito.
            val columns = """
                docId, source, dayKey, text,
                notindexed=docId, notindexed=source, notindexed=dayKey
            """.trimIndent()
            val tried = mutableListOf<String>()
            for (tokenize in listOf(
                """tokenize=unicode61 "remove_diacritics=1"""",
                "tokenize=unicode61",
                null,
            )) {
                try {
                    val extra = if (tokenize != null) ", $tokenize" else ""
                    db.execSQL(
                        "CREATE VIRTUAL TABLE IF NOT EXISTS search_index USING fts4($columns$extra)",
                    )
                    return
                } catch (e: Exception) {
                    tried += (tokenize ?: "simple(default)")
                }
            }
            // All three failed — surface so a bug report captures it.
            // // PT: os três níveis falharam — lança para o relatório de erros.
            error("Could not create FTS4 search_index — tried: $tried")
        }

        // E1: keep the index in lock-step with the three source tables. Doing it in
        // triggers (rather than on each repository write) means every path stays
        // consistent for free — single edits, bulk backup import, demo reseed and
        // resetAll all flow through INSERT/UPDATE/DELETE here. A block has no day
        // key of its own, so its day is derived from createdAt in LOCAL time, to
        // match DateUtils.dayKeyOf. // PT: triggers mantêm o índice sincronizado em
        // todos os caminhos (edição, importação, reseed, reset).
        private fun createSearchTriggers(db: SupportSQLiteDatabase) {
            // intentions — docId "i:<id>"
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS search_intentions_ai AFTER INSERT ON intentions BEGIN
                    INSERT INTO search_index(docId, source, dayKey, text)
                    VALUES('i:' || new.id, 'intention', new.dayKey, new.text);
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS search_intentions_au AFTER UPDATE ON intentions BEGIN
                    DELETE FROM search_index WHERE docId = 'i:' || old.id;
                    INSERT INTO search_index(docId, source, dayKey, text)
                    VALUES('i:' || new.id, 'intention', new.dayKey, new.text);
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS search_intentions_ad AFTER DELETE ON intentions BEGIN
                    DELETE FROM search_index WHERE docId = 'i:' || old.id;
                END
                """.trimIndent(),
            )

            // day reflections — docId "d:<dayKey>"; only index a non-empty reflection
            // (a day row exists even with a blank reflection).
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS search_days_ai AFTER INSERT ON days BEGIN
                    INSERT INTO search_index(docId, source, dayKey, text)
                    SELECT 'd:' || new.dayKey, 'reflection', new.dayKey, new.reflection
                    WHERE new.reflection <> '';
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS search_days_au AFTER UPDATE ON days BEGIN
                    DELETE FROM search_index WHERE docId = 'd:' || old.dayKey;
                    INSERT INTO search_index(docId, source, dayKey, text)
                    SELECT 'd:' || new.dayKey, 'reflection', new.dayKey, new.reflection
                    WHERE new.reflection <> '';
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS search_days_ad AFTER DELETE ON days BEGIN
                    DELETE FROM search_index WHERE docId = 'd:' || old.dayKey;
                END
                """.trimIndent(),
            )

            // focus blocks — docId "b:<id>"; searchable text is title + reflection.
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS search_blocks_ai AFTER INSERT ON focus_blocks BEGIN
                    INSERT INTO search_index(docId, source, dayKey, text)
                    VALUES('b:' || new.id, 'block',
                           date(new.createdAt / 1000, 'unixepoch', 'localtime'),
                           new.title || ' ' || new.reflection);
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS search_blocks_au AFTER UPDATE ON focus_blocks BEGIN
                    DELETE FROM search_index WHERE docId = 'b:' || old.id;
                    INSERT INTO search_index(docId, source, dayKey, text)
                    VALUES('b:' || new.id, 'block',
                           date(new.createdAt / 1000, 'unixepoch', 'localtime'),
                           new.title || ' ' || new.reflection);
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS search_blocks_ad AFTER DELETE ON focus_blocks BEGIN
                    DELETE FROM search_index WHERE docId = 'b:' || old.id;
                END
                """.trimIndent(),
            )
        }

        // E1: index the rows that already exist at upgrade time (direct inserts —
        // these don't fire the source-table triggers above). // PT: reindexa o que
        // já existe na migração.
        private fun backfillSearchIndex(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT INTO search_index(docId, source, dayKey, text) " +
                    "SELECT 'i:' || id, 'intention', dayKey, text FROM intentions",
            )
            db.execSQL(
                "INSERT INTO search_index(docId, source, dayKey, text) " +
                    "SELECT 'd:' || dayKey, 'reflection', dayKey, reflection FROM days WHERE reflection <> ''",
            )
            db.execSQL(
                "INSERT INTO search_index(docId, source, dayKey, text) " +
                    "SELECT 'b:' || id, 'block', date(createdAt / 1000, 'unixepoch', 'localtime'), " +
                    "title || ' ' || reflection FROM focus_blocks",
            )
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pauta.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .addCallback(SEARCH_CALLBACK)
                    .build()
                    .also { instance = it }
            }
    }
}
