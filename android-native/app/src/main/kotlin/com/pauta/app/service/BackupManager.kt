package com.pauta.app.service

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.pauta.app.PautaApplication
import com.pauta.app.data.entity.*
import com.pauta.app.ui.viewmodel.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/** Full JSON snapshot of every table — the offline-first, anti-lock-in export. */
@Serializable
data class BackupData(
    val app: String = "pauta",
    val version: Int = 5,
    val exportedAt: Long = System.currentTimeMillis(),
    val days: List<DayEntity> = emptyList(),
    val intentions: List<IntentionEntity> = emptyList(),
    val blocks: List<FocusBlockEntity> = emptyList(),
    val sessions: List<FocusSessionEntity> = emptyList(),
    val habits: List<HabitEntity> = emptyList(),
    val logs: List<HabitLogEntity> = emptyList(),
    val respiros: List<HabitRespiroEntity> = emptyList(),
    val counts: List<HabitCountEntity> = emptyList(),
    val prefs: PrefsEntity = PrefsEntity()
)

object BackupManager {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun buildBackup(context: Context): BackupData = withContext(Dispatchers.IO) {
        val db = (context.applicationContext as PautaApplication).database
        BackupData(
            days       = db.dayDao().getAll(),
            intentions = db.intentionDao().getAll(),
            blocks     = db.focusBlockDao().getAllBlocks(),
            sessions   = db.focusBlockDao().getAllSessions(),
            habits     = db.habitDao().getAllSuspend(),
            logs       = db.habitDao().getAllLogsGlobal(),
            respiros   = db.habitDao().getAllRespirosGlobal(),
            counts     = db.habitDao().getAllCountsGlobal(),
            prefs      = db.prefsDao().getSuspend() ?: PrefsEntity()
        )
    }

    /** Write the JSON snapshot to the public Downloads folder. */
    suspend fun exportToDownloads(context: Context, vm: AppViewModel): Boolean = withContext(Dispatchers.IO) {
        try {
            val data = buildBackup(context)
            val text = json.encodeToString(data)
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val name = "pauta-backup-$stamp.json"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext false
                resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                File(dir, name).writeText(text)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Write a flat CSV snapshot (anti-lock-in) to the public Downloads folder. */
    suspend fun exportCsvToDownloads(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val csv = CsvExport.build(buildBackup(context))
            // Lead with a UTF-8 BOM (U+FEFF) so spreadsheets (Excel) read accented PT text.
            val text = String(intArrayOf(0xFEFF), 0, 1) + csv
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            writeToDownloads(context, "pauta-export-$stamp.csv", "text/csv", text)
        } catch (e: Exception) {
            false
        }
    }

    /** Shared Downloads writer (MediaStore on Q+, public dir below). */
    private fun writeToDownloads(context: Context, name: String, mime: String, text: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                File(dir, name).writeText(text)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Restore from a native JSON backup (BackupData). Returns row count. */
    suspend fun importJson(context: Context, jsonText: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (jsonText.length > 25 * 1024 * 1024) return@withContext Result.failure(Exception("too large"))
            applyBackup(context, json.decodeFromString(BackupData.serializer(), jsonText))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Restore from a backup exported by the WEB/Capacitor build (store.jsx shape). */
    suspend fun importWebBackup(context: Context, jsonText: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (jsonText.length > 25 * 1024 * 1024) return@withContext Result.failure(Exception("too large"))
            applyBackup(context, WebBackupConverter.convert(jsonText))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Import either a native backup or a web (store.jsx) backup — auto-detected.
     * The native snapshot has a top-level `days` ARRAY; the web shape has `days`
     * as an object (or nests the state under `data`).
     */
    suspend fun importAny(context: Context, jsonText: String): Result<Int> {
        val looksNative = try {
            json.parseToJsonElement(jsonText).jsonObject["days"] is JsonArray
        } catch (e: Exception) {
            false
        }
        return if (looksNative) importJson(context, jsonText) else importWebBackup(context, jsonText)
    }

    /** Write a decoded snapshot into the database. Returns intentions+blocks+habits count. */
    private suspend fun applyBackup(context: Context, data: BackupData): Result<Int> {
        return try {
            val db = (context.applicationContext as PautaApplication).database
            db.dayDao().let { dao -> data.days.forEach { dao.upsert(it) } }
            db.intentionDao().let { dao -> data.intentions.forEach { dao.insert(it) } }
            db.focusBlockDao().let { dao ->
                data.blocks.forEach { dao.insert(it) }
                data.sessions.forEach { dao.insertSession(it) }
            }
            db.habitDao().let { dao ->
                data.habits.forEach { dao.insert(it) }
                data.logs.forEach { dao.insertLog(it) }
                data.respiros.forEach { dao.insertRespiro(it) }
                data.counts.forEach { dao.upsertCount(it) }
            }
            db.prefsDao().upsert(data.prefs)
            Result.success(data.intentions.size + data.blocks.size + data.habits.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
