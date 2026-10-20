package com.uzukip.gokapi.desktop.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme

private fun systemIsDark(): Boolean = currentSystemTheme == SystemTheme.DARK

/** The OS light or dark setting, polled so a change applies without a restart. */
@Composable
fun rememberSystemThemeIsDark(pollMillis: Long = 1_000L): State<Boolean> =
    produceState(systemIsDark(), pollMillis) {
        while (isActive) {
            value = systemIsDark()
            delay(pollMillis)
        }
    }
