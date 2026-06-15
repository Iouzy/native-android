package com.pauta.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.data.dao.SearchHit
import com.pauta.app.data.entity.FocusBlockEntity
import com.pauta.app.data.entity.FocusSessionEntity
import com.pauta.app.data.entity.IntentionEntity
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.FocusMath
import com.pauta.app.domain.HistoryDay
import com.pauta.app.i18n.I18n
import com.pauta.app.i18n.Lang
import com.pauta.app.i18n.tr
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.SansFamily
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.viewmodel.AppViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The memory surface: past days with their done/total intentions and reflection,
 * plus E1 full-text search across intentions, reflections and focus blocks. A
 * blank query shows the day list; typing shows hits grouped by day. Tapping any
 * day (a list row or a result) opens its read-only detail. A full-surface
 * navigation destination, so it owns the status-bar inset itself.
 * // PT: superfície de memória — histórico + pesquisa; tocar num dia abre o detalhe.
 */
@Composable
fun HistoryView(onClose: () -> Unit) {
    val colors = LocalPautaColors.current
    val vm: AppViewModel = viewModel()
    val history by vm.history.collectAsStateWithLifecycle()
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val allIntentions by vm.allIntentions.collectAsStateWithLifecycle()
    val allDays by vm.allDays.collectAsStateWithLifecycle()
    val blocks by vm.blocks.collectAsStateWithLifecycle()
    val allSessions by vm.allSessions.collectAsStateWithLifecycle()

    // The day whose read-only detail is open (null = the list/search view).
    var selectedDay by remember { mutableStateOf<String?>(null) }

    // Leave the query blank for next time the screen opens. // PT: limpa ao sair.
    DisposableEffect(Unit) { onDispose { vm.setSearchQuery("") } }

    // A nested sub-view, like Settings' PIN flow: while a day is open the back
    // gesture peels it back to the list first; with none open it's disabled, so
    // back pops the whole History destination (predictive). // PT: o recuo volta
    // primeiro do dia para a lista.
    BackHandler(enabled = selectedDay != null) { selectedDay = null }

    val day = selectedDay
    if (day != null) {
        DayMemory(
            dayKey = day,
            intentions = allIntentions.filter { it.dayKey == day }.sortedBy { it.position },
            reflection = allDays.firstOrNull { it.dayKey == day }?.reflection.orEmpty(),
            blocks = blocks.filter { DateUtils.dayKeyOf(it.createdAt) == day },
            sessions = allSessions,
            onBack = { selectedDay = null },
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding(),
    ) {
        // Header + search are pinned; only the list/results below them scroll.
        Column(Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "←",
                    color = colors.accent,
                    fontSize = 22.sp,
                    modifier = Modifier.clickableNoRipple(onClose),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = tr("Histórico"),
                    color = colors.ink,
                    fontFamily = SerifFamily,
                    fontSize = 26.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
            SearchField(value = query, onChange = vm::setSearchQuery, onClear = { vm.setSearchQuery("") })
            Spacer(Modifier.height(8.dp))
        }

        if (query.isBlank()) {
            if (history.isEmpty()) {
                Text(
                    tr("Ainda sem dias anteriores."),
                    color = colors.ink4,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 24.dp)) {
                    items(history, key = { it.dayKey }) { d ->
                        Column {
                            HistoryRow(d, onClick = { selectedDay = d.dayKey })
                            Divider()
                        }
                    }
                    item { Spacer(Modifier.height(48.dp)) }
                }
            }
        } else {
            if (results.isEmpty()) {
                Text(
                    tr("Sem resultados."),
                    color = colors.ink4,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            } else {
                // Hits arrive newest-day-first; group preserves that order.
                val grouped = remember(results) { results.groupBy { it.dayKey } }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 24.dp)) {
                    grouped.forEach { (d, hits) ->
                        item(key = "head-$d") { DayResultHeader(d) }
                        items(hits, key = { it.docId }) { hit ->
                            ResultRow(hit, onClick = { selectedDay = d })
                        }
                    }
                    item { Spacer(Modifier.height(48.dp)) }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(day: HistoryDay, onClick: () -> Unit) {
    val colors = LocalPautaColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick)
            .padding(vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatDay(day.dayKey).replaceFirstChar { it.uppercase() },
                color = colors.ink2,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            if (day.totalCount > 0) {
                Text(
                    text = "${day.doneCount}/${day.totalCount}",
                    color = colors.ink3,
                    fontSize = 13.sp,
                )
            }
        }
        if (day.reflection.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = day.reflection,
                color = colors.ink3,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The search box: a quiet bordered field with a leading glyph and a clear "×".
 *  Live — every keystroke re-runs the lookup, so there's no submit button; the
 *  IME "search" action just dismisses the keyboard. // PT: campo de pesquisa ao
 *  vivo; o "procurar" do teclado só fecha o teclado. */
@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onClear: () -> Unit) {
    val colors = LocalPautaColors.current
    val focus = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = colors.ink, fontFamily = SansFamily, fontSize = 15.sp),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, colors.rule, RoundedCornerShape(10.dp))
                    .background(colors.paper2)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = colors.ink4, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(tr("Procurar…"), color = colors.ink4, fontFamily = SansFamily, fontSize = 15.sp)
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = tr("Limpar"),
                        tint = colors.ink4,
                        modifier = Modifier.size(16.dp).clickableNoRipple(onClear),
                    )
                }
            }
        },
    )
}

