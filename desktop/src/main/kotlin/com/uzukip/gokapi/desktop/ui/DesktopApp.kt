package com.uzukip.gokapi.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import com.uzukip.gokapi.desktop.DesktopContainer
import java.io.File

private enum class Screen { HOME, SETTINGS }

/**
 * Home and Settings with the Android app's shared-axis X motion: the incoming screen slides
 * a quarter width and fades in, the covered one drifts back. Back plays it in reverse.
 */
@Composable
internal fun DesktopApp(container: DesktopContainer, keys: KeyActions, onPickFiles: () -> List<File>) {
    val scope = rememberCoroutineScope()
    val files = remember { FilesModel(container, scope).also { it.refresh() } }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var serverChanged by remember { mutableStateOf(false) }
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = screen,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        transitionSpec = {
            val spatial = motion.defaultSpatialSpec<IntOffset>()
            if (targetState == Screen.SETTINGS) {
                (slideInHorizontally(spatial) { it / 4 } + fadeIn(motion.defaultEffectsSpec()))
                    .togetherWith(slideOutHorizontally(spatial) { -it / 10 } + fadeOut(motion.fastEffectsSpec()))
            } else {
                (slideInHorizontally(spatial) { -it / 10 } + fadeIn(motion.defaultEffectsSpec()))
                    .togetherWith(slideOutHorizontally(spatial) { it / 4 } + scaleOut(motion.defaultSpatialSpec(), targetScale = 0.94f) + fadeOut(motion.fastEffectsSpec()))
            }
        },
        label = "screen",
    ) { target ->
        when (target) {
            Screen.HOME -> FilesScreen(files, keys, onPickFiles, onOpenSettings = { screen = Screen.SETTINGS })
            Screen.SETTINGS -> SettingsScreen(
                container,
                keys,
                onServerSaved = { serverChanged = true },
                onBack = {
                    screen = Screen.HOME
                    if (serverChanged) { serverChanged = false; files.refresh() }
                },
            )
        }
    }
}
