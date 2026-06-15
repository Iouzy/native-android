package com.pauta.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms the daily reminder alarms after a reboot or app update — alarms don't
 * survive either. Covers both the three global reminder kinds and the D2 per-habit
 * clock alarms, each from its own SharedPreferences snapshot (no Room needed at
 * boot). // PT: re-arma os lembretes (globais + por maré) após reinício ou
 * atualização.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            -> {
                ReminderScheduler.reschedule(context)
                HabitReminderScheduler.reschedule(context)
            }
        }
    }
}
