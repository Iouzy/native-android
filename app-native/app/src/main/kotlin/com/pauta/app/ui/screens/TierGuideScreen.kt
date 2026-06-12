package com.pauta.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pauta.app.domain.HabitCalculator
import com.pauta.app.i18n.tr
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.SerifFamily

@Composable
fun TierGuideScreen(onClose: () -> Unit) {
    val colors = LocalPautaColors.current

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
            Text(tr("Como funcionam as marés"), color = colors.ink, fontFamily = SerifFamily, fontSize = 22.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            tr("Cada hábito tem uma maré — uma forma de ver até onde chegaste."),
            color = colors.ink2,
            fontFamily = SerifFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

        // Streak tiers
        Spacer(Modifier.height(24.dp))
        Text(tr("Streaks").uppercase(), color = colors.ink3, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.paper2)
                .border(1.dp, colors.rule, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
        ) {
            HabitCalculator.TIDE_TIERS.forEachIndexed { i, tier ->
                if (i > 0) HorizontalDivider(color = colors.rule.copy(alpha = 0.6f))
                TierRow(
                    name = tier.name,
                    subtitle = tier.subtitle,
                    threshold = trf("${tier.min}+ dias"),
                    accent = colors.accent,
                )
            }
        }

        // Navigator tiers
        Spacer(Modifier.height(26.dp))
        Text(tr("Nível do navegador").uppercase(), color = colors.ink3, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            tr("Soma de todos os dias marcados em todos os hábitos."),
            color = colors.ink3,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.paper2)
                .border(1.dp, colors.rule, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
        ) {
            HabitCalculator.NAVIGATOR_LEVELS.forEachIndexed { i, tier ->
                if (i > 0) HorizontalDivider(color = colors.rule.copy(alpha = 0.6f))
                TierRow(
                    name = tier.name,
                    subtitle = tier.subtitle,
                    threshold = trf("${tier.min}+ dias"),
                    accent = colors.accent,
                )
            }
        }

        // Respiro note
        Spacer(Modifier.height(26.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.paper2)
                .border(1.dp, colors.rule, RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            Text(
                tr("Respiros"),
                color = colors.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                tr("Um dia de respiro conta para a streak mas não entra no cálculo de conclusão. Usa-o quando precisas de uma pausa honesta — a tua maré não quebra."),
                color = colors.ink2,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun TierRow(name: String, subtitle: String, threshold: String, accent: androidx.compose.ui.graphics.Color) {
    val colors = LocalPautaColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(8.dp).clip(CircleShape).background(accent.copy(alpha = 0.5f))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = colors.ink3, fontSize = 12.sp, lineHeight = 16.sp, fontStyle = FontStyle.Italic)
        }
        Text(threshold, color = colors.ink3, fontSize = 11.sp, fontFamily = com.pauta.app.ui.theme.MonoFamily)
    }
}

private fun trf(s: String) = s  // threshold labels are not localised (numeric)
