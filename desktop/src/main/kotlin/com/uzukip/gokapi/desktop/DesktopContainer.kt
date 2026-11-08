package com.uzukip.gokapi.desktop

import androidx.compose.ui.graphics.toComposeImageBitmap
import com.uzukip.gokapi.data.server.GokapiClient
import com.uzukip.gokapi.data.server.PreviewStore
import com.uzukip.gokapi.data.server.ServerCredentials
import com.uzukip.gokapi.ui.components.PreviewSource
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.Image
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

    val previews = PreviewStore(File(dirs.cacheDir, "previews"), client)
    /** Set by the file list once credentials load; the UI reads it through LocalPreviewSource. */
    val previewSource = MutableStateFlow<PreviewSource?>(null)

    fun usePreviews(c: ServerCredentials) {
        if (previewSource.value?.uses(c) == true) return
        previewSource.value = PreviewSource(previews, c) { bytes ->
            runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
        }
    }
}
