package com.uzukip.gokapi.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.uzukip.gokapi.desktop.platform.AppDirs
import com.uzukip.gokapi.desktop.platform.DesktopShell
import com.uzukip.gokapi.desktop.platform.setLinuxWmClass
import com.uzukip.gokapi.desktop.ui.DesktopApp
import com.uzukip.gokapi.desktop.ui.KeyActions
import com.uzukip.gokapi.ui.theme.GokapiTheme
import java.awt.Dimension
import org.jetbrains.skia.Image

fun main() = runApp(DesktopContainer())

/** Opens the app window for [container]; the mock app in the tests passes one with fake data. */
fun runApp(container: DesktopContainer, title: String = "Gokapi") {
    // Before any window exists, so KDE matches windows to the gokapi-desktop launcher entry.
    setLinuxWmClass(AppDirs.APP_ID)
    val icon = object {}.javaClass.getResourceAsStream("/icons/gokapi-desktop.png")!!.use {
        BitmapPainter(Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap())
    }
    application {
        val settings by container.settings.settings.collectAsState()
        val keys = remember { KeyActions() }
        Window(
            onCloseRequest = ::exitApplication,
            title = title,
            icon = icon,
            state = rememberWindowState(size = DpSize(960.dp, 820.dp)),
            onKeyEvent = keys::onKeyEvent,
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(480, 560) }
            GokapiTheme(settings) {
                DesktopApp(container, keys, onPickFiles = { DesktopShell.pickFiles(window) })
            }
        }
    }
}
