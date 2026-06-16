package com.pauta.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pauta.app.i18n.I18n
import com.pauta.app.i18n.Lang
import com.pauta.app.ui.theme.LocalPautaColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

/** A bilingual one-liner Pip can say. */
private data class Phrase(val pt: String, val en: String)

private val GENERAL = listOf(
    Phrase("Pequenos passos, marés grandes.", "Small steps, big tides."),
    Phrase("Começar mal feito é melhor que não começar.", "Badly started beats not started."),
    Phrase("Sabias? Um hábito forma-se em média em 66 dias.", "Did you know? A habit takes ~66 days to form."),
    Phrase("O calendário à vista puxa a corrente.", "A visible calendar pulls the current."),
    Phrase("Repetição é a mãe da constância.", "Repetition is the mother of consistency."),
    Phrase("Os papagaios também planeiam o dia. Eu, pelo menos.", "Parrots plan their day too. This one does."),
    Phrase("\"Conhece-te a ti mesmo.\" — e ao teu sono.", "\"Know thyself\" — and thy sleep."),
    Phrase("Heraclito: nunca entras na mesma maré duas vezes.", "Heraclitus: never the same tide twice."),
)
private val HOJE = listOf(
    Phrase("Uma intenção principal vale mais que dez vagas.", "One key intention beats ten vague ones."),
    Phrase("A reflexão da noite ensina o dia seguinte.", "Tonight's reflection teaches tomorrow."),
    Phrase("Prioridade 1: o que farias se só desse para uma?", "Priority 1: the one you'd keep if only one."),
)
private val PAUTA = listOf(
    Phrase("Aparecer já é metade do foco.", "Showing up is half the focus."),
    Phrase("Pausar não é desistir; é respirar.", "Pausing isn't quitting; it's breathing."),
    Phrase("25 minutos sem distrações fazem maravilhas.", "25 undistracted minutes work wonders."),
)
private val MARES = listOf(
    Phrase("Constância vence intensidade.", "Consistency beats intensity."),
    Phrase("Um respiro honesto vale uma semana de mentira.", "An honest rest beats a week of pretending."),
    Phrase("A maré sobe um dia de cada vez.", "The tide rises one day at a time."),
)

private fun poolFor(tab: Tab): List<Phrase> = when (tab) {
    Tab.HOJE -> HOJE + GENERAL
    Tab.PAUTA -> PAUTA + GENERAL
    Tab.MARES -> MARES + GENERAL
}

private fun pickFresh(tab: Tab, recent: List<String>): Phrase {
    val pool = poolFor(tab)
    val fresh = pool.filter { it.pt !in recent }
    val from = fresh.ifEmpty { pool }
    return from[Random.nextInt(from.size)]
}

/** F2: Pip's steady context moods, read off real app state. The celebratory
 *  flutter (the day's last intention completing) isn't a steady mood — it's a
 *  one-shot edge handled separately below. // PT: humores do Pip conforme o estado
 *  real da app. */
private enum class PipMood { IDLE, WATCHING, SLEEPY }

/**
 * Pip — a small parrot companion in the bottom corner. He breathes and blinks
 * continuously (a JS-style per-frame loop via [withFrameMillis]), pops out with a
 * tab-aware bilingual one-liner when tapped, and recoils after a few seconds. The
 * web "peek" is adapted to a scale here (no tab-bar to clip against). Hidden when
 * the parrot preference is off.
 *
 * F2 makes him context-aware: a [PipMood.WATCHING] gaze while a focus block runs,
 * [PipMood.SLEEPY] half-lids after the reflection hour, and a single celebratory
 * flutter when the day's last intention is ticked. With [reducedMotion] on he
 * holds a static pose — no breath, blink, spring or flutter — so the accessibility
 * preference is honoured. // PT: o Pip — respira, fala ao toque, e agora reage ao
 * estado (a observar / sonolento / festeja); estático com "movimento reduzido".
 *
 * @param activeFocus a focus block is running → calm watching.
 * @param allIntentionsDone today's last intention just completed → one flutter on
 *   the false→true edge.
 * @param reflectionTime the user's "HH:MM" reflection hour → sleepy once past it.
 */
