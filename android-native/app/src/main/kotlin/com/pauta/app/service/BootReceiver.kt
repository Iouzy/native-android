package com.pauta.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms the scheduled reminder alarms after a reboot or an app update —
 * AlarmManager alarms survive neither. Reads persisted state via
 * ReminderScheduler, so no UI round-trip is needed.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" ->
                ReminderScheduler.rescheduleAll(context)
        }
    }
}
