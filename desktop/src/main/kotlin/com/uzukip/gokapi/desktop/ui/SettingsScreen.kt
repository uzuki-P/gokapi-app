package com.uzukip.gokapi.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uzukip.gokapi.desktop.DesktopContainer
import com.uzukip.gokapi.desktop.data.ColorSource
import com.uzukip.gokapi.desktop.data.ThemeMode
import com.uzukip.gokapi.desktop.platform.appVersion
import com.uzukip.gokapi.ui.components.Choice
import com.uzukip.gokapi.ui.components.ConnectedChoices
import com.uzukip.gokapi.ui.components.ExpiryChoices
import com.uzukip.gokapi.ui.components.GokapiTopBar
import com.uzukip.gokapi.ui.components.IconBadge
import com.uzukip.gokapi.ui.components.MciIcon
import com.uzukip.gokapi.ui.components.MorphSurface
import com.uzukip.gokapi.ui.components.MorphingBadge
import com.uzukip.gokapi.ui.components.SegmentedGroup
import com.uzukip.gokapi.ui.components.ThemedTextField
import com.uzukip.gokapi.ui.components.pressScale
import com.uzukip.gokapi.ui.components.rememberCollapsingBar
import com.uzukip.gokapi.ui.components.segmentCorners
import com.uzukip.gokapi.ui.settings.GroupLabel
import com.uzukip.gokapi.ui.settings.SettingsRow
import com.uzukip.gokapi.ui.theme.DEFAULT_SEED
import com.uzukip.gokapi.ui.theme.PRESET_SEEDS
import com.uzukip.gokapi.ui.theme.parseSeedColor
import kotlinx.coroutines.launch

/** Server, appearance, theme color, upload defaults, and the tray: the Android app's settings without haptics or notifications. */
@Composable
internal fun SettingsScreen(container: DesktopContainer, keys: KeyActions, trayAvailable: Boolean, onServerSaved: () -> Unit, onBack: () -> Unit) {
    val settings by container.settings.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollBehavior = rememberCollapsingBar()
    val scroll = rememberScrollState()
    keys.handler = { key -> if (key == AppKey.ESCAPE) { onBack(); true } else false }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { GokapiTopBar(title = "Settings", onBack = onBack, scrollBehavior = scrollBehavior) },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
                    ServerSettings(container.credentials, container.client, onServerSaved)

                    GroupLabel("Appearance")
                    ConnectedChoices(
                        options = listOf(
                            Choice(ThemeMode.SYSTEM, "System", "theme-light-dark"),
                            Choice(ThemeMode.LIGHT, "Light", "white-balance-sunny"),
                            Choice(ThemeMode.DARK, "Dark", "moon-waning-crescent"),
                        ),
                        selected = settings.themeMode,
                        onSelect = { mode -> scope.launch { container.settings.update { it.copy(themeMode = mode) } } },
                    )

                    GroupLabel("Theme color")
                    ConnectedChoices(
                        options = listOf(
                            Choice(ColorSource.DEFAULT, "Default", "format-color-fill"),
                            Choice(ColorSource.USER, "Custom", "palette-outline"),
                        ),
                        selected = settings.colorSource,
                        onSelect = { source -> scope.launch { container.settings.update { it.copy(colorSource = source) } } },
                    )
                    AnimatedVisibility(
                        visible = settings.colorSource == ColorSource.USER,
                        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            SwatchGrid(
                                selectedHex = settings.userSeed ?: DEFAULT_SEED,
                                onPick = { hex -> scope.launch { container.settings.update { it.copy(userSeed = hex) } } },
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }

                    GroupLabel("Uploads")
                    SegmentedGroup {
                        MorphSurface(onClick = null, corners = segmentCorners(0, 2)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        "Default expiry",
                                        style = MaterialTheme.typography.labelLargeEmphasized,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                    ConnectedChoices(
                                        options = ExpiryChoices,
                                        selected = settings.uploadExpiryDays,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        onSelect = { days -> scope.launch { container.settings.update { it.copy(uploadExpiryDays = days) } } },
                                    )
                                }
                                var limit by remember(settings.uploadDownloads) { mutableStateOf(settings.uploadDownloads.toString()) }
                                val pending = limit.toIntOrNull()?.takeIf { it != settings.uploadDownloads }
                                ThemedTextField(
                                    value = limit,
                                    onValueChange = { limit = it.filter(Char::isDigit).take(9) },
                                    label = "Default allowed downloads",
                                    placeholder = "0",
                                    supportingText = "0 means unlimited",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    trailing = {
                                        AnimatedVisibility(
                                            visible = pending != null,
                                            enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                                            exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                                        ) {
                                            FilledIconButton(
                                                onClick = { pending?.let { value -> scope.launch { container.settings.update { it.copy(uploadDownloads = value) } } } },
                                                shapes = IconButtonDefaults.shapes(),
                                            ) { MciIcon("check", 22.dp, LocalContentColor.current, contentDescription = "Save download limit") }
                                        }
                                    },
                                )
                            }
                        }
                        SettingsRow(
                            corners = segmentCorners(1, 2),
                            icon = { IconBadge("content-copy", "#3C7CAB", 40.dp) },
                            title = "Copy upload link automatically",
                            subtitle = "When an upload finishes",
                            onClick = { scope.launch { container.settings.update { it.copy(autoCopyLink = !it.autoCopyLink) } } },
                            trailing = {
                                Switch(
                                    checked = settings.autoCopyLink,
                                    onCheckedChange = { value -> scope.launch { container.settings.update { it.copy(autoCopyLink = value) } } },
                                )
                            },
                        )
                    }

                    NotificationSettings(container, trayAvailable)

                    GroupLabel("Desktop")
                    SegmentedGroup {
                        SettingsRow(
                            corners = segmentCorners(0, 1),
                            icon = { IconBadge("tray-arrow-down", "#7B6A9E", 40.dp) },
                            title = "Keep running in the tray",
                            subtitle = if (trayAvailable) "Closing the window hides it to the tray. Quit from the tray menu."
                                else "No system tray found, so closing the window quits.",
                            onClick = { scope.launch { container.settings.update { it.copy(closeToTray = !it.closeToTray) } } },
                            trailing = {
                                Switch(
                                    checked = settings.closeToTray,
                                    onCheckedChange = { value -> scope.launch { container.settings.update { it.copy(closeToTray = value) } } },
                                )
                            },
                        )
                    }

                    Text(
                        "gokapi-desktop v$appVersion",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 32.dp),
                    )
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scroll),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp, horizontal = 2.dp),
            )
        }
    }
}

/** Seed swatches; the picked one morphs from a circle into a scalloped cookie. */
@Composable
private fun SwatchGrid(selectedHex: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val normalized = selectedHex.uppercase()
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PRESET_SEEDS.forEach { hex ->
            val isSelected = hex.uppercase() == normalized
            MorphingBadge(
                selected = isSelected,
                color = parseSeedColor(hex),
                size = 48.dp,
                modifier = Modifier
                    .pressScale(pressedScale = 0.88f, onClick = { onPick(hex) })
                    .semantics { contentDescription = "Seed color $hex" },
            ) {
                AnimatedVisibility(
                    visible = isSelected,
                    enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()),
                    exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}
