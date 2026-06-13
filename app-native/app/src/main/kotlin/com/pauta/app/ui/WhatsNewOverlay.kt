package com.pauta.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pauta.app.BuildConfig
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.service.WhatsNewState
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.SerifFamily

/**
 * The post-update "what's new" screen: shown once on the first launch after the
 * in-app updater installs a newer build, so the user sees what changed. Mirrors
 * the onboarding overlay's calm paper/ink layout — mono eyebrow, serif headline,
 * the version line, then the release notes (the GitHub release body, shown
 * plainly like the Settings update card) and a single dismiss. With no stashed
 * notes it falls back to a quiet generic line. // PT: ecrã de novidades
 * pós-atualização, mostrado uma vez; mostra as notas da versão ou uma linha
 * genérica.
 */
@Composable
fun WhatsNewOverlay(state: WhatsNewState, onDone: () -> Unit) {
    val colors = LocalPautaColors.current
    val scroll = rememberScrollState()
    val notes = remember(state) { state.notes.trim() }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            // Swallow taps so the app underneath never receives them.
            .clickableNoRipple { },
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 34.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(40.dp))
            Text(
                text = tr("Novidades").uppercase(),
                color = colors.accent,
                fontFamily = MonoFamily,
                fontSize = 10.sp,
                letterSpacing = 2.2.sp, // 0.22em of 10sp
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = tr("Pauta foi atualizada."),
                color = colors.ink,
                fontFamily = SerifFamily,
                fontSize = 34.sp,
                lineHeight = 38.sp,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = versionLabel(),
                color = colors.ink4,
                fontFamily = MonoFamily,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(22.dp))
            Text(
                text = if (notes.isNotBlank()) notes else tr("Pequenas melhorias e correções."),
                color = colors.ink2,
                fontFamily = SerifFamily,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                modifier = Modifier.widthIn(max = 380.dp),
            )
            Spacer(Modifier.height(40.dp))
        }

        Column(Modifier.fillMaxWidth().padding(start = 34.dp, end = 34.dp, bottom = 40.dp)) {
            PautaButton(label = tr("Continuar"), modifier = Modifier.fillMaxWidth(), onClick = onDone)
        }
    }
}

/** "Version YYYY-MM-DD" (from the build timestamp) or a build-run fallback — the
 *  same label Settings shows. // PT: a mesma etiqueta de versão das Definições. */
private fun versionLabel(): String =
    if (BuildConfig.BUILD_TS > 0L) {
        val d = java.time.Instant.ofEpochSecond(BuildConfig.BUILD_TS)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        trf("Versão de {date}", "date" to d.toString())
    } else {
        "build #${BuildConfig.BUILD_RUN}"
    }