@Composable
fun ParrotCompanion(
    tab: Tab,
    activeFocus: Boolean,
    allIntentionsDone: Boolean,
    reflectionTime: String,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPautaColors.current
    var out by remember { mutableStateOf(false) }
    var line by remember { mutableStateOf<Phrase?>(null) }
    val recent = remember { ArrayDeque<String>() }

    val breath = remember { mutableFloatStateOf(1f) }
    val eyeSquash = remember { mutableFloatStateOf(1f) }
    val p = remember { mutableFloatStateOf(0f) } // 0 = idle/peeked, 1 = out/speaking

    // Past the reflection hour? Re-checked each minute so Pip grows sleepy in the
    // evening without a restart (works under reduced motion too, where the frame
    // loop below is idle). // PT: já passou a hora da reflexão? — a cada minuto.
    var afterReflection by remember(reflectionTime) { mutableStateOf(pastReflectionHour(reflectionTime)) }
    LaunchedEffect(reflectionTime) {
        while (true) {
            afterReflection = pastReflectionHour(reflectionTime)
            delay(60_000)
        }
    }

    // The steady mood: an active focus block wins (calm watching); otherwise, past
    // the reflection hour → sleepy; else idle. // PT: humor base.
    val mood = when {
        activeFocus -> PipMood.WATCHING
        afterReflection -> PipMood.SLEEPY
        else -> PipMood.IDLE
    }
    // Static pose params from the mood: half-lidded eyes when sleepy; a soft
    // up-left gaze toward the work when watching. Safe to apply with motion off.
    // // PT: parâmetros de pose — pálpebras descidas / olhar para o trabalho.
    val lid = if (mood == PipMood.SLEEPY) 0.42f else 1f
    val gazeX = if (mood == PipMood.WATCHING) -0.16f else 0f
    val gazeY = when (mood) {
        PipMood.WATCHING -> -0.12f
        PipMood.SLEEPY -> 0.10f
        else -> 0f
    }

    // A single celebratory flutter on the false→true edge of [allIntentionsDone] —
    // the day's last intention being ticked. Not fired on first composition (so
    // opening an already-finished day stays calm), and skipped under reduced
    // motion. The edge detector only *bumps* a trigger; a separate effect plays the
    // hop to completion, so re-adding an intention right after (flipping the flag
    // back) can't strand the flutter mid-air. // PT: um adejo único quando a última
    // intenção do dia fica feita — disparo e animação separados, para nunca encravar.
    val flutter = remember { Animatable(0f) }
    var celebrateTrigger by remember { mutableIntStateOf(0) }
    var primed by remember { mutableStateOf(false) }
    LaunchedEffect(allIntentionsDone) {
        if (!primed) { primed = true; return@LaunchedEffect }
        if (allIntentionsDone && !reducedMotion) celebrateTrigger++
    }
    LaunchedEffect(celebrateTrigger) {
        if (celebrateTrigger == 0) return@LaunchedEffect
        flutter.snapTo(0f)
        flutter.animateTo(1f, animationSpec = tween(1100))
        flutter.snapTo(0f)
    }

    // Auto-recoil a few seconds after speaking.
    LaunchedEffect(out, line) {
        if (out) {
            delay(4500)
            out = false
        }
    }

    // The motion loop: breathing (continuous), blink (randomised), and the spring
    // toward the peek/out target — all written every frame. It runs only when
    // motion is allowed; under reduced motion Pip holds the static pose below.
    // // PT: o ciclo de movimento só corre com animações ligadas.
    val lidState = rememberUpdatedState(lid)
    // Breath calms as the mood settles: idle → watching → sleepy. // PT: a
    // respiração acalma conforme o humor (parado → a observar → sonolento).
    val breathFreqState = rememberUpdatedState(
        when (mood) {
            PipMood.SLEEPY -> 0.9
            PipMood.WATCHING -> 1.1
            PipMood.IDLE -> 1.5
        },
    )
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var nextBlink = 0L
        var blinkUntil = 0L
        while (true) {
            withFrameMillis { nowMs ->
                val target = if (out) 1f else 0f
                p.floatValue += (target - p.floatValue) * 0.16f
                breath.floatValue = 1f + sin(nowMs / 1000.0 * breathFreqState.value).toFloat() * 0.012f
                if (nextBlink == 0L) nextBlink = nowMs + 2500 + Random.nextLong(0, 3500)
                if (blinkUntil == 0L && nowMs >= nextBlink) blinkUntil = nowMs + 130
                // Eyes spring wide during the celebratory flutter; otherwise rest at
                // the mood's lid level. // PT: olhos arregalados ao festejar.
                val restLid = if (flutter.value > 0f) 1f else lidState.value
                if (blinkUntil != 0L) {
                    val bp = (1f - (blinkUntil - nowMs) / 130f).coerceIn(0f, 1f)
                    eyeSquash.floatValue = restLid * (1f - sin(PI * bp).toFloat() * 0.9f)
                    if (nowMs >= blinkUntil) {
                        blinkUntil = 0L
                        nextBlink = nowMs + 2800 + Random.nextLong(0, 4200)
                    }
                } else {
                    eyeSquash.floatValue = restLid
                }
            }
        }
    }

    Box(modifier, contentAlignment = Alignment.BottomEnd) {
        Column(horizontalAlignment = Alignment.End) {
            // The bubble (and scale) snap to their target under reduced motion.
            val pv = if (reducedMotion) (if (out) 1f else 0f) else p.floatValue
            line?.let { phrase ->
                if (pv > 0.04f) {
                    SpeechBubble(text = if (I18n.lang == Lang.EN) phrase.en else phrase.pt, alpha = pv)
                    Spacer(Modifier.height(6.dp))
                }
            }
            Canvas(
                modifier = Modifier
                    .size(96.dp, 84.dp)
                    .graphicsLayer {
                        val sv = if (reducedMotion) (if (out) 1f else 0f) else p.floatValue
                        val br = if (reducedMotion) 1f else breath.floatValue
                        val scale = 0.82f + 0.18f * sv
                        scaleX = scale
                        scaleY = scale * br
                        // Celebrate: a few decaying hops + a small wing-wiggle rotation.
                        val f = if (reducedMotion) 0f else flutter.value
                        if (f > 0f) {
                            val decay = 1f - f
                            translationY = -abs(sin(f * PI * 3f).toFloat()) * decay * size.height * 0.16f
                            rotationZ = sin(f * PI * 4f).toFloat() * decay * 5f
                        }
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .clickableNoRipple {
                        out = true
                        val ph = pickFresh(tab, recent.toList())
                        line = ph
                        recent.addLast(ph.pt)
                        while (recent.size > 8) recent.removeFirst()
                    },
            ) {
                val eye = if (reducedMotion) lid else eyeSquash.floatValue
                drawParrot(headColor = colors.accent, eyeOpen = eye, gazeX = gazeX, gazeY = gazeY)
            }
        }
    }
}

