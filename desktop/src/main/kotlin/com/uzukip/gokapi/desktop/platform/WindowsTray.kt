package com.uzukip.gokapi.desktop.platform

import java.awt.EventQueue
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BaseMultiResolutionImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notification-area icon through AWT `SystemTray`, with a native popup menu. A left click
 * shows or hides the window; a right click opens the menu. Notifications show as
 * Windows toasts from the icon. Adapted from metrolist-desktop's
 * WindowsTray, which draws its own themed menu for its longer player menu.
 */
class WindowsTray(private val model: AppTrayModel) : AppTray {
    private val availableState = MutableStateFlow(false)
    override val available: StateFlow<Boolean> = availableState.asStateFlow()
    private var trayIcon: TrayIcon? = null

    override fun start() {
        if (trayIcon != null) return
        EventQueue.invokeLater {
            try {
                if (!SystemTray.isSupported()) return@invokeLater
                val variants = ICON_SIZES.mapNotNull(TrayIconImages::render)
                if (variants.isEmpty()) return@invokeLater
                val toggle = MenuItem()
                val menu = PopupMenu().apply {
                    add(toggle.apply { addActionListener { model.onToggleWindow() } })
                    add(MenuItem("Upload files…").apply { addActionListener { model.onUpload() } })
                    addSeparator()
                    add(MenuItem("Quit").apply { addActionListener { model.onQuit() } })
                }
                val icon = TrayIcon(BaseMultiResolutionImage(*variants.toTypedArray()), model.title, menu).apply {
                    isImageAutoSize = true
                    // A click on a notification balloon.
                    addActionListener { model.onNotificationClicked() }
                    addMouseListener(object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent) {
                            if (e.button == MouseEvent.BUTTON1 && e.clickCount == 1) model.onToggleWindow()
                        }

                        // Label the item for the current state just before the menu opens.
                        override fun mousePressed(e: MouseEvent) {
                            toggle.label = if (model.windowVisible.value) "Hide window" else "Show window"
                        }
                    })
                }
                toggle.label = if (model.windowVisible.value) "Hide window" else "Show window"
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
        EventQueue.invokeLater { runCatching { SystemTray.getSystemTray().remove(icon) } }
    }

    private companion object {
        /** 16 px at 100% scale up to 32 px at 200%. */
        val ICON_SIZES = listOf(16, 20, 24, 32, 40, 48, 64)
    }
}
