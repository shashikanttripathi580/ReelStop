package com.reelstop.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.reelstop.ui.dashboard.DashboardScreen
import com.reelstop.ui.onboarding.OnboardingScreen
import com.reelstop.ui.privacy.PrivacyScreen
import com.reelstop.ui.settings.SettingsScreen
import com.reelstop.ui.theme.ReelStopTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ReelStopTheme {
                ReelStopAppNavigation(
                    mainViewModel = mainViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.checkPermissions(this)
    }
}

@Composable
fun ReelStopAppNavigation(
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()
    val settings by mainViewModel.settings.collectAsState()

    // Route dynamically based on onboarding state
    LaunchedEffect(settings) {
        val onboardingCompleted = settings?.onboardingCompleted ?: false
        if (!onboardingCompleted) {
            navController.navigate(Screen.Onboarding.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                viewModel = mainViewModel,
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToPrivacy = { navController.navigate(Screen.Privacy.route) }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPrivacy = { navController.navigate(Screen.Privacy.route) }
            )
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                viewModel = mainViewModel,
                onFinishOnboarding = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Privacy.route) {
            PrivacyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
