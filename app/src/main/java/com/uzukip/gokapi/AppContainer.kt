package com.uzukip.gokapi

import android.app.Application
import com.uzukip.gokapi.data.settings.SettingsRepository
import com.uzukip.gokapi.platform.Haptics

/** Manual DI: app-wide singletons, created once in [GokapiApplication]. */
class AppContainer(context: Application) {

    val settings = SettingsRepository(context)
    val haptics = Haptics(context)
}
