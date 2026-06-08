package com.pauta.app.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Process-wide bus for notification-button actions (pause / resume / conclude).
 * FocusActionReceiver emits onto it; MainActivity collects and reconciles the
 * store. Replaces the Capacitor JS bridge that the WebView version used.
 *
 * extraBufferCapacity keeps emits from a BroadcastReceiver non-suspending — the
 * receiver has no coroutine scope, so tryEmit must succeed without a collector.
 */
object FocusActionBus {
    private val _actions = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 8)
    val actions: SharedFlow<String> = _actions

    fun emit(kind: String) {
        _actions.tryEmit(kind)
    }

    // Separate channel for "open the focus screen" (launcher shortcut / QS tile).
    private val _openFocus = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val openFocus: SharedFlow<Unit> = _openFocus

    fun requestOpenFocus() {
        _openFocus.tryEmit(Unit)
    }
}