/** F2: a small static Pip pose for the tabs' empty states — a calm, eyes-open
 *  look beside the empty phrase. The parrot preference is honoured at the call
 *  site (hidden when off); being static, it needs no reduced-motion branch.
 *  // PT: pose pequena e estática do Pip para os estados vazios. */
@Composable
fun PipPose(modifier: Modifier = Modifier, height: Dp = 40.dp) {
    val colors = LocalPautaColors.current
    // Keep Pip's 96:84 head proportions so the pose isn't distorted.
    Canvas(modifier.size(height * (96f / 84f), height)) {
        drawParrot(headColor = colors.accent, eyeOpen = 1f)
    }
}

/** True once the local clock has passed the user's reflection time ("HH:MM").
 *  Malformed input is treated as not-yet, so Pip stays awake. // PT: já passou a
 *  hora da reflexão? */
private fun pastReflectionHour(hhmm: String): Boolean {
    val parts = hhmm.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return false
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return false
    val now = java.time.LocalTime.now()
    return now.hour * 60 + now.minute >= h * 60 + m
}

@Composable
private fun SpeechBubble(text: String, alpha: Float) {
    val colors = LocalPautaColors.current
    Box(
        Modifier
            .graphicsLayer { this.alpha = alpha }
            .widthIn(max = 220.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.paper2)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        androidx.compose.material3.Text(
            text = text,
            color = colors.ink,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

/** Draws Pip's head: accent skull, a hooked beak, a crown tuft, and an eye that
 *  opens/half-lids ([eyeOpen]) and can shift its gaze ([gazeX]/[gazeY], fractions
 *  of the eye radius). Coordinates are fractions of the canvas so it scales
 *  cleanly. */
private fun DrawScope.drawParrot(headColor: Color, eyeOpen: Float, gazeX: Float = 0f, gazeY: Float = 0f) {
    val w = size.width
    val h = size.height
    val face = Color(0xFFF1EADA)
    val beakUp = Color(0xFFE8D6AC)
    val beakLo = Color(0xFF6E5F50)
    val eyeWhite = Color(0xFFF7F1E4)
    val pupil = Color(0xFF2A1E14)

    val cx = w * 0.60f
    val cy = h * 0.50f
    val r = w * 0.30f

    // Crown tuft.
    val crown = Path().apply {
        moveTo(cx - r * 0.5f, cy - r * 0.7f)
        cubicTo(cx - r * 0.2f, cy - r * 1.5f, cx + r * 0.4f, cy - r * 1.4f, cx + r * 0.5f, cy - r * 0.6f)
        close()
    }
    drawPath(crown, headColor.copy(alpha = 0.9f))

    // Head.
    drawCircle(headColor, radius = r, center = Offset(cx, cy))

    // Bare face patch.
    drawCircle(face, radius = r * 0.62f, center = Offset(cx + r * 0.18f, cy + r * 0.05f))

    // Hooked beak, pointing left.
    val beak = Path().apply {
        moveTo(cx - r * 0.5f, cy - r * 0.15f)
        cubicTo(cx - r * 1.25f, cy - r * 0.2f, cx - r * 1.45f, cy + r * 0.25f, cx - r * 1.05f, cy + r * 0.5f)
        cubicTo(cx - r * 0.85f, cy + r * 0.62f, cx - r * 0.55f, cy + r * 0.5f, cx - r * 0.45f, cy + r * 0.3f)
        close()
    }
    drawPath(beak, beakUp)
    // Lower mandible shadow.
    val lower = Path().apply {
        moveTo(cx - r * 1.05f, cy + r * 0.5f)
        cubicTo(cx - r * 0.85f, cy + r * 0.66f, cx - r * 0.6f, cy + r * 0.58f, cx - r * 0.5f, cy + r * 0.42f)
        cubicTo(cx - r * 0.7f, cy + r * 0.55f, cx - r * 0.9f, cy + r * 0.58f, cx - r * 1.05f, cy + r * 0.5f)
        close()
    }
    drawPath(lower, beakLo)

    // Eye — opens/half-lids vertically (eyeOpen) and squashes around its own centre
    // on blink; the pupil + highlight shift by the gaze. // PT: o olho abre/semicerra
    // e desvia o olhar (modo "a observar").
    val eyeCx = cx + r * 0.28f
    val eyeCy = cy - r * 0.18f
    scale(scaleX = 1f, scaleY = eyeOpen, pivot = Offset(eyeCx, eyeCy)) {
        drawCircle(eyeWhite, radius = r * 0.30f, center = Offset(eyeCx, eyeCy))
        drawCircle(pupil, radius = r * 0.18f, center = Offset(eyeCx + r * 0.04f + r * gazeX, eyeCy + r * gazeY))
        drawCircle(Color.White, radius = r * 0.06f, center = Offset(eyeCx + r * 0.10f + r * gazeX, eyeCy - r * 0.06f + r * gazeY))
    }
}
