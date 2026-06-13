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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.i18n.tr
import com.pauta.app.ui.canUseBiometric
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.findFragmentActivity
import com.pauta.app.ui.promptBiometric
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.viewmodel.AppViewModel

enum class PinMode { LOCK, SET, DISABLE }

/**
 * Full-screen PIN entry used for three flows:
 * LOCK — verify existing PIN to unlock the app on launch.
 * SET  — enter new PIN twice to enable the lock.
 * DISABLE — enter existing PIN to disable the lock.
 * // PT: ecrã de PIN partilhado pelos três fluxos: desbloquear, definir, desativar.
 */
@Composable
fun PinScreen(
    mode: PinMode,
    onSuccess: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    val colors = LocalPautaColors.current
    val vm: AppViewModel = viewModel()

    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var confirmStage by remember { mutableStateOf(false) }  // only used in SET mode
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // C3: biometric unlock — LOCK mode only, when the user enabled it and the
    // device can do it. SET/DISABLE always use the keypad. The keypad below stays
    // fully usable, so it's the fallback whenever biometrics are skipped/failed.
    // // PT: biometria só no modo LOCK (se ativada e disponível); o teclado fica
    // sempre como alternativa.
    val context = LocalContext.current
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val activity = remember(context) { context.findFragmentActivity() }
    val canBiometric = remember { context.canUseBiometric() }
    val biometricOffered =
        mode == PinMode.LOCK && prefs.biometricEnabled && canBiometric && activity != null
    val bioTitle = tr("Desbloquear")
    val bioNegative = tr("Usar PIN")
    fun launchBiometric() {
        if (activity != null) promptBiometric(activity, bioTitle, bioNegative, onSuccess)
    }
    // Present the system sheet once when the lock screen first appears; the user
    // can re-summon it with the fingerprint affordance below. Saved across config
    // changes so a rotation doesn't double-prompt. // PT: mostra a folha uma vez
    // ao abrir; o utilizador pode reabri-la pelo ícone abaixo.
    var autoPrompted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(biometricOffered) {
        if (biometricOffered && !autoPrompted) { autoPrompted = true; launchBiometric() }
    }

    val title = when {
        mode == PinMode.LOCK -> tr("Introduz o teu PIN")
        mode == PinMode.DISABLE -> tr("Confirma o PIN para desativar")
        confirmStage -> tr("Confirma o novo PIN")
        else -> tr("Cria um novo PIN")
    }
    val subtitle = when {
        mode == PinMode.LOCK -> null
        mode == PinMode.DISABLE -> null
        confirmStage -> null
        else -> tr("Mínimo 4 dígitos")
    }

    val current = if (mode == PinMode.SET && confirmStage) confirm else pin

    fun onKey(digit: String) {
        errorMsg = null
        if (mode == PinMode.SET && confirmStage) {
            if (confirm.length < 8) confirm += digit
        } else {
            if (pin.length < 8) pin += digit
        }
    }

    fun onDelete() {
        errorMsg = null
        if (mode == PinMode.SET && confirmStage) {
            if (confirm.isNotEmpty()) confirm = confirm.dropLast(1)
        } else {
            if (pin.isNotEmpty()) pin = pin.dropLast(1)
        }
    }

    fun onSubmit() {
        errorMsg = null
        when (mode) {
            PinMode.LOCK -> {
                if (pin.length < 4) { errorMsg = tr("PIN demasiado curto"); return }
                if (vm.verifyPinCode(pin)) onSuccess()
                else { errorMsg = tr("PIN incorrecto"); pin = "" }
            }
            PinMode.DISABLE -> {
                if (pin.length < 4) { errorMsg = tr("PIN demasiado curto"); return }
                if (vm.verifyPinCode(pin)) { vm.clearPinCode(); onSuccess() }
                else { errorMsg = tr("PIN incorrecto"); pin = "" }
            }
            PinMode.SET -> {
                if (!confirmStage) {
                    if (pin.length < 4) { errorMsg = tr("PIN demasiado curto"); return }
                    confirmStage = true
                } else {
                    if (confirm != pin) { errorMsg = tr("Os PINs não coincidem"); confirm = ""; confirmStage = false }
                    else { vm.setPinCode(pin); onSuccess() }
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                color = colors.ink,
                fontFamily = SerifFamily,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = colors.ink3, fontSize = 13.sp, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(28.dp))

            // Dot indicators
            val displayPin = if (mode == PinMode.SET && confirmStage) confirm else pin
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(maxOf(4, displayPin.length)) { i ->
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (i < displayPin.length) colors.accent else Color.Transparent)
                            .border(1.5.dp, if (i < displayPin.length) colors.accent else colors.rule, CircleShape),
                    )
                }
            }

            if (errorMsg != null) {
                Spacer(Modifier.height(10.dp))
                Text(errorMsg!!, color = Color(0xFFE53935), fontSize = 13.sp, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(32.dp))

            // Numpad
            val rows = listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("","0","⌫"))
            rows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                ) {
                    row.forEach { key ->
                        Box(
                            Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(if (key.isEmpty()) Color.Transparent else colors.paper2)
                                .border(if (key.isEmpty()) 0.dp else 1.dp, colors.rule, CircleShape)
                                .clickableNoRipple {
                                    when {
                                        key == "⌫" -> onDelete()
                                        key.isNotEmpty() -> onKey(key)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (key.isNotEmpty()) {
                                Text(
                                    key,
                                    color = if (key == "⌫") colors.ink3 else colors.ink,
                                    fontSize = if (key == "⌫") 20.sp else 22.sp,
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = if (key == "⌫") null else MonoFamily,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(8.dp))

            // Submit button
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.accent)
                    .clickableNoRipple { onSubmit() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (mode == PinMode.SET && !confirmStage) tr("Continuar") else tr("Confirmar"),
                    color = colors.onDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            // C3: re-summon the biometric sheet (e.g. after dismissing it once).
            // // PT: reabrir a folha biométrica.
            if (biometricOffered) {
                Spacer(Modifier.height(18.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickableNoRipple { launchBiometric() },
                ) {
                    Icon(
                        Icons.Filled.Fingerprint,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(tr("Usar biometria"), color = colors.accent, fontSize = 15.sp)
                }
            }

            if (onCancel != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    tr("Cancelar"),
                    color = colors.ink3,
                    fontSize = 15.sp,
                    modifier = Modifier.clickableNoRipple(onCancel),
                )
            }
        }
    }
}
