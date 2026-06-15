package com.pauta.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.pauta.app.MainActivity
import com.pauta.app.R
import com.pauta.app.data.PautaRepository
import com.pauta.app.data.entity.HabitEntity
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.HabitCalculator.DayState
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.TideToday
import com.pauta.app.ui.computeTodayTides
import kotlinx.coroutines.flow.first

/**
 * C2: builds and posts the *actionable* reminder notifications. The planner nudge
 * is a plain tap-to-open; the reflection reminder carries a [RemoteInput]
 * direct-reply that writes the night's reflection straight from the shade; the
 * habits reminder lists today's still-open tides inbox-style, each of the first
 * few with a quick "done" action that marks it via the repository without opening
 * the app. The receivers own the coroutine/goAsync plumbing — this object only
 * assembles and posts (and shares one builder so they can't drift). // PT:
 * notificações de lembrete acionáveis — resposta direta na reflexão, ações
 * rápidas "feito" nas marés; este objeto só monta e mostra.
 */
object ReminderNotifications {
    // Direct-reply + quick-action contract. These PendingIntents target
    // [ReminderActionReceiver] by class (explicit), so they need no manifest
    // intent-filter — only the receiver's registration. // PT: intents explícitos.
    const val ACTION_REFLECTION_REPLY = "com.pauta.app.REFLECTION_REPLY"
    const val ACTION_HABIT_DONE = "com.pauta.app.HABIT_DONE"
    const val KEY_REFLECTION = "reflection"
    const val EXTRA_HABIT_ID = "habitId"

    /** Android renders at most three notification actions, so cap the inline
     *  "done" buttons there; the inbox lists the rest. */
    private const val MAX_TIDE_ACTIONS = 3

    /** Today's still-open tides — the same `todayTides` slice the Hoje strip and
     *  the widget use, filtered to the ones still pending (not done, not respiro).
     *  A one-shot read of the reactive flows, off the main thread. // PT: as marés
     *  de hoje ainda por completar (leitura pontual dos flows). */
    suspend fun pendingTides(repo: PautaRepository): List<TideToday> {
        val today = DateUtils.todayKey()
        return computeTodayTides(
            habits = repo.habits().first(),
            logs = repo.habitLogs().first(),
            respiros = repo.habitRespiros().first(),
            counts = repo.habitCounts().first(),
            today = today,
        ).filter { it.state == DayState.EMPTY }
    }

    /** The global habits digest covers only tides WITHOUT their own per-habit clock
     *  reminder (D2): a timed tide is nudged individually at its clock by
     *  [postHabitReminder], so listing it here too would double-remind. // PT: o
     *  resumo geral só inclui marés sem hora própria — as com hora são avisadas à
     *  parte. */
    suspend fun digestTides(repo: PautaRepository): List<TideToday> =
        pendingTides(repo).filter { it.habit.clock.isBlank() }

    /** "Plan your day" — unchanged from the original: tap to open. */
    fun postPlanner(context: Context) {
        val (title, body) = ReminderScheduler.titleBody(context, ReminderScheduler.Kind.PLANNER)
        val code = ReminderScheduler.Kind.PLANNER.code
        notify(context, code, base(context, title, body, code).build())
    }

