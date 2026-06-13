package com.pauta.app.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * C3: biometric-unlock helpers. We only gate the UI (no CryptoObject), so the
 * weak biometric class is enough — any enrolled fingerprint/face qualifies, and a
 * strong sensor satisfies it too — while the PIN keypad is always the fallback.
 * // PT: ajudas do desbloqueio biométrico; só protegemos a UI, logo a classe
 * "fraca" basta e o PIN é sempre a alternativa.
 */

/** True when the device has biometric hardware with something enrolled. Gates
 *  both the Settings toggle and whether the lock screen offers biometrics — so no
 *  hardware (or nothing enrolled) means today's PIN-only behaviour, exactly.
 *  // PT: há biometria utilizável? Sem ela, fica só o PIN, como hoje. */
fun Context.canUseBiometric(): Boolean =
    BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

/** Walk the ContextWrapper chain to the hosting FragmentActivity, which
 *  BiometricPrompt requires; null if not hosted by our activity. // PT:
 *  desembrulha o contexto até à FragmentActivity que aloja a folha biométrica. */
fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Show the system biometric sheet. Success unlocks; the negative button ("use
 * PIN") and any error/cancel just dismiss back to the PIN keypad silently — the
 * PIN path is never altered. Caller pre-resolves the strings (via tr()) so this
 * stays non-composable. // PT: mostra a folha biométrica do sistema; sucesso
 * desbloqueia, o resto volta ao teclado do PIN sem ruído.
 */
fun promptBiometric(
    activity: FragmentActivity,
    title: String,
    negativeText: String,
    onSuccess: () -> Unit,
) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            // onAuthenticationError / onAuthenticationFailed are intentionally left
            // to default: the user just stays on the PIN keypad. // PT: erro/falha
            // → fica no teclado do PIN.
        },
    )
    // Biometric-only (no DEVICE_CREDENTIAL), so a negative button is required and
    // routes back to our own PIN fallback. // PT: só biometria → botão negativo
    // obrigatório, que volta ao PIN.
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setNegativeButtonText(negativeText)
        .setAllowedAuthenticators(BIOMETRIC_WEAK)
        .build()
    prompt.authenticate(info)
}
