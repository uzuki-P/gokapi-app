package com.uzukip.gokapi.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uzukip.gokapi.LocalVibrate
import com.uzukip.gokapi.ui.components.BouncyIconButton
import com.uzukip.gokapi.ui.components.BouncyTonalButton
import com.uzukip.gokapi.ui.components.EmptyState
import com.uzukip.gokapi.ui.components.GokapiTopBar
import com.uzukip.gokapi.ui.components.MciIcon

/** Files home. Empty until the file list lands (plan.md M2). */
@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    val vibrate = LocalVibrate.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            GokapiTopBar(
                title = "Gokapi",
                onBack = null,
                actions = {
                    BouncyIconButton(onClick = onOpenSettings) {
                        MciIcon("cog-outline", 24.dp, LocalContentColor.current, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                glyph = "cloud-download-outline",
                title = "No files yet",
                message = "Shared files will show up here once the server is connected.",
                action = {
                    BouncyTonalButton(
                        onClick = {
                            vibrate()
                            onOpenSettings()
                        },
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MciIcon("cog-outline", 18.dp, LocalContentColor.current)
                            Text("Open settings")
                        }
                    }
                },
            )
        }
    }
}
