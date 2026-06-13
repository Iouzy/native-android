package com.pauta.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.pauta.app.data.entity.GoalEntity
import com.pauta.app.domain.DateUtils
import com.pauta.app.i18n.tr
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.viewmodel.AppViewModel

/**
 * Quarterly goals overlay: a quarter at a time, each goal toggleable with its
 * milestones. Reached from Settings. // PT: objetivos trimestrais — um trimestre
 * de cada vez, com marcos.
 */
@Composable
fun GoalsScreen(onClose: () -> Unit) {
    val colors = LocalPautaColors.current
    val vm: AppViewModel = viewModel()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val milestones by vm.milestones.collectAsStateWithLifecycle()
    var quarter by remember { mutableStateOf(DateUtils.currentQuarter()) }

    // A8: no BackHandler here — this is a NavHost destination, so the system back
    // gesture pops it predictively (the ← also calls onClose). // PT: sem
    // BackHandler — é um destino de navegação; o gesto recua a rota.
    val quarterGoals = goals.filter { it.quarter == quarter }.sortedBy { it.position }

    Column(
        Modifier.fillMaxSize().background(colors.paper).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = colors.accent, fontSize = 22.sp, modifier = Modifier.clickableNoRipple(onClose))
            Spacer(Modifier.width(14.dp))
            Text(tr("Objetivos"), color = colors.ink, fontFamily = SerifFamily, fontSize = 26.sp)
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.ChevronLeft, contentDescription = null, tint = colors.ink3,
                modifier = Modifier.size(26.dp).clickableNoRipple { quarter = DateUtils.prevQuarter(quarter) },
            )
            Text(
                quarterLabel(quarter), color = colors.ink2, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                modifier = Modifier.weight(1f).clickableNoRipple { quarter = DateUtils.currentQuarter() },
            )
            Icon(
                Icons.Filled.ChevronRight, contentDescription = null, tint = colors.ink3,
                modifier = Modifier.size(26.dp).clickableNoRipple { quarter = DateUtils.nextQuarter(quarter) },
            )
        }

        Spacer(Modifier.height(12.dp))
        // A6: focus the new-goal field only on an empty quarter — invites typing
        // without ever covering an existing list. // PT: só foca o campo no
        // trimestre vazio, sem tapar a lista.
        AddField(tr("Novo objetivo…"), autoFocus = quarterGoals.isEmpty()) { vm.addGoal(it, quarter) }

        if (quarterGoals.isEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(tr("Sem objetivos neste trimestre."), color = colors.ink4, fontSize = 14.sp)
        } else {
            quarterGoals.forEach { goal ->
                GoalBlock(
                    goal = goal,
                    milestones = milestones.filter { it.goalId == goal.id }.sortedBy { it.position },
                    onToggle = { vm.toggleGoal(goal.id) },
                    onDelete = { vm.removeGoal(goal.id) },
                    onAddMilestone = { vm.addMilestone(goal.id, it) },
                    onToggleMilestone = { vm.toggleMilestone(it) },
                    onDeleteMilestone = { vm.removeMilestone(it) },
                )
            }
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun GoalBlock(
    goal: GoalEntity,
    milestones: List<com.pauta.app.data.entity.MilestoneEntity>,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onAddMilestone: (String) -> Unit,
    onToggleMilestone: (String) -> Unit,
    onDeleteMilestone: (String) -> Unit,
) {
    val colors = LocalPautaColors.current
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        CheckRow(goal.text, goal.done, onToggle, onDelete, bold = true)
        milestones.forEach { m ->
            Row(Modifier.padding(start = 22.dp)) {
                CheckRow(m.text, m.done, { onToggleMilestone(m.id) }, { onDeleteMilestone(m.id) }, small = true)
            }
        }
        Row(Modifier.padding(start = 22.dp, top = 2.dp)) {
            AddField(tr("Novo marco…"), small = true) { onAddMilestone(it) }
        }
    }
}

@Composable
private fun CheckRow(text: String, done: Boolean, onToggle: () -> Unit, onDelete: () -> Unit, bold: Boolean = false, small: Boolean = false) {
    val colors = LocalPautaColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (done) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (done) colors.accent else colors.ink4,
            modifier = Modifier.size(if (small) 18.dp else 22.dp).clickableNoRipple(onToggle),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            color = if (done) colors.ink4 else colors.ink,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            fontSize = if (small) 14.sp else 16.sp,
            fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.Close, contentDescription = tr("Apagar"), tint = colors.ink4,
            modifier = Modifier.size(if (small) 16.dp else 18.dp).clickableNoRipple(onDelete),
        )
    }
}

@Composable
private fun AddField(placeholder: String, small: Boolean = false, autoFocus: Boolean = false, onAdd: (String) -> Unit) {
    val colors = LocalPautaColors.current
    var text by remember { mutableStateOf("") }
    // Done commits and clears but keeps focus, so goals/milestones can be typed in
    // a row from the keyboard alone. // PT: Enter regista e limpa, mantendo o foco.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (autoFocus) { delay(120); runCatching { focusRequester.requestFocus() } }
    }
    fun commit() { if (text.isNotBlank()) { onAdd(text); text = "" } }
    TextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(placeholder, color = colors.ink4, fontSize = if (small) 14.sp else 16.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        textStyle = LocalTextStyle.current.copy(color = colors.ink, fontSize = if (small) 14.sp else 16.sp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            cursorColor = colors.accent,
            focusedIndicatorColor = colors.accent,
            unfocusedIndicatorColor = colors.rule,
            focusedTextColor = colors.ink,
            unfocusedTextColor = colors.ink,
        ),
    )
}

private fun quarterLabel(quarter: String): String {
    val (year, q) = quarter.split("-Q")
    return "Q$q $year"
}