/** A day header above its search hits — a quiet mono date eyebrow. */
@Composable
private fun DayResultHeader(dayKey: String) {
    val colors = LocalPautaColors.current
    Column {
        Spacer(Modifier.height(18.dp))
        Text(
            text = formatDay(dayKey).replaceFirstChar { it.uppercase() },
            color = colors.ink3,
            fontFamily = MonoFamily,
            fontSize = 10.sp,
            letterSpacing = 1.0.sp,
        )
        Spacer(Modifier.height(2.dp))
    }
}

/** One search hit: a tiny mono source tag (intention / reflection / block) and
 *  the matched text. Tapping opens the day. // PT: um resultado da pesquisa. */
@Composable
private fun ResultRow(hit: SearchHit, onClick: () -> Unit) {
    val colors = LocalPautaColors.current
    val label = when (hit.source) {
        "intention" -> tr("intenção")
        "reflection" -> tr("reflexão")
        else -> tr("bloco")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label.uppercase(),
            color = colors.ink4,
            fontFamily = MonoFamily,
            fontSize = 8.sp,
            letterSpacing = 1.0.sp,
            modifier = Modifier.width(62.dp).padding(top = 3.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = hit.text.trim(),
            color = colors.ink2,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Divider() {
    val colors = LocalPautaColors.current
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.rule),
    )
}

/** The read-only detail of one past day: its intentions (done struck through),
 *  the focus blocks logged that day, and the night's reflection. Reached by
 *  tapping a day in the list or a search hit; back returns to the list. // PT:
 *  detalhe só-leitura de um dia — intenções, blocos e reflexão. */
@Composable
private fun DayMemory(
    dayKey: String,
    intentions: List<IntentionEntity>,
    reflection: String,
    blocks: List<FocusBlockEntity>,
    sessions: List<FocusSessionEntity>,
    onBack: () -> Unit,
) {
    val colors = LocalPautaColors.current
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "←",
                color = colors.accent,
                fontSize = 22.sp,
                modifier = Modifier.clickableNoRipple(onBack),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = formatDay(dayKey).replaceFirstChar { it.uppercase() },
                color = colors.ink,
                fontFamily = SerifFamily,
                fontSize = 24.sp,
            )
        }
        Spacer(Modifier.height(20.dp))

        if (intentions.isEmpty() && reflection.isBlank() && blocks.isEmpty()) {
            Text(tr("Sem conteúdo neste dia."), color = colors.ink4, fontSize = 14.sp)
        } else {
            if (intentions.isNotEmpty()) {
                SectionLabel(tr("intenções"))
                Spacer(Modifier.height(8.dp))
                intentions.forEach { MemoryIntentionRow(it) }
            }
            if (blocks.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                SectionLabel(tr("blocos"))
                Spacer(Modifier.height(8.dp))
                blocks.sortedByDescending { it.createdAt }.forEach { b ->
                    val ms = FocusMath.blockElapsedMs(
                        sessions.filter { it.blockId == b.id }
                            .map { FocusMath.FocusSeg(it.startedAt, it.endedAt) },
                        System.currentTimeMillis(),
                    )
                    MemoryBlockRow(b, ms)
                }
            }
            if (reflection.isNotBlank()) {
                Spacer(Modifier.height(28.dp))
                ReflectionCard(reflection)
            }
        }
        Spacer(Modifier.height(48.dp))
    }
}

/** An uppercase mono section eyebrow, the app's recurring quiet header. */
@Composable
private fun SectionLabel(text: String) {
    val colors = LocalPautaColors.current
    Text(
        text = text.uppercase(),
        color = colors.ink3,
        fontFamily = MonoFamily,
        fontSize = 9.sp,
        letterSpacing = 1.8.sp,
    )
}

/** One past intention, read-only: a dim done/pending mark and the text, struck
 *  through once done. // PT: uma intenção passada, só-leitura. */
@Composable
private fun MemoryIntentionRow(item: IntentionEntity) {
    val colors = LocalPautaColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Text(
            text = if (item.done) "✓" else "·",
            color = if (item.done) colors.accent else colors.ink4,
            fontFamily = MonoFamily,
            fontSize = 14.sp,
            modifier = Modifier.width(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = item.text,
            color = if (item.done) colors.ink3 else colors.ink2,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
    }
}

/** One focus block from that day: title, its total focus time, and any note. */
@Composable
private fun MemoryBlockRow(block: FocusBlockEntity, elapsedMs: Long) {
    val colors = LocalPautaColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = block.title, color = colors.ink2, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(
                text = FocusMath.fmtDuration(elapsedMs),
                color = colors.ink3,
                fontFamily = MonoFamily,
                fontSize = 12.sp,
            )
        }
        if (block.reflection.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(text = block.reflection, color = colors.ink3, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

/** The night's reflection, in the app's paper-2 card with a serif-italic body. */
@Composable
private fun ReflectionCard(reflection: String) {
    val colors = LocalPautaColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, colors.rule, RoundedCornerShape(14.dp))
            .background(colors.paper2)
            .padding(horizontal = 22.dp, vertical = 22.dp),
    ) {
        SectionLabel(tr("Reflexão"))
        Spacer(Modifier.height(10.dp))
        Text(
            text = reflection,
            color = colors.ink2,
            fontFamily = SerifFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 17.sp,
            lineHeight = 24.sp,
        )
    }
}

private fun formatDay(dayKey: String): String {
    val locale = if (I18n.lang == Lang.EN) Locale.ENGLISH else Locale("pt", "PT")
    return LocalDate.parse(dayKey).format(DateTimeFormatter.ofPattern("EEEE, d MMM", locale))
}
