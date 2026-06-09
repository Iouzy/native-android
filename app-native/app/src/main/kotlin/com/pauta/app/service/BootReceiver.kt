package com.pauta.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms the daily reminder alarms after a reboot or app update — alarms don't
 * survive either. // PT: re-arma os lembretes após reinício ou atualização.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            -> ReminderScheduler.reschedule(context)
        }
    }
}
