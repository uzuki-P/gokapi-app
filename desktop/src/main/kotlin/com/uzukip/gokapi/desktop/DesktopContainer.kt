package com.uzukip.gokapi.desktop

import com.uzukip.gokapi.data.server.GokapiClient
import com.uzukip.gokapi.desktop.data.CredentialStore
import com.uzukip.gokapi.desktop.data.SeenFiles
import com.uzukip.gokapi.desktop.data.SettingsStore
import com.uzukip.gokapi.desktop.notify.NtfyConfig
import com.uzukip.gokapi.desktop.platform.AppDirs
import java.io.File

/** Manual DI, as in the Android app's AppContainer: app-wide singletons created once in main(). */
class DesktopContainer(
    val dirs: AppDirs = AppDirs(),
    val client: GokapiClient = GokapiClient(),
) {
    val credentials = CredentialStore(File(dirs.configDir, "credentials.properties"))
    val settings = SettingsStore(File(dirs.configDir, "settings.properties"))

    /** The ntfy topic URL and its access token or `user:password`, for instant notifications. */
    val ntfy = NtfyConfig(CredentialStore(File(dirs.configDir, "ntfy.properties")))
    val seenFiles = SeenFiles(File(dirs.configDir, "seen-files.properties"))
}
