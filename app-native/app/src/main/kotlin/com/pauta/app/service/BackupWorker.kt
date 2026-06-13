package com.pauta.app.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pauta.app.PautaApplication
import com.pauta.app.data.SafBackup
import com.pauta.app.domain.DateUtils

/**
 * B1: the periodic auto-backup job. Runs the same [maybeAutoBackup] the app runs
 * on resume, but on WorkManager's schedule — so a backup happens even with the
 * app fully closed (the old cadence only fired while it was open, so an uninstall
 * or device loss could take everything). Reuses the process-wide repository like
 * [FocusActionReceiver], and writes to the user's SAF folder when one is set.
 * // PT: tarefa periódica de cópia — corre mesmo com a app fechada.
 */
class BackupWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? PautaApplication ?: return Result.success()
        return try {
            app.repository.maybeAutoBackup(applicationContext.filesDir, DateUtils.todayKey()) { uri, name, json ->
                SafBackup.write(applicationContext, uri, name, json)
            }
            Result.success()
        } catch (e: Exception) {
            // Transient I/O hiccup → let WorkManager retry with its own backoff.
            // // PT: falha transitória → o WorkManager volta a tentar.
            Result.retry()
        }
    }
}
