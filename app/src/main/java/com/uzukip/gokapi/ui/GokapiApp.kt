package com.uzukip.gokapi.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.uzukip.gokapi.AppContainer
import com.uzukip.gokapi.ui.home.HomeScreen
import com.uzukip.gokapi.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
}

/**
 * M3 Expressive navigation motion (same as spendr-kt): a shared-axis X
 * transition driven by the theme's expressive springs. The incoming screen
 * slides a quarter width and fades in; the covered screen drifts back and
 * dims. Pops play the same choreography in reverse, which also drives the
 * predictive-back preview.
 */
private const val SLIDE_DIVISOR = 4
private const val DRIFT_DIVISOR = 10

@Composable
fun GokapiApp(container: AppContainer, incomingIntent: Intent? = null, consumeIntent: () -> Unit = {}) {
    val navController = rememberNavController()
    LaunchedEffect(incomingIntent) { if (incomingIntent != null) navController.popBackStack(Routes.HOME, false) }
    val motion = MaterialTheme.motionScheme
    val spatial = motion.defaultSpatialSpec<IntOffset>()
    val fastEffects = motion.fastEffectsSpec<Float>()
    val effects = motion.defaultEffectsSpec<Float>()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        // Opaque theme surface behind every screen: while two screens
        // cross-fade, this shows through instead of the window's splash color.
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        enterTransition = { slideInHorizontally(spatial) { it / SLIDE_DIVISOR } + fadeIn(effects) },
        exitTransition = { slideOutHorizontally(spatial) { -it / DRIFT_DIVISOR } + fadeOut(fastEffects) },
        popEnterTransition = { slideInHorizontally(spatial) { -it / DRIFT_DIVISOR } + fadeIn(effects) },
        popExitTransition = {
            slideOutHorizontally(spatial) { it / SLIDE_DIVISOR } +
                scaleOut(motion.defaultSpatialSpec(), targetScale = 0.94f) + fadeOut(fastEffects)
        },
    ) {
        composable(Routes.HOME) {
            HomeScreen(container = container, incomingIntent = incomingIntent, consumeIntent = consumeIntent, onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(container = container, onBack = { navController.popBackStack() })
        }
    }
}
