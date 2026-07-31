package com.pauta.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.PautaType
import com.pauta.app.ui.viewmodel.AppViewModel

/**
 * P10: the one empty state. The app had two treatments for "there is nothing
 * here yet" — the tabs' serif phrase with a small Pip pose beside it, and the
 * book faces' quiet mono one-liner — drifting in size, colour and family. Both
 * become this: a serif-italic line in `ink4`, optionally led by a slightly larger
 * phrase in `ink2`, optionally with Pip.
 *
 * [pip] is opt-in per call site and still hidden when the parrot preference is
 * off, so an empty state never argues with a user who dismissed the bird.
 * // PT: o estado vazio único — uma linha em serifa itálica (`ink4`), com frase
 * de abertura e pose do Pip opcionais; o Pip continua sujeito à preferência.
 */
@Composable
fun EmptyState(
    line: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    pip: Boolean = false,
    pipHeight: Dp = 40.dp,
) {
    val colors = LocalPautaColors.current
    // Only the call sites that ask for Pip pay for the preference read.
    // // PT: só quem pede o Pip lê a preferência.
    val showPip = if (pip) rememberParrotEnabled() else false
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (showPip) {
            PipPose(height = pipHeight)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            if (title != null) {
                Text(
                    text = title,
                    color = colors.ink2,
                    style = PautaType.CardTitle,
                    fontStyle = FontStyle.Italic,
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = line,
                color = colors.ink4,
                style = PautaType.Body,
                fontStyle = FontStyle.Italic,
            )
        }
    }
}

/** The parrot preference, read from the app-scoped ViewModel. // PT: a
 *  preferência do papagaio. */
@Composable
private fun rememberParrotEnabled(): Boolean {
    val vm: AppViewModel = viewModel()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    return prefs.parrot
}
