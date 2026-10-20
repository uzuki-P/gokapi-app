package com.uzukip.gokapi.desktop.platform

import androidx.compose.runtime.Composable
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's tray icon and menu: StatusNotifierItem on Linux, AWT `SystemTray` on Windows.
 * [available] is true only while the desktop shows the icon, so close-to-tray never hides
 * the window with no way back. Adapted from metrolist-desktop's AppTray.
 */
interface AppTray : AutoCloseable {
    val available: StateFlow<Boolean>

    /** Registers the icon. Never throws; on failure [available] stays false. */
    fun start()

    /** Shows a desktop notification; clicking it calls [AppTrayModel.onNotificationClicked]. Never throws. */
    fun notify(title: String, body: String)

    override fun close()
}

/** Menu state and actions. Callbacks may run off the UI thread. */
class AppTrayModel(
    val title: String,
    val windowVisible: StateFlow<Boolean>,
    val onToggleWindow: () -> Unit,
    val onUpload: () -> Unit,
    val onQuit: () -> Unit,
    val onNotificationClicked: () -> Unit,
    /** Wraps the Windows tray menu in the app's theme. */
    val theme: @Composable (@Composable () -> Unit) -> Unit = { it() },
)

object AppTrays {
    fun create(model: AppTrayModel, scope: CoroutineScope): AppTray = when {
        AppDirs.isWindows -> WindowsTray(model)
        System.getProperty("os.name").orEmpty().startsWith("Linux") -> LinuxSniTray(model, scope)
        else -> UnavailableAppTray
    }
}

private object UnavailableAppTray : AppTray {
    override val available: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override fun start() {}
    override fun notify(title: String, body: String) {}
    override fun close() {}
}

/** The launcher icon scaled to the sizes a tray host picks from. */
internal object TrayIconImages {
    private val source: BufferedImage? by lazy {
        TrayIconImages::class.java.getResourceAsStream("/icons/gokapi-desktop.png")?.use(ImageIO::read)
    }

    fun render(size: Int): BufferedImage? {
        val image = source ?: return null
        return BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).apply {
            createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                drawImage(image, 0, 0, size, size, null)
                dispose()
            }
        }
    }

    /** SNI `IconPixmap` data: ARGB32 in network byte order (A, R, G, B per pixel). */
    fun argbNetworkOrder(image: BufferedImage): ByteArray {
        val argb = IntArray(image.width * image.height)
        image.getRGB(0, 0, image.width, image.height, argb, 0, image.width)
        val data = ByteArray(argb.size * 4)
        var index = 0
        for (pixel in argb) {
            data[index++] = ((pixel ushr 24) and 0xFF).toByte()
            data[index++] = ((pixel shr 16) and 0xFF).toByte()
            data[index++] = ((pixel shr 8) and 0xFF).toByte()
            data[index++] = (pixel and 0xFF).toByte()
        }
        return data
    }
}
