package com.uzukip.gokapi.desktop.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

internal enum class AppKey { ESCAPE, REFRESH, UPLOAD, SELECT_ALL, DELETE }

/**
 * Window shortcuts that no focused element consumed. The visible screen sets [handler];
 * it returns true when it used the key.
 */
internal class KeyActions {
    var handler: (AppKey) -> Boolean = { false }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val key = when {
            event.key == Key.Escape -> AppKey.ESCAPE
            event.key == Key.F5 || (event.isCtrlPressed && event.key == Key.R) -> AppKey.REFRESH
            event.isCtrlPressed && event.key == Key.U -> AppKey.UPLOAD
            event.isCtrlPressed && event.key == Key.A -> AppKey.SELECT_ALL
            event.key == Key.Delete -> AppKey.DELETE
            else -> return false
        }
        return handler(key)
    }
}
