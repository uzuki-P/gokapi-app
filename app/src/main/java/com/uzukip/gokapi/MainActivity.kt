package com.uzukip.gokapi

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPeakRefreshRate()
        val container = (application as GokapiApplication).container
        setContent {
            val settings by container.settings.settings
                .collectAsState(initial = Settings())
            CompositionLocalProvider(
                LocalVibrate provides { container.haptics.vibrate(settings.vibrationMs) },
            ) {
                GokapiTheme(settings = settings) {
                    GokapiApp(container)
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
