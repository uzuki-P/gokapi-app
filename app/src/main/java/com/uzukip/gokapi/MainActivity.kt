package com.uzukip.gokapi

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.uzukip.gokapi.platform.MainActivityState
import com.uzukip.gokapi.platform.AppMessages
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.uzukip.gokapi.data.settings.Settings
import com.uzukip.gokapi.platform.vibrationMs
import com.uzukip.gokapi.ui.GokapiApp
import com.uzukip.gokapi.ui.theme.GokapiTheme

/** App-wide haptic hook honoring the vibration strength setting. */
val LocalVibrate = staticCompositionLocalOf { {} }

class MainActivity : ComponentActivity() {
    private val incomingIntent = mutableStateOf<Intent?>(null)
    private fun acceptIntent(intent: Intent) {
        if (intent.data != null || intent.hasExtra("file_id")) incomingIntent.value = Intent(intent)
        intent.getStringExtra("copy_link")?.let { link ->
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Gokapi link", link))
            AppMessages.message.value = "Link copied"
            intent.removeExtra("copy_link")
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); acceptIntent(intent) }
    override fun onResume() {
        super.onResume()
        MainActivityState.foreground = true
        val container = (application as GokapiApplication).container
        lifecycleScope.launch {
            container.settings.takeUploadLink()?.let { link ->
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Gokapi link", link))
                AppMessages.message.value = "Upload link copied"
            }
            if (container.settings.settings.first().notificationMode == com.uzukip.gokapi.data.settings.NotificationMode.INSTANT) {
                org.unifiedpush.android.connector.UnifiedPush.getAckDistributor(this@MainActivity)?.let {
                    org.unifiedpush.android.connector.UnifiedPush.register(this@MainActivity)
                }
            }
        }
    }
    override fun onPause() { MainActivityState.foreground = false; super.onPause() }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPeakRefreshRate()
        if (savedInstanceState == null) acceptIntent(intent)
        val container = (application as GokapiApplication).container
        setContent {
            val settings by container.settings.settings
                .collectAsState(initial = Settings())
            CompositionLocalProvider(
                LocalVibrate provides { container.haptics.vibrate(settings.vibrationMs) },
            ) {
                GokapiTheme(settings = settings) {
                    GokapiApp(container, incomingIntent.value) { incomingIntent.value = null }
                }
            }
        }
    }

    /**
     * OEM frame-rate policy pins third-party apps to 60Hz unless the app
     * asks, so opt the window into the display's fastest mode at the current
     * resolution. Without this, every animation runs at half rate on 120Hz
     * panels no matter how cheap the frames are.
     */
    private fun requestPeakRefreshRate() {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        } ?: return
        val current = display.mode
        val peak = display.supportedModes
            .filter {
                it.physicalWidth == current.physicalWidth &&
                    it.physicalHeight == current.physicalHeight
            }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = peak.modeId
        }
    }
}