    /**
     * The nightly reflection reminder. With [savedText] null it prompts and
     * carries the direct-reply action; after a reply it re-posts as a quiet
     * confirmation showing what was saved, with the reply action gone (so it's a
     * one-shot, never an overwrite loop). // PT: lembrete de reflexão com resposta
     * direta; depois de responder, vira confirmação do que ficou guardado.
     */
    fun postReflection(context: Context, savedText: String?) {
        val code = ReminderScheduler.Kind.REFLECTION.code
        if (savedText == null) {
            val (title, body) = ReminderScheduler.titleBody(context, ReminderScheduler.Kind.REFLECTION)
            val remoteInput = RemoteInput.Builder(KEY_REFLECTION)
                .setLabel(tr("Escreve a tua reflexão…"))
                .build()
            val replyIntent = Intent(context, ReminderActionReceiver::class.java).setAction(ACTION_REFLECTION_REPLY)
            // RemoteInput needs a MUTABLE PendingIntent so the system can inject the
            // typed text. // PT: o RemoteInput exige um PendingIntent mutável.
            val replyPi = PendingIntent.getBroadcast(context, code, replyIntent, mutableFlags())
            val action = NotificationCompat.Action.Builder(0, tr("Responder"), replyPi)
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(false)
                .build()
            notify(
                context, code,
                base(context, title, body, code)
                    .setOnlyAlertOnce(true)
                    .addAction(action)
                    .build(),
            )
        } else {
            notify(
                context, code,
                base(context, tr("Reflexão guardada"), savedText, code)
                    .setOnlyAlertOnce(true)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(savedText))
                    .build(),
            )
        }
    }

    /**
     * The habits reminder: an inbox of today's pending tides, with up to three
     * inline "done" actions (one per tide). With nothing pending it cancels any
     * existing notification — covering both a fire with everything already done
     * and the moment the last tide is marked from an action. // PT: caixa de
     * entrada das marés por completar, com ações rápidas; some quando não há nada.
     */
    fun postHabits(context: Context, tides: List<TideToday>) {
        val code = ReminderScheduler.Kind.HABITS.code
        if (tides.isEmpty()) {
            runCatching { NotificationManagerCompat.from(context).cancel(code) }
            return
        }
        val (title, _) = ReminderScheduler.titleBody(context, ReminderScheduler.Kind.HABITS)
        val summary = if (tides.size == 1) tr("1 maré por completar")
        else trf("{n} marés por completar", "n" to tides.size)
        val inbox = NotificationCompat.InboxStyle().setBigContentTitle(title).setSummaryText(summary)
        tides.forEach { inbox.addLine(it.habit.name) }
        val builder = base(context, title, summary, code)
            .setOnlyAlertOnce(true)
            .setStyle(inbox)
        // One quick "done" per tide (capped at three): distinct request codes by
        // habit id keep the PendingIntents from collapsing into one. // PT: uma ação
        // "feito" por maré, com requestCodes distintos por id.
        tides.take(MAX_TIDE_ACTIONS).forEach { tide ->
            val intent = Intent(context, ReminderActionReceiver::class.java)
                .setAction(ACTION_HABIT_DONE)
                .putExtra(EXTRA_HABIT_ID, tide.habit.id)
            val pi = PendingIntent.getBroadcast(context, tide.habit.id.hashCode(), intent, immutableFlags())
            builder.addAction(0, "✓ ${tide.habit.name}", pi)
        }
        notify(context, code, builder.build())
    }

    /**
     * D2: a single tide's own-clock reminder — its name with one quick "done"
     * action, reusing the global habits reminder's [ReminderActionReceiver] plumbing
     * (same [ACTION_HABIT_DONE] / [EXTRA_HABIT_ID], so marking from here works with
     * the app closed and the receiver clears this notification afterwards). Its
     * notification id + request code come from the habit id, distinct from the global
     * digest's id, so a tide can carry both without either replacing the other.
     * // PT: lembrete de uma maré específica, com ação "feito"; id próprio, não choca
     * com o resumo geral.
     */
    fun postHabitReminder(context: Context, habit: HabitEntity) {
        val id = habitNotifId(habit.id)
        val doneIntent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(ACTION_HABIT_DONE)
            .putExtra(EXTRA_HABIT_ID, habit.id)
        val donePi = PendingIntent.getBroadcast(context, id, doneIntent, immutableFlags())
        notify(
            context, id,
            base(context, habit.name, tr("Está na hora desta maré."), id)
                .addAction(0, "✓ " + tr("Feito"), donePi)
                .build(),
        )
    }

    /** Clear a tide's per-habit reminder once it's marked done (from the action or
     *  in-app) or has gone off-schedule. A no-op when no such notification is live —
     *  e.g. when the "done" action came from the global digest. // PT: limpa o
     *  lembrete da maré quando já não faz sentido. */
    fun cancelHabitReminder(context: Context, habitId: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(habitNotifId(habitId)) }
    }

    /** The per-habit reminder's notification id (and quick-action request code),
     *  derived from the habit id so each tide's reminder stays distinct from the
     *  others and from the global digest. // PT: id da notificação por maré. */
    fun habitNotifId(habitId: String): Int = habitId.hashCode()

    // ── shared bits ───────────────────────────────────────────
    /** The common notification skeleton (icon, title/body, channel, tap-to-open).
     *  Each reminder kind keeps its own id so same-kind posts replace each other. */
    private fun base(context: Context, title: String, body: String, code: Int): NotificationCompat.Builder {
        ReminderScheduler.ensureChannel(context)
        return NotificationCompat.Builder(context, ReminderScheduler.channelId())
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(context, code))
    }

    private fun openApp(context: Context, code: Int): PendingIntent =
        PendingIntent.getActivity(
            context, code,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            immutableFlags(),
        )

    private fun notify(context: Context, id: Int, notif: Notification) {
        runCatching { NotificationManagerCompat.from(context).notify(id, notif) }
    }

    private fun immutableFlags(): Int {
        var f = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) f = f or PendingIntent.FLAG_IMMUTABLE
        return f
    }

    private fun mutableFlags(): Int {
        var f = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) f = f or PendingIntent.FLAG_MUTABLE
        return f
    }
}
