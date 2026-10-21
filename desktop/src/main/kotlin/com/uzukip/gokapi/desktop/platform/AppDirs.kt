package com.uzukip.gokapi.desktop.platform

import java.io.File

/** Where the app keeps its settings and downloaded-file cache. */
class AppDirs(
    private val env: (String) -> String? = System::getenv,
    private val home: File = File(System.getProperty("user.home")),
    private val windows: Boolean = isWindows,
) {
    /** Settings and the saved server credentials. */
    val configDir: File get() = if (windows) File(windowsRoot, "config") else xdg("XDG_CONFIG_HOME", ".config")

    /** Files downloaded for Open; safe to delete. */
    val cacheDir: File get() = if (windows) File(windowsRoot, "cache") else xdg("XDG_CACHE_HOME", ".cache")

    /** Where Save to Downloads puts files, as the Android app's Downloads/Gokapi. */
    val downloadsDir: File get() = File(xdgDownloads() ?: File(home, "Downloads"), "Gokapi")

    private fun xdg(variable: String, fallback: String): File {
        val base = env(variable)?.takeIf { it.isNotBlank() && File(it).isAbsolute }?.let(::File) ?: File(home, fallback)
        return File(base, APP_ID)
    }

    // The per-user installer puts the app in %LOCALAPPDATA%\Files - Gokapi Desktop, so data uses another name.
    private val windowsRoot: File
        get() = File(env("LOCALAPPDATA")?.takeIf { it.isNotBlank() && File(it).isAbsolute }?.let(::File) ?: File(home, "AppData/Local"), "GokapiDesktop")

    /** `XDG_DOWNLOAD_DIR` from user-dirs.dirs, which localized desktops rename. */
    private fun xdgDownloads(): File? {
        if (windows) return null
        val config = env("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, ".config")
        val line = runCatching { File(config, "user-dirs.dirs").readLines() }.getOrNull()
            ?.firstOrNull { it.startsWith("XDG_DOWNLOAD_DIR=") } ?: return null
        val value = line.substringAfter('=').trim().removeSurrounding("\"").replace("\$HOME", home.path)
        return File(value).takeIf { it.isAbsolute }
    }

    companion object {
        const val APP_ID = "gokapi-desktop"
        val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")
    }
}
