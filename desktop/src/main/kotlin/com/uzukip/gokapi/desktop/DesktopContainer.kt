package com.uzukip.gokapi.desktop

import com.uzukip.gokapi.data.server.GokapiClient
import com.uzukip.gokapi.desktop.data.CredentialStore
import com.uzukip.gokapi.desktop.data.SettingsStore
import com.uzukip.gokapi.desktop.platform.AppDirs
import java.io.File

/** Manual DI, as in the Android app's AppContainer: app-wide singletons created once in main(). */
class DesktopContainer(
    val dirs: AppDirs = AppDirs(),
    val client: GokapiClient = GokapiClient(),
) {
    val credentials = CredentialStore(File(dirs.configDir, "credentials.properties"))
    val settings = SettingsStore(File(dirs.configDir, "settings.properties"))
}
