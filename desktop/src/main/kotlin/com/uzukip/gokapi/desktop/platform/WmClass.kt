package com.uzukip.gokapi.desktop.platform

import java.awt.Toolkit

/**
 * Sets the X11 `WM_CLASS` that AWT gives every window, so KDE matches the windows to the
 * launcher entry (`StartupWMClass=gokapi-desktop`) for the taskbar icon and grouping.
 * AWT otherwise uses the main class name. Needs
 * `--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED`; without it this does nothing.
 * Call before the first window opens. Ported from metrolist-desktop.
 */
fun setLinuxWmClass(name: String) {
    if (!System.getProperty("os.name").orEmpty().startsWith("Linux")) return
    runCatching {
        val toolkit = Toolkit.getDefaultToolkit()
        val field = toolkit.javaClass.getDeclaredField("awtAppClassName")
        field.isAccessible = true
        field.set(toolkit, name)
    }
}
