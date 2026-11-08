package com.uzukip.gokapi

import android.app.Application
import androidx.compose.ui.graphics.asImageBitmap
import com.uzukip.gokapi.data.settings.SettingsRepository
import com.uzukip.gokapi.platform.Haptics

/** Manual DI: app-wide singletons, created once in [GokapiApplication]. */
class AppContainer(context: Application, val client: com.uzukip.gokapi.data.server.GokapiClient = com.uzukip.gokapi.data.server.GokapiClient()) {

    val credentials = com.uzukip.gokapi.data.server.CredentialStore(context)
    val settings = SettingsRepository(context)
    val transfers = com.uzukip.gokapi.data.files.FileTransfers(context, client)
    val installer = com.uzukip.gokapi.platform.ApkInstaller(context)
    val haptics = Haptics(context)
    val previews = com.uzukip.gokapi.data.server.PreviewStore(java.io.File(context.cacheDir, "previews"), client)
    /** Set by the file list once credentials load; the UI reads it through LocalPreviewSource. */
    val previewSource = kotlinx.coroutines.flow.MutableStateFlow<com.uzukip.gokapi.ui.components.PreviewSource?>(null)

    fun usePreviews(c: com.uzukip.gokapi.data.server.ServerCredentials) {
        if (previewSource.value?.uses(c) == true) return
        previewSource.value = com.uzukip.gokapi.ui.components.PreviewSource(previews, c) { bytes ->
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
}
