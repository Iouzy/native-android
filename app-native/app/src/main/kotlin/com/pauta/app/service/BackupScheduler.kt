package com.pauta.app.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * B1: keeps the periodic [BackupWorker] in step with the `autoBackup` cadence
 * preference, the same way [ReminderScheduler] tracks the reminder prefs. Called
 * whenever the cadence changes; "off" cancels the job. The cadence gate still
 * lives in the repository, so even WorkManager's flex window can't double-back-up
 * within a period. // PT: agenda/cancela a tarefa periódica conforme a frequência.
 */
object BackupScheduler {
    private const val WORK = "pauta_auto_backup"

    /** Re-arm (or cancel) the periodic backup for the given cadence string. */
    fun reschedule(context: Context, cadence: String) {
        val wm = WorkManager.getInstance(context)
        // WorkManager's minimum period is 15 min — every cadence we offer (30m+)
        // clears it. // PT: o mínimo do WorkManager (15 min) cabe em todas.
        val minutes: Long = when (cadence) {
            "30m"    -> 30
            "hourly" -> 60
            "daily"  -> 24 * 60
            "weekly" -> 7 * 24 * 60
            else     -> { wm.cancelUniqueWork(WORK); return }
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(minutes, TimeUnit.MINUTES).build()
        // UPDATE swaps the interval in place without dropping the existing
        // schedule. // PT: UPDATE troca o intervalo sem perder o agendamento.
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
