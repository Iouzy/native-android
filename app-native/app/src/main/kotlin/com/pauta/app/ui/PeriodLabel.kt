package com.pauta.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily

/**
 * The shared top-left period eyebrow used across Hoje, Pauta and Marés so the
 * date/period always reads the same: a dim weekday/day [prefix], the accent
 * [month] token with a small accent dot beneath it (the web MonthStrip's active
 * marker), and an optional dim year [suffix] ("'26").
 *
 * The [prefix]/[suffix] carry a bottom padding equal to the dot's drop so their
 * baselines stay level with the month while the dot hangs underneath.
 * // PT: rótulo do período — mês em destaque, com o ponto da maré activa.
 */
@Composable
fun PeriodLabel(
    month: String,
    modifier: Modifier = Modifier,
    prefix: String = "",
    suffix: String = "",
) {
    val colors = LocalPautaColors.current
    val tracking = 1.8.sp // 0.18em of 10sp
    val dotDrop = 7.dp // dot (4) + gap (3) — keeps prefix/suffix baselines on the month

    Row(modifier, verticalAlignment = Alignment.Bottom) {
        if (prefix.isNotEmpty()) {
            Text(
                text = prefix.uppercase(),
                color = colors.ink3,
                fontFamily = MonoFamily,
                fontSize = 10.sp,
                letterSpacing = tracking,
                modifier = Modifier.padding(bottom = dotDrop),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = month.uppercase(),
                color = colors.accent,
                fontFamily = MonoFamily,
                fontSize = 10.sp,
                letterSpacing = tracking,
            )
            Spacer(Modifier.height(3.dp))
            Box(Modifier.size(4.dp).clip(CircleShape).background(colors.accent))
        }
        if (suffix.isNotEmpty()) {
            Text(
                text = suffix.uppercase(),
                color = colors.ink4,
                fontFamily = MonoFamily,
                fontSize = 9.sp,
                letterSpacing = 1.0.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = dotDrop),
            )
        }
    }
}
