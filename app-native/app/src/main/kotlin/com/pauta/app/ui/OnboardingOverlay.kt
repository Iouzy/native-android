package com.pauta.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pauta.app.i18n.tr
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.SerifFamily

/**
 * The web's onboarding carousel (extras.jsx OnboardingOverlay): five calm,
 * swipeable cards — bem-vindo, hoje, pauta, marés, pronto — with the page
 * dots, "saltar" and "Seguir". Shown once (prefs.onboardingSeen). The web's
 * "explorar com um exemplo" option needs the demo seed, which the native build
 * doesn't carry — the final card offers the blank start. // PT: o carrossel de
 * boas-vindas da web, mostrado uma vez.
 */
@Composable
fun OnboardingOverlay(onDone: () -> Unit) {
    val colors = LocalPautaColors.current
    var idx by remember { mutableIntStateOf(0) }

    data class Card(val tag: String, val icon: String, val title: @Composable () -> Unit, val body: String)

    val accentEm: (String, String, String) -> (@Composable () -> Unit) = { pre, em, post ->
        {
            Text(
                text = buildAnnotatedString {
                    append(pre)
                    withStyle(SpanStyle(color = colors.accent, fontStyle = FontStyle.Italic)) { append(em) }
                    append(post)
                },
                color = colors.ink,
                fontFamily = SerifFamily,
                fontSize = 38.sp,
                lineHeight = 40.sp,
                letterSpacing = (-0.57).sp, // -0.015em of 38sp
            )
        }
    }
    val plainTitle: (String) -> (@Composable () -> Unit) = { t ->
        {
            Text(
                text = t,
                color = colors.ink,
                fontFamily = SerifFamily,
                fontSize = 38.sp,
                lineHeight = 40.sp,
                letterSpacing = (-0.57).sp,
            )
        }
    }

    val cards = listOf(
        Card(
            tr("bem-vindo"), "spark",
            accentEm(tr("Esta é a sua") + " ", tr("pauta"), "."),
            tr("Um lugar calmo, privado e offline para o que importa. Sem conta, sem servidor — tudo fica no seu telemóvel."),
        ),
        Card(
            tr("hoje"), "hoje",
            plainTitle(tr("Comece pelo que importa.")),
            tr("Na tab Hoje, liste 1 a 4 intenções — as coisas que movem o seu dia — e reflita à noite."),
        ),
        Card(
            tr("pauta"), "pauta",
            plainTitle(tr("Trabalhe em blocos.")),
            tr("Na Pauta, inicie um bloco de foco e o tempo conta-se sozinho. Pause, retome e conclua quando quiser."),
        ),
        Card(
            tr("marés"), "mares",
            plainTitle(tr("Cultive hábitos como marés.")),
            tr("Nas Marés, marque hábitos dia a dia. A constância faz a maré subir — e os dias de descanso são honestos."),
        ),
        Card(
            tr("pronto"), "check",
            accentEm(tr("Tudo") + " ", tr("seu"), "."),
            tr("Comece com uma pauta em branco. Muda tudo depois nas Definições."),
        ),
    )
    val last = idx == cards.size - 1
    val card = cards[idx]

    var dragX by remember { mutableFloatStateOf(0f) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            // Swallow taps so the app underneath never receives them.
            .clickableNoRipple { },
    ) {
        // top bar: saltar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (!last) {
                Text(
                    text = tr("saltar").uppercase(),
                    color = colors.ink3,
                    fontFamily = MonoFamily,
                    fontSize = 11.sp,
                    letterSpacing = 1.32.sp, // 0.12em of 11sp
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickableNoRipple(onDone)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            } else {
                Spacer(Modifier.height(33.dp))
            }
        }

        // card body, swipeable
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 34.dp)
                .pointerInput(idx) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragX = 0f },
                        onHorizontalDrag = { _, delta -> dragX += delta },
                        onDragEnd = {
                            if (dragX < -45f && !last) idx += 1
                            else if (dragX > 45f && idx > 0) idx -= 1
                        },
                    )
                },
            verticalArrangement = Arrangement.Center,
        ) {
            OnboardingIcon(card.icon)
            Spacer(Modifier.height(26.dp))
            Text(
                text = card.tag.uppercase(),
                color = colors.accent,
                fontFamily = MonoFamily,
                fontSize = 10.sp,
                letterSpacing = 2.2.sp, // 0.22em of 10sp
            )
            Spacer(Modifier.height(14.dp))
            card.title()
            Spacer(Modifier.height(18.dp))
            Text(
                text = card.body,
                color = colors.ink2,
                fontFamily = SerifFamily,
                fontSize = 17.sp,
                lineHeight = 25.sp,
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }

        // dots + buttons
        Column(Modifier.fillMaxWidth().padding(start = 34.dp, end = 34.dp, bottom = 40.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                cards.forEachIndexed { i, _ ->
                    val w by animateDpAsState(if (i == idx) 22.dp else 7.dp, tween(250), label = "dot")
                    Box(
                        Modifier
                            .width(w)
                            .height(7.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (i == idx) colors.accent else colors.rule)
                            .clickableNoRipple { idx = i },
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            PautaButton(
                label = if (last) tr("Começar em branco") else tr("Seguir"),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (last) onDone() else idx += 1
            }
        }
    }
}

/** The 64dp rounded icon tile; "spark" renders the serif ✦ like the web. */
@Composable
private fun OnboardingIcon(kind: String) {
    val colors = LocalPautaColors.current
    Box(
        Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.accent.copy(alpha = 0.08f))
            .border(1.dp, colors.accent.copy(alpha = 0.2f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when (kind) {
            "hoje" -> Icon(PautaIcons.Hoje, contentDescription = null, tint = colors.accent, modifier = Modifier.size(30.dp))
            "pauta" -> Icon(PautaIcons.Pauta, contentDescription = null, tint = colors.accent, modifier = Modifier.size(30.dp))
            "mares" -> Icon(PautaIcons.Mares, contentDescription = null, tint = colors.accent, modifier = Modifier.size(30.dp))
            "check" -> Icon(PautaIcons.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(26.dp))
            else -> Text(
                text = "✦",
                color = colors.accent,
                fontFamily = SerifFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 36.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}
