package com.uzukip.gokapi

import android.app.Application
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class GokapiApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.transfers.prune()
            com.uzukip.gokapi.platform.scheduleNotifications(this@GokapiApplication, container.settings.settings.first().notificationMode)
        }
    }
}
