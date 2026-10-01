package com.uzukip.gokapi

import android.app.Application
import com.uzukip.gokapi.data.settings.SettingsRepository
import com.uzukip.gokapi.platform.Haptics

/** Manual DI: app-wide singletons, created once in [GokapiApplication]. */
class AppContainer(context: Application, val client: com.uzukip.gokapi.data.server.GokapiClient = com.uzukip.gokapi.data.server.GokapiClient()) {

    val credentials = com.uzukip.gokapi.data.server.CredentialStore(context)
    val settings = SettingsRepository(context)
    val transfers = com.uzukip.gokapi.data.files.FileTransfers(context, client)
    val installer = com.uzukip.gokapi.platform.ApkInstaller(context)
    val haptics = Haptics(context)
}
