package com.pauta.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pauta.app.domain.HabitCalculator
import com.pauta.app.domain.HabitModel
import com.pauta.app.i18n.I18n
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.PautaSheet
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.SerifFamily
import java.time.YearMonth

/** One month's point on the wave. */
private data class TrendMonth(val year: Int, val month: Int, val pct: Int?)

/**
 * "Marés Passadas" — the 12-month (up to 18) wave of overall consistency,
 * ported from mares-sheets.jsx TrendSheet/WaveChart: a smooth accent wave with
 * a soft gradient fill, dashed gridlines at 25/50/75, a tappable point + label
 * per month, the média/alta/baixa stat row and the navigator-rank card.
 * Picking a month offers "abrir este mês →", which jumps the grid there.
 * // PT: a onda dos últimos meses + o posto do navegante.
 */
@Composable
fun TrendSheet(
    habits: List<HabitModel>,
    today: String,
    onPickMonth: (year: Int, month: Int) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalPautaColors.current
    val accent = colors.accent

    // Months from the earliest habit creation → today (most recent 18), padded
    // to at least 3 so a young account still reads as a wave.
    val months = remember(habits, today) {
        val now = YearMonth.parse(today.substring(0, 7))
        val earliest = habits.minOfOrNull { it.createdAt }
            ?.let { YearMonth.from(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())) }
            ?: now
        val all = mutableListOf<YearMonth>()
        var ym = now
        while (ym >= earliest && all.size < 18) { all += ym; ym = ym.minusMonths(1) }
        while (all.size < 3) all += all.last().minusMonths(1)
        all.reverse()
        all.map { TrendMonth(it.year, it.monthValue, HabitCalculator.overallPctInMonth(habits, it.year, it.monthValue, today)) }
    }
    val valid = months.filter { it.pct != null }
    val best = valid.maxByOrNull { it.pct!! }
    val worst = valid.minByOrNull { it.pct!! }
    val avg = if (valid.isEmpty()) null else valid.sumOf { it.pct!! } / valid.size

    var picked by remember { mutableStateOf<TrendMonth?>(null) }

    val totalDone = HabitCalculator.totalDoneDays(habits)
    val level = HabitCalculator.navigatorLevel(totalDone)
    val next = HabitCalculator.NAVIGATOR_LEVELS.lastOrNull { it.min > totalDone }

    PautaSheet(title = tr("Marés Passadas"), onClose = onClose) {
        Text(
            text = tr("As suas marés ao longo do ano."),
            color = colors.ink,
            fontFamily = SerifFamily,
            fontSize = 22.sp,
            lineHeight = 27.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = tr("Sobem e descem. Cada onda é um mês. Toque para ver as marés desse mês."),
            color = colors.ink3,
            fontFamily = SerifFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 14.sp,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(24.dp))

        if (valid.size < 2) {
            Text(
                text = tr("Ainda não há marés suficientes para mostrar uma onda.") + "\n" +
                    tr("Volte aqui dentro de alguns meses."),
                color = colors.ink3,
                fontFamily = SerifFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
            )
        } else {
            // ── the wave ──
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .pointerInput(months) {
                        detectTapGestures { offset ->
                            val padX = 16.dp.toPx()
                            val innerW = size.width - padX * 2
                            val n = months.size
                            val idx = (((offset.x - padX) / innerW) * (n - 1)).let { Math.round(it) }
                                .coerceIn(0, n - 1)
                            val m = months[idx]
                            if (m.pct != null) picked = if (picked == m) null else m
                        }
                    },
            ) {
                val padX = 16.dp.toPx()
                val padY = 24.dp.toPx()
                val innerW = size.width - padX * 2
                val innerH = size.height - padY * 2
                val baseY = size.height - padY
                val n = months.size

                fun cx(i: Int) = padX + if (n == 1) innerW / 2 else (i.toFloat() / (n - 1)) * innerW
                fun cy(pct: Int) = padY + (1 - pct / 100f) * innerH

                // Dashed gridlines at 25/50/75.
                for (v in listOf(25, 50, 75)) {
                    val y = cy(v)
                    drawLine(
                        color = colors.rule,
                        start = Offset(padX, y),
                        end = Offset(size.width - padX, y),
                        strokeWidth = 0.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
                    )
                }

                // Consecutive non-null runs → smooth Catmull-Rom-ish segments.
                data class Pt(val x: Float, val y: Float)
                val runs = mutableListOf<List<Pt>>()
                var run = mutableListOf<Pt>()
                months.forEachIndexed { i, m ->
                    if (m.pct == null) {
                        if (run.size >= 2) runs += run
                        run = mutableListOf()
                    } else run += Pt(cx(i), cy(m.pct))
                }
                if (run.size >= 2) runs += run

                for (seg in runs) {
                    val path = Path().apply {
                        moveTo(seg[0].x, seg[0].y)
                        for (i in 0 until seg.size - 1) {
                            val p0 = seg[maxOf(0, i - 1)]
                            val p1 = seg[i]
                            val p2 = seg[i + 1]
                            val p3 = seg[minOf(seg.size - 1, i + 2)]
                            cubicTo(
                                p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6,
                                p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6,
                                p2.x, p2.y,
                            )
                        }
                    }
                    val fill = Path().apply {
                        addPath(path)
                        lineTo(seg.last().x, baseY)
                        lineTo(seg.first().x, baseY)
                        close()
                    }
                    drawPath(
                        fill,
                        brush = Brush.verticalGradient(
                            colors = listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.02f)),
                            startY = padY,
                            endY = baseY,
                        ),
                    )
                    drawPath(path, color = accent, style = Stroke(width = 2.dp.toPx()))
                }

                // Points.
                months.forEachIndexed { i, m ->
                    if (m.pct == null) return@forEachIndexed
                    val c = Offset(cx(i), cy(m.pct))
                    val isPicked = picked == m
                    if (isPicked) drawCircle(accent.copy(alpha = 0.18f), radius = 8.dp.toPx(), center = c)
                    drawCircle(
                        color = if (isPicked) accent else colors.paper,
                        radius = (if (isPicked) 4.5 else 3.0).dp.toPx(),
                        center = c,
                    )
                    drawCircle(accent, radius = (if (isPicked) 4.5 else 3.0).dp.toPx(), center = c, style = Stroke(1.5.dp.toPx()))
                }
            }

            // Month labels + pct under the wave.
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                months.forEach { m ->
                    val isPicked = picked == m
                    Column(
                        Modifier
                            .weight(1f)
                            .clickableNoRipple { if (m.pct != null) picked = if (picked == m) null else m }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = I18n.fmtMonthShort(m.month),
                            color = when {
                                isPicked -> accent
                                m.pct == null -> colors.ink4
                                else -> colors.ink3
                            },
                            fontFamily = MonoFamily,
                            fontSize = 9.sp,
                            fontWeight = if (isPicked) FontWeight.SemiBold else FontWeight.Normal,
                            letterSpacing = 0.45.sp,
                        )
                        if (m.pct != null) {
                            Text(
                                text = m.pct.toString(),
                                color = if (isPicked) accent else colors.ink2,
                                fontFamily = MonoFamily,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }

            // Picked month → jump the grid there.
            picked?.let { p ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = I18n.fmtMonthYear(p.year, p.month) + "  ·  " + tr("abrir este mês") + " →",
                    color = accent,
                    fontFamily = MonoFamily,
                    fontSize = 11.sp,
                    letterSpacing = 0.44.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableNoRipple { onPickMonth(p.year, p.month); onClose() }
                        .padding(vertical = 6.dp),
                )
            }

            // ── média / alta / baixa ──
            Spacer(Modifier.height(28.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, colors.rule, RoundedCornerShape(8.dp))
                    .background(colors.rule),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(1.dp),
            ) {
                TrendStat(tr("Maré média"), avg?.let { "$it%" } ?: "—", "", Modifier.weight(1f))
                TrendStat(tr("Maré alta"), best?.let { "${it.pct}%" } ?: "—", best?.let { I18n.fmtMonthShort(it.month) } ?: "", Modifier.weight(1f))
                TrendStat(tr("Maré baixa"), worst?.let { "${it.pct}%" } ?: "—", worst?.let { I18n.fmtMonthShort(it.month) } ?: "", Modifier.weight(1f))
            }
        }

        // ── o seu posto ──
        Spacer(Modifier.height(32.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.paper2)
                .border(1.dp, colors.rule, RoundedCornerShape(12.dp))
                .padding(18.dp),
        ) {
            Text(
                text = tr("o seu posto").uppercase(),
                color = colors.ink3,
                fontFamily = MonoFamily,
                fontSize = 9.sp,
                letterSpacing = 1.44.sp, // 0.16em of 9sp
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = tr(level.name),
                        color = accent,
                        fontFamily = SerifFamily,
                        fontSize = 26.sp,
                        lineHeight = 26.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = tr(level.subtitle),
                        color = colors.ink3,
                        fontFamily = SerifFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 13.sp,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(totalDone.toString(), color = colors.ink, fontFamily = MonoFamily, fontSize = 18.sp)
                    Text(
                        text = tr("dias feitos").uppercase(),
                        color = colors.ink3,
                        fontFamily = MonoFamily,
                        fontSize = 9.sp,
                        letterSpacing = 0.9.sp, // 0.1em of 9sp
                    )
                }
            }
            next?.let {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
                Spacer(Modifier.height(10.dp))
                Text(
                    text = trf("faltam {n} dias para", "n" to (it.min - totalDone)) + " " + tr(it.name),
                    color = colors.ink3,
                    fontFamily = MonoFamily,
                    fontSize = 10.sp,
                    letterSpacing = 0.4.sp,
                )
            }
        }
    }
}

@Composable
private fun TrendStat(label: String, value: String, hint: String, modifier: Modifier = Modifier) {
    val colors = LocalPautaColors.current
    Column(
        modifier
            .background(colors.paper)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.uppercase(),
            color = colors.ink3,
            fontFamily = MonoFamily,
            fontSize = 9.sp,
            letterSpacing = 0.9.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(value, color = colors.ink, fontFamily = SerifFamily, fontSize = 22.sp, lineHeight = 22.sp)
        if (hint.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(hint, color = colors.ink3, fontFamily = MonoFamily, fontSize = 9.sp)
        }
    }
}
