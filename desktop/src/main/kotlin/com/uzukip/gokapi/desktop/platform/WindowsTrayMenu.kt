package com.uzukip.gokapi.desktop.platform

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.uzukip.gokapi.ui.components.MciIcon
import java.awt.GraphicsEnvironment
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import org.jetbrains.skia.Image as SkiaImage

/**
 * The tray icon's menu as a small Compose window in the app's theme, adapted from
 * metrolist-desktop's WindowsTrayMenu. AWT's `PopupMenu` draws Windows' classic menu, which
 * ignores the theme and dark mode and lacks glyphs such as "…". The window exists only while
 * the menu is open. Clicking outside (focus loss) or Esc closes it. Call from the AWT thread.
 */
internal class WindowsTrayMenu(private val model: AppTrayModel) {
    private var window: ComposeWindow? = null

    /** When focus loss last closed the menu: a click on the tray icon does that first. */
    private var closedAt = 0L

    /** Opens the menu next to [anchor] (the pointer, AWT screen coordinates), or closes it. */
    fun toggle(anchor: Point) {
        if (window != null) close()
        else if (System.currentTimeMillis() - closedAt > REOPEN_GUARD_MS) open(anchor)
    }

    // Compose only sees keys once one of its nodes has focus, and the menu has none.
    private val escapeCloses = KeyEventDispatcher { e ->
        (window != null && e.id == KeyEvent.KEY_PRESSED && e.keyCode == KeyEvent.VK_ESCAPE).also { if (it) close() }
    }

    fun close() {
        val open = window ?: return
        window = null
        closedAt = System.currentTimeMillis()
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(escapeCloses)
        open.dispose()
    }

    private fun open(anchor: Point) {
        // ComposeWindow sizes and places in AWT units, which on Windows are dp.
        val width = (MENU_WIDTH + MARGIN * 2).toInt()
        val height = (MARGIN * 2 + LIST_PADDING * 2 + HEADER_HEIGHT + ITEM_HEIGHT * 3 + DIVIDER_HEIGHT).toInt()
        val menu = ComposeWindow().apply {
            isUndecorated = true
            isTransparent = true
            isAlwaysOnTop = true
            // Keeps it out of the taskbar and Alt+Tab.
            type = Window.Type.POPUP
            setSize(width, height)
            location = trayMenuPlacement(anchor, width, height, MARGIN.toInt())
            addWindowFocusListener(object : WindowAdapter() {
                override fun windowLostFocus(e: WindowEvent) {
                    if (window === this@apply) close()
                }
            })
            setContent {
                model.theme {
                    TrayMenuContent(model) { action ->
                        close()
                        action()
                    }
                }
            }
        }
        window = menu
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(escapeCloses)
        menu.isVisible = true
        menu.toFront()
        // The shell lets the process that owns the clicked tray icon take the foreground;
        // without it the menu never gets focus and so never closes on an outside click.
        runCatching { Native.getWindowPointer(menu)?.let { User32.INSTANCE.SetForegroundWindow(HWND(it)) } }
        menu.requestFocus()
    }

    private companion object {
        const val REOPEN_GUARD_MS = 300L
        const val MENU_WIDTH = 240f

        /** Room around the menu surface for its shadow. */
        const val MARGIN = 10f
        const val LIST_PADDING = 6f
        const val HEADER_HEIGHT = 44f
        const val ITEM_HEIGHT = 40f
        const val DIVIDER_HEIGHT = 9f
    }
}

/**
 * Top-left corner for a [width] x [height] menu opened at [anchor]: above and right of the
 * pointer like Windows' own tray menus, flipped and clamped to stay off the taskbar. [margin]
 * is the transparent shadow border, so the surface's corner is the one that meets the pointer.
 */
private fun trayMenuPlacement(anchor: Point, width: Int, height: Int, margin: Int): Point {
    val config = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        .map { it.defaultConfiguration }
        .firstOrNull { it.bounds.contains(anchor) }
        ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
    val b = config.bounds
    val usable = Rectangle(b.x + insets.left, b.y + insets.top, b.width - insets.left - insets.right, b.height - insets.top - insets.bottom)
    var x = anchor.x - margin
    if (x + width > usable.x + usable.width) x = anchor.x - width + margin
    var y = anchor.y - height + margin
    if (y < usable.y) y = anchor.y - margin
    return Point(
        x.coerceIn(usable.x, (usable.x + usable.width - width).coerceAtLeast(usable.x)),
        y.coerceIn(usable.y, (usable.y + usable.height - height).coerceAtLeast(usable.y)),
    )
}

private val appIcon by lazy {
    TrayIconImages.render(64)?.let { image ->
        val bytes = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(image, "png", it) }.toByteArray()
        BitmapPainter(SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap())
    }
}

@Composable
private fun TrayMenuContent(model: AppTrayModel, run: (() -> Unit) -> Unit) {
    val windowVisible by model.windowVisible.collectAsState()
    Surface(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(6.dp)) {
            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                appIcon?.let { Image(it, contentDescription = null, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Text(model.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MenuItem(if (windowVisible) "Hide window" else "Show window", if (windowVisible) "eye-off-outline" else "application-outline") { run(model.onToggleWindow) }
            MenuItem("Upload files…", "cloud-upload-outline") { run(model.onUpload) }
            Column(Modifier.height(9.dp), verticalArrangement = Arrangement.Center) {
                HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
            MenuItem("Quit", "power") { run(model.onQuit) }
        }
    }
}

@Composable
private fun MenuItem(label: String, glyph: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MciIcon(glyph, 20.dp, MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}
