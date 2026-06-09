package com.pauta.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.i18n.tr
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.viewmodel.AppViewModel

/** Accent presets offered in Settings (null = the build default terracotta). */
private val ACCENT_PRESETS = listOf(
    null to "#B8533A",   // padrão (terracotta)
    "#2563EB" to "#2563EB",
    "#0E7C66" to "#0E7C66",
    "#7C5CBF" to "#7C5CBF",
    "#C2410C" to "#C2410C",
    "#9333EA" to "#9333EA",
)

/**
 * Settings as a full-surface overlay: appearance (theme / accent / contrast /
 * reduced motion), language, haptics and Pip. Reminders, export/import and the
 * updater layer on here next. // PT: definições — aspeto, idioma, hápticos, Pip.
 */
@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val colors = LocalPautaColors.current
    val vm: AppViewModel = viewModel()
    val prefs by vm.prefs.collectAsStateWithLifecycle()

    BackHandler { onClose() }

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
            Text(tr("Definições"), color = colors.ink, fontFamily = SerifFamily, fontSize = 26.sp)
        }

        Section(tr("Aspeto"))
        SegmentedRow(
            label = tr("Tema"),
            options = listOf("auto" to tr("Auto"), "light" to tr("Claro"), "dark" to tr("Escuro")),
            selected = prefs.theme,
            onSelect = { vm.setTheme(it) },
        )
        Spacer(Modifier.height(14.dp))
        Text(tr("Cor de destaque"), color = colors.ink2, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ACCENT_PRESETS.forEach { (value, hex) ->
                val selected = prefs.accent == value
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(parseHex(hex))
                        .clickableNoRipple { vm.setAccent(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White))
                }
            }
        }

        Section(tr("Idioma"))
        SegmentedRow(
            label = tr("Língua"),
            options = listOf("pt" to "Português", "en" to "English"),
            selected = prefs.lang,
            onSelect = { vm.setLang(it) },
        )

        Section(tr("Acessibilidade"))
        ToggleRow(tr("Alto contraste"), prefs.highContrast) { vm.setHighContrast(it) }
        ToggleRow(tr("Movimento reduzido"), prefs.reducedMotion) { vm.setReducedMotion(it) }

        Section(tr("Companhia"))
        ToggleRow(tr("Hápticos"), prefs.haptics) { vm.setHaptics(it) }
        ToggleRow(tr("Papagaio ajudante (Pip)"), prefs.parrot) { vm.setParrot(it) }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun Section(title: String) {
    val colors = LocalPautaColors.current
    Spacer(Modifier.height(26.dp))
    Text(title.uppercase(), color = colors.ink3, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalPautaColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onDark,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.paper,
                uncheckedTrackColor = colors.rule,
            ),
        )
    }
}

@Composable
private fun SegmentedRow(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val colors = LocalPautaColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = colors.ink2, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, text) ->
                val isSel = value == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) colors.accent.copy(alpha = 0.16f) else colors.paper2)
                        .clickableNoRipple { onSelect(value) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text,
                        color = if (isSel) colors.accent else colors.ink3,
                        fontSize = 13.sp,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

private fun parseHex(hex: String): Color =
    try { Color(android.graphics.Color.parseColor(hex)) } catch (e: IllegalArgumentException) { Color(0xFFB8533A) }
