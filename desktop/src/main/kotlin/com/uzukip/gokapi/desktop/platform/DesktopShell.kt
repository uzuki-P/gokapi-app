package com.uzukip.gokapi.desktop.platform

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Hands files and links to the operating system: open, reveal, clipboard, and the file picker. */
object DesktopShell {
    /** Opens [file] in its default app. */
    suspend fun open(file: File) = launch(file.path) { Desktop.getDesktop().open(file) }

    /**
     * Shows [file] selected in the file manager: Explorer on Windows, the D-Bus FileManager1
     * service on Linux. Falls back to opening its folder when neither is available.
     */
    suspend fun reveal(file: File) {
        val selected = withContext(Dispatchers.IO) {
            runCatching {
                val command = if (AppDirs.isWindows) {
                    // explorer.exe exits 1 even when it succeeds, so starting it counts as success.
                    listOf("explorer.exe", "/select,", file.absolutePath)
                } else {
                    listOf(
                        "dbus-send", "--session", "--print-reply", "--dest=org.freedesktop.FileManager1",
                        "/org/freedesktop/FileManager1", "org.freedesktop.FileManager1.ShowItems",
                        "array:string:${file.toURI()}", "string:",
                    )
                }
                val process = ProcessBuilder(command).redirectErrorStream(true).start()
                AppDirs.isWindows || (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0)
            }.getOrDefault(false)
        }
        if (!selected) open(file.parentFile)
    }

    /** Opens [url] in the default browser. */
    suspend fun browse(url: String) = launch(url) { Desktop.getDesktop().browse(java.net.URI(url)) }

    // AWT's Desktop needs GNOME libraries on Linux, so xdg-open comes first there.
    private suspend fun launch(target: String, awt: () -> Unit) = withContext(Dispatchers.IO) {
        if (!AppDirs.isWindows) {
            val started = runCatching { ProcessBuilder("xdg-open", target).redirectErrorStream(true).start() }.getOrNull()
            if (started != null) return@withContext
        }
        if (!Desktop.isDesktopSupported()) throw IOException("No app can open this file.")
        awt()
    }

    fun copy(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    /** The clipboard's text, or empty when it holds none. */
    fun pasteText(): String = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
    }.getOrNull().orEmpty()

    /** The native multi-select file picker; empty when cancelled. */
    fun pickFiles(owner: Frame?): List<File> {
        val dialog = FileDialog(owner, "Upload files", FileDialog.LOAD).apply { isMultipleMode = true }
        dialog.isVisible = true
        return dialog.files.orEmpty().filter(File::isFile)
    }
}
