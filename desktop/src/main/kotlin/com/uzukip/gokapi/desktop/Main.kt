package com.uzukip.gokapi.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.uzukip.gokapi.desktop.notify.NewFileWatcher
import com.uzukip.gokapi.desktop.platform.AppDirs
import com.uzukip.gokapi.desktop.platform.AppTrayModel
import com.uzukip.gokapi.desktop.platform.AppTrays
import com.uzukip.gokapi.desktop.platform.DesktopShell
import com.uzukip.gokapi.desktop.platform.Launch
import com.uzukip.gokapi.desktop.platform.SingleInstance
import com.uzukip.gokapi.desktop.platform.setLinuxWmClass
import com.uzukip.gokapi.desktop.ui.DesktopApp
import com.uzukip.gokapi.desktop.ui.KeyActions
import com.uzukip.gokapi.ui.theme.GokapiTheme
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Frame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.concurrent.thread
import org.jetbrains.skia.Image

fun main() = runApp(DesktopContainer())

/** Opens the app window for [container]; the mock app in the tests passes one with fake data. */
fun runApp(container: DesktopContainer, title: String = "Files - Gokapi Desktop") {
    // A second launch, for example from the app menu while the window sits in the tray,
    // shows the running app's window instead of starting another app.
    val launch = SingleInstance.acquire(container.dirs.configDir)
    if (launch !is Launch.Primary) return
    // Before any window exists, so KDE matches windows to the gokapi-desktop launcher entry.
    setLinuxWmClass(AppDirs.APP_ID)
    val icon = object {}.javaClass.getResourceAsStream("/icons/gokapi-desktop.png")!!.use {
        BitmapPainter(Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap())
    }
    application {
        // Quit from the tray, or closing the window without the tray, ends the whole process.
        val quit = remember { { quitApp(::exitApplication) } }
        val settings by container.settings.settings.collectAsState()
        val keys = remember { KeyActions() }
        val scope = rememberCoroutineScope()
        val windowVisible = remember { MutableStateFlow(true) }
        val raiseRequests = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
        val uploadRequests = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
        val showWindow = {
            windowVisible.value = true
            raiseRequests.tryEmit(Unit)
        }
        // Tray callbacks arrive on the D-Bus or AWT thread.
        val tray = remember {
            AppTrays.create(
                AppTrayModel(
                    title = title,
                    windowVisible = windowVisible,
                    onToggleWindow = { if (windowVisible.value) windowVisible.value = false else showWindow() },
                    onUpload = { showWindow(); uploadRequests.tryEmit(Unit) },
                    onQuit = { EventQueue.invokeLater(quit) },
                    onNotificationClicked = { showWindow() },
                    theme = { content ->
                        val current by container.settings.settings.collectAsState()
                        GokapiTheme(current) { content() }
                    },
                ),
                scope,
            )
        }
        // New-file notifications run while the app runs, window shown or not.
        val fileEvents = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
        val watcher = remember { NewFileWatcher(container, scope, tray::notify, onNewFiles = { fileEvents.tryEmit(Unit) }) }
        DisposableEffect(tray) {
            tray.start()
            watcher.start()
            launch.instance?.listen { showWindow() }
            onDispose {
                watcher.stop()
                tray.close()
                launch.instance?.close()
            }
        }
        val trayAvailable by tray.available.collectAsState()
        val visible by windowVisible.collectAsState()
        Window(
            // Hide to the tray only while the tray icon is showing, so the app never keeps
            // running with no way back to it.
            onCloseRequest = { if (settings.closeToTray && tray.available.value) windowVisible.value = false else quit() },
            visible = visible,
            title = title,
            icon = icon,
            state = rememberWindowState(size = DpSize(1200.dp, 820.dp)),
            onKeyEvent = keys::onKeyEvent,
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(480, 560) }
            RaiseWindowEffect(window, raiseRequests)
            GokapiTheme(settings) {
                DesktopApp(container, keys, trayAvailable, uploadRequests, fileEvents, onPickFiles = { DesktopShell.pickFiles(window) })
            }
        }
    }
}

/**
 * Ends the app: [exitApplication] closes the window and runs the effects' cleanup (tray, watcher,
 * single-instance lock), after which Compose exits the process. A daemon watchdog makes sure of
 * it: if cleanup or a stuck network read keeps the JVM up for 3 s, it exits anyway, so Quit never
 * leaves Gokapi running in the background with no tray icon.
 */
private fun quitApp(exitApplication: () -> Unit) {
    thread(name = "gokapi-quit-watchdog", isDaemon = true) {
        Thread.sleep(3_000)
        Runtime.getRuntime().halt(0)
    }
    exitApplication()
}

/** Brings [window] to the front when the tray or a second launch asks for it (from metrolist-desktop). */
@androidx.compose.runtime.Composable
private fun RaiseWindowEffect(window: ComposeWindow, requests: SharedFlow<Unit>) {
    LaunchedEffect(window) {
        requests.collect {
            // `visible = true` reaches AWT on the next recomposition.
            repeat(20) { if (!window.isVisible) delay(25) }
            if (window.extendedState and Frame.ICONIFIED != 0) {
                window.extendedState = window.extendedState and Frame.ICONIFIED.inv()
            }
            // KWin's focus stealing prevention may only mark the window as demanding
            // attention under XWayland; toFront is still the right request.
            window.toFront()
            window.requestFocus()
        }
    }
}
