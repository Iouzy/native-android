package com.pauta.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.FocusMath
import com.pauta.app.domain.HabitCalculator
import com.pauta.app.domain.HabitModel
import com.pauta.app.domain.InsightsMath
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.viewmodel.AppViewModel
import java.time.LocalDate

@Composable
fun YearReviewScreen(onClose: () -> Unit) {
    val colors = LocalPautaColors.current
    val vm: AppViewModel = viewModel()
    val blocks by vm.blocks.collectAsStateWithLifecycle()
    val allSessions by vm.allSessions.collectAsStateWithLifecycle()
    val habits by vm.habits.collectAsStateWithLifecycle()
    val habitLogs by vm.habitLogs.collectAsStateWithLifecycle()
    val habitRespiros by vm.habitRespiros.collectAsStateWithLifecycle()
    val allIntentions by vm.allIntentions.collectAsStateWithLifecycle()
    val allDays by vm.allDays.collectAsStateWithLifecycle()

    val currentYear = remember { LocalDate.now().year }
    var year by remember { mutableStateOf(currentYear) }
    val now = remember { System.currentTimeMillis() }

    // A8: no BackHandler here — this is a NavHost destination, so the system back
    // gesture pops it predictively (the ← also calls onClose). // PT: sem
    // BackHandler — é um destino de navegação; o gesto recua a rota.
    val blockSegs = remember(blocks, allSessions) {
        val byBlock = allSessions.groupBy { it.blockId }
        blocks.map { b ->
            InsightsMath.BlockSegs(b.id, byBlock[b.id].orEmpty().map { FocusMath.FocusSeg(it.startedAt, it.endedAt) })
        }
    }
    val namedHabits = remember(habits, habitLogs, habitRespiros) {
        val logSet = habitLogs.groupBy { it.habitId }.mapValues { e -> e.value.map { it.dayKey }.toSet() }
        val respSet = habitRespiros.groupBy { it.habitId }.mapValues { e -> e.value.map { it.dayKey }.toSet() }
        habits.map { h ->
            InsightsMath.NamedHabit(
                h.name,
                HabitModel(h.id, h.createdAt, h.cadence, h.anchor, h.weekdays, h.recurrence, h.endsAt,
                    logSet[h.id].orEmpty(), respSet[h.id].orEmpty()),
            )
        }
    }
    val reflectionByDay = remember(allDays) { allDays.associate { it.dayKey to it.reflection } }

    val review = remember(blockSegs, namedHabits, allIntentions, reflectionByDay, year, now) {
        InsightsMath.yearReview(blockSegs, namedHabits, allIntentions, reflectionByDay, year, now)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = colors.accent, fontSize = 22.sp, modifier = Modifier.clickableNoRipple(onClose))
            Spacer(Modifier.width(14.dp))
            Text(tr("Retrospetiva do ano"), color = colors.ink, fontFamily = SerifFamily, fontSize = 22.sp)
        }

        // Year navigation
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "←",
                color = colors.accent,
                fontSize = 20.sp,
                modifier = Modifier.clickableNoRipple { year-- },
            )
            Spacer(Modifier.width(24.dp))
            Text(
                year.toString(),
                color = colors.ink,
                fontFamily = SerifFamily,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(24.dp))
            Text(
                "→",
                color = if (year < currentYear) colors.accent else colors.ink4,
                fontSize = 20.sp,
                modifier = Modifier.clickableNoRipple { if (year < currentYear) year++ },
            )
        }

        Spacer(Modifier.height(24.dp))

        // Stats grid — 2×2
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                value = fmtFocusHours(review.focusMs),
                label = tr("horas de foco"),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = review.blockCount.toString(),
                label = tr("blocos"),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                value = "${review.intDone}/${review.intTotal}",
                label = tr("intenções"),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = review.tideDoneDays.toString(),
                label = tr("dias com marés"),
                modifier = Modifier.weight(1f),
            )
        }

        // Highlights
        if (review.topHabitName != null || review.reflections > 0) {
            Spacer(Modifier.height(24.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.paper2)
                    .border(1.dp, colors.rule, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (review.topHabitName != null && review.topHabitStreak > 0) {
                    HighlightRow(
                        label = tr("Melhor streak"),
                        value = trf("{n} dias — {name}", "n" to review.topHabitStreak, "name" to review.topHabitName),
                    )
                }
                if (review.reflections > 0) {
                    HighlightRow(
                        label = tr("Reflexões"),
                        value = trf("{n} reflexões escritas", "n" to review.reflections),
                    )
                }
                val tier = HabitCalculator.tideTier(review.topHabitStreak)
                if (tier != null) {
                    HighlightRow(
                        label = tr("Maré mais alta"),
                        value = tier.name,
                    )
                }
            }
        }

        // Empty state
        if (review.blockCount == 0 && review.intTotal == 0 && review.tideDoneDays == 0) {
            Spacer(Modifier.height(32.dp))
            Text(
                tr("Sem dados para este ano."),
                color = colors.ink3,
                fontFamily = SerifFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = LocalPautaColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.paper2)
            .border(1.dp, colors.rule, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            color = colors.accent,
            fontFamily = SerifFamily,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = colors.ink3,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HighlightRow(label: String, value: String) {
    val colors = LocalPautaColors.current
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = colors.ink3, fontSize = 13.sp, modifier = Modifier.width(100.dp))
        Spacer(Modifier.width(8.dp))
        Text(value, color = colors.ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

private fun fmtFocusHours(ms: Long): String {
    val h = ms / 3_600_000
    val m = (ms % 3_600_000) / 60_000
    return if (h > 0) "${h}h${m.toString().padStart(2, '0')}" else "${m}m"
}
