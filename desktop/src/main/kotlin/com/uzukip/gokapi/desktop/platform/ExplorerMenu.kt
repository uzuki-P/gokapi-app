package com.uzukip.gokapi.desktop.platform

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File

/**
 * "Upload to Gokapi" in Explorer's classic right-click menu (Show more options on Windows 11),
 * for any file. A static verb under `HKCU\Software\Classes\*\shell`: per user, no admin, and
 * no shell extension. Explorer runs `"<launcher>" "<file>"` once per selected file, up to its
 * limit of 15; each launch hands its file to the running app ([SingleInstance]).
 *
 * The jpackage installer cannot remove the key on uninstall, so turn the setting off first.
 */
object ExplorerMenu {
    private const val KEY = "Software\\Classes\\*\\shell\\GokapiUpload"
    private const val LABEL = "Upload to Gokapi"

    /** The installed app's launcher EXE, which jpackage sets; null when run from source or off Windows. */
    val launcher: File?
        get() = if (!AppDirs.isWindows) null
        else System.getProperty("jpackage.app-path")?.let(::File)?.takeIf(File::isFile)

    /** Adds or removes the menu entry; does nothing without an installed launcher. */
    fun sync(enabled: Boolean) {
        val exe = launcher ?: return
        runCatching { if (enabled) register(exe) else unregister() }
            .onFailure { System.err.println("Couldn't update the Explorer menu: ${it.message}") }
    }

    private fun register(exe: File) {
        val root = WinReg.HKEY_CURRENT_USER
        Advapi32Util.registryCreateKey(root, "$KEY\\command")
        Advapi32Util.registrySetStringValue(root, KEY, "", LABEL)
        Advapi32Util.registrySetStringValue(root, KEY, "Icon", "${exe.absolutePath},0")
        Advapi32Util.registrySetStringValue(root, "$KEY\\command", "", "\"${exe.absolutePath}\" \"%1\"")
    }

    private fun unregister() {
        val root = WinReg.HKEY_CURRENT_USER
        if (Advapi32Util.registryKeyExists(root, "$KEY\\command")) Advapi32Util.registryDeleteKey(root, "$KEY\\command")
        if (Advapi32Util.registryKeyExists(root, KEY)) Advapi32Util.registryDeleteKey(root, KEY)
    }
}
