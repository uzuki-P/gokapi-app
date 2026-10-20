package com.uzukip.gokapi.desktop.platform

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Hands files and links to the operating system: open, reveal, clipboard, and the file picker. */
object DesktopShell {
    /** Opens [file] in its default app. */
    suspend fun open(file: File) = launch(file.path) { Desktop.getDesktop().open(file) }

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

    /** The native multi-select file picker; empty when cancelled. */
    fun pickFiles(owner: Frame?): List<File> {
        val dialog = FileDialog(owner, "Upload files", FileDialog.LOAD).apply { isMultipleMode = true }
        dialog.isVisible = true
        return dialog.files.orEmpty().filter(File::isFile)
    }
}
