package com.uzukip.gokapi.desktop.platform

import java.awt.EventQueue
import java.awt.MouseInfo
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BaseMultiResolutionImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notification-area icon through AWT `SystemTray`, with the menu drawn by [WindowsTrayMenu]
 * in the app's theme. A left click shows or hides the window; a right click opens the menu.
 * Notifications show as Windows toasts from the icon. Adapted from metrolist-desktop's
 * WindowsTray.
 */
class WindowsTray(private val model: AppTrayModel) : AppTray {
    private val availableState = MutableStateFlow(false)
    override val available: StateFlow<Boolean> = availableState.asStateFlow()
    private var trayIcon: TrayIcon? = null
    private val menu = WindowsTrayMenu(model)

    override fun start() {
        if (trayIcon != null) return
        EventQueue.invokeLater {
            try {
                if (!SystemTray.isSupported()) return@invokeLater
                val variants = ICON_SIZES.mapNotNull(TrayIconImages::render)
                if (variants.isEmpty()) return@invokeLater
                val icon = TrayIcon(BaseMultiResolutionImage(*variants.toTypedArray()), model.title).apply {
                    isImageAutoSize = true
                    // A click on a notification balloon.
                    addActionListener { model.onNotificationClicked() }
                    addMouseListener(object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent) {
                            if (e.button == MouseEvent.BUTTON1 && e.clickCount == 1) model.onToggleWindow()
                        }

                        // Windows reports the popup trigger on release.
                        override fun mouseReleased(e: MouseEvent) {
                            if (e.isPopupTrigger || e.button == MouseEvent.BUTTON3) {
                                // Tray icon events carry screen coordinates.
                                menu.toggle(MouseInfo.getPointerInfo()?.location ?: e.point)
                            }
                        }
                    })
                }
                SystemTray.getSystemTray().add(icon)
                trayIcon = icon
                availableState.value = true
            } catch (t: Throwable) {
                close()
            }
        }
    }

    override fun notify(title: String, body: String) {
        EventQueue.invokeLater { runCatching { trayIcon?.displayMessage(title, body, TrayIcon.MessageType.NONE) } }
    }

    override fun close() {
        availableState.value = false
        val icon = trayIcon ?: return
        trayIcon = null
        EventQueue.invokeLater {
            menu.close()
            runCatching { SystemTray.getSystemTray().remove(icon) }
        }
    }

    private companion object {
        /** 16 px at 100% scale up to 32 px at 200%. */
        val ICON_SIZES = listOf(16, 20, 24, 32, 40, 48, 64)
    }
}
