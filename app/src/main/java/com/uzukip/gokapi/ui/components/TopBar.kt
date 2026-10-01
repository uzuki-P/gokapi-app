package com.uzukip.gokapi.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow

/** Back arrow with the M3E icon-button press morph. */
@Composable
fun BackButton(onClick: () -> Unit) {
    BouncyIconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
}

/**
 * Screen app bar. With a [scrollBehavior] it is the M3 Expressive medium
 * flexible bar: a large rounded title that collapses into the bar as the
 * content scrolls (wire `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)`
 * on the Scaffold). Without one it is a plain small bar for fixed layouts.
 */
@Composable
fun GokapiTopBar(
    title: String,
    onBack: (() -> Unit)?,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
    if (scrollBehavior != null) {
        MediumFlexibleTopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            subtitle = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            navigationIcon = { if (onBack != null) BackButton(onBack) },
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior,
        )
    } else {
        val titleSlot: @Composable () -> Unit = {
            Text(
                title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val navSlot: @Composable () -> Unit = { if (onBack != null) BackButton(onBack) }
        if (subtitle != null) {
            TopAppBar(
                title = titleSlot,
                subtitle = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = navSlot,
                actions = actions,
                colors = colors,
            )
        } else {
            TopAppBar(title = titleSlot, navigationIcon = navSlot, actions = actions, colors = colors)
        }
    }
}

/** Collapsing behavior for [GokapiTopBar]. */
@Composable
fun rememberCollapsingBar(): TopAppBarScrollBehavior =
    TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
