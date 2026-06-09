package com.pauta.app.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pauta.app.i18n.I18n
import com.pauta.app.i18n.Lang
import com.pauta.app.ui.theme.LocalPautaColors
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

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

/**
 * Pip — a small parrot companion in the bottom corner. He breathes and blinks
 * continuously (a JS-style per-frame loop via [withFrameMillis], immune to the
 * OS "remove animations" setting), and pops out with a tab-aware bilingual
 * one-liner when tapped, recoiling after a few seconds. The web "peek" is
 * adapted to a scale here (no tab-bar to clip against). Hidden when the parrot
 * preference is off. // PT: o Pip — respira, pestaneja e fala ao toque.
 */
@Composable
fun ParrotCompanion(tab: Tab, modifier: Modifier = Modifier) {
    val colors = LocalPautaColors.current
    var out by remember { mutableStateOf(false) }
    var line by remember { mutableStateOf<Phrase?>(null) }
    val recent = remember { ArrayDeque<String>() }

    val breath = remember { mutableFloatStateOf(1f) }
    val eyeSquash = remember { mutableFloatStateOf(1f) }
    val p = remember { mutableFloatStateOf(0f) } // 0 = idle/peeked, 1 = out/speaking

    // Auto-recoil a few seconds after speaking.
    LaunchedEffect(out, line) {
        if (out) {
            kotlinx.coroutines.delay(4500)
            out = false
        }
    }

    // The motion loop: breathing (continuous), blink (randomised), and the
    // spring toward the peek/out target — all written every frame.
    LaunchedEffect(Unit) {
        var nextBlink = 0L
        var blinkUntil = 0L
        while (true) {
            withFrameMillis { nowMs ->
                val target = if (out) 1f else 0f
                p.floatValue += (target - p.floatValue) * 0.16f
                breath.floatValue = 1f + sin(nowMs / 1000.0 * 1.5).toFloat() * 0.012f
                if (nextBlink == 0L) nextBlink = nowMs + 2500 + Random.nextLong(0, 3500)
                if (blinkUntil == 0L && nowMs >= nextBlink) blinkUntil = nowMs + 130
                if (blinkUntil != 0L) {
                    val bp = (1f - (blinkUntil - nowMs) / 130f).coerceIn(0f, 1f)
                    eyeSquash.floatValue = (1f - sin(PI * bp).toFloat() * 0.9f)
                    if (nowMs >= blinkUntil) {
                        blinkUntil = 0L
                        nextBlink = nowMs + 2800 + Random.nextLong(0, 4200)
                    }
                } else {
                    eyeSquash.floatValue = 1f
                }
            }
        }
    }

    Box(modifier, contentAlignment = Alignment.BottomEnd) {
        Column(horizontalAlignment = Alignment.End) {
            val pv = p.floatValue
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
                        val scale = 0.82f + 0.18f * pv
                        scaleX = scale
                        scaleY = scale * breath.floatValue
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
                drawParrot(headColor = colors.accent, eyeSquash = eyeSquash.floatValue)
            }
        }
    }
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

/** Draws Pip's head: accent skull, a hooked beak, a crown tuft, and a blinking
 *  eye. Coordinates are fractions of the canvas so it scales cleanly. */
private fun DrawScope.drawParrot(headColor: Color, eyeSquash: Float) {
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

    // Eye — squashes vertically on blink, around its own centre.
    val eyeCx = cx + r * 0.28f
    val eyeCy = cy - r * 0.18f
    scale(scaleX = 1f, scaleY = eyeSquash, pivot = Offset(eyeCx, eyeCy)) {
        drawCircle(eyeWhite, radius = r * 0.30f, center = Offset(eyeCx, eyeCy))
        drawCircle(pupil, radius = r * 0.18f, center = Offset(eyeCx + r * 0.04f, eyeCy))
        drawCircle(Color.White, radius = r * 0.06f, center = Offset(eyeCx + r * 0.10f, eyeCy - r * 0.06f))
    }
}
