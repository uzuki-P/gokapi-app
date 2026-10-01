package com.uzukip.gokapi

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import com.uzukip.gokapi.data.settings.Settings
import com.uzukip.gokapi.ui.theme.GokapiTheme
import com.uzukip.gokapi.ui.upload.UploadSheet

class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }.filter { it.scheme == "content" }.distinct()
        if (uris.isEmpty()) { finish(); return }
        enableEdgeToEdge()
        val container = (application as GokapiApplication).container
        setContent {
            val settings by container.settings.settings.collectAsState(initial = Settings())
            GokapiTheme(settings) { Surface {
                UploadSheet(container, uris, onDismiss = ::finish, onQueued = ::finish)
            } }
        }
    }
}
