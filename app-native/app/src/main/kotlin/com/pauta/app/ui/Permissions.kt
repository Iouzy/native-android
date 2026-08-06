package com.pauta.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pauta.app.ui.viewmodel.AppViewModel

/**
 * N1 · the one place that owns "may we post a notification, and have we ever
 * asked?".
 *
 * The app posts from three places — the focus/reading service, the three daily
 * reminders and the per-habit reminders — and on a clean Android 13+ install
 * every one of them was dropped by the OS in silence, because the only code that
 * requested `POST_NOTIFICATIONS` sat behind a Settings toggle. The request now
 * happens at the moment the app is about to make the promise (starting a block,
 * turning a reminder on), never at launch, and never more than once: Android
 * shows that dialog a single time, so a second request is noise rather than a
 * second chance.
 *
 * // PT: o único sítio que sabe se podemos notificar e se já perguntámos. Pede-se
 * no momento em que a app promete um aviso — nunca ao arrancar, nunca duas vezes.
 */
object NotificationAccess {

    /** Android 13+ gates notifications behind a runtime permission; below it the
     *  grant is implicit at install. // PT: só o Android 13+ pede permissão. */
    val runtimeGated: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** The permission alone. // PT: apenas a permissão. */
    fun granted(context: Context): Boolean =
        !runtimeGated || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * What the OS will actually do, which is the only question worth asking. A
     * user can switch the app's notifications off — or silence the channel —
     * without ever touching the permission, and the Settings row has to tell the
     * truth in that case too. // PT: o que o sistema faz mesmo, não só a permissão.
     */
    fun enabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * We asked and the answer is still no. Only then is the Settings row a dead
     * end the user has to leave the app to reopen — before we have asked, the
     * toggle itself is the door. // PT: já pedimos e continua não; só aí a linha
     * das definições manda o utilizador ao sistema.
     */
    fun blocked(notifAskedAt: Long, enabled: Boolean): Boolean =
        notifAskedAt > 0L && !enabled

    /**
     * The system's own notification page for this app. Falls back to the app
     * details page on the (rare) device without the notification screen, so the
     * link is never a tap that does nothing — the defect this task exists to fix.
     * // PT: abre as definições de notificações do sistema, com recurso à página
     * da app se não existirem.
     */
    fun openSettings(context: Context) {
        val notifications = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(notifications) }.isSuccess) return
        val details = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(details) }
    }
}

/**
 * N1 · returns the "ask once, now" action for a screen that is about to promise a
 * notification. Call it from the gesture itself (starting a block, enabling a
 * reminder) — it is a no-op on every call after the first, and on a device that
 * already grants notifications.
 *
 * The pref is written whether or not the dialog is shown, because it records that
 * the app has been past this moment, not that a dialog appeared: on Android 12
 * and below there is no dialog at all, and on 13+ a permanently-denied permission
 * returns without one.
 *
 * // PT: devolve a acção "pedir uma vez"; a marca fica gravada mesmo quando não
 * há diálogo, porque marca o momento e não o diálogo.
 */
@Composable
fun rememberNotificationAsk(vm: AppViewModel, notifAskedAt: Long): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    return remember(notifAskedAt, context, vm) {
        {
            if (notifAskedAt == 0L) {
                vm.markNotifAsked()
                if (NotificationAccess.runtimeGated && !NotificationAccess.granted(context)) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}
