package com.resonance.music.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.resonance.music.data.repository.AuthRepository
import com.resonance.music.ui.screens.TabataScreen
import com.resonance.music.ui.screens.login.LoginScreen
import com.resonance.music.ui.screens.settings.SettingsScreen

object Routes {
    const val LOGIN = "login"
    const val TABATA = "tabata"
    const val SETTINGS = "settings"
}

@Composable
fun ResonanceNavHost(
    authRepository: AuthRepository = hiltViewModel<NavViewModel>().authRepository
) {
    val navController = rememberNavController()
    val isLoggedIn by authRepository.isLoggedIn.collectAsState(initial = null)

    val loggedIn = isLoggedIn
    if (loggedIn == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val startDestination = if (loggedIn) Routes.TABATA else Routes.LOGIN

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.TABATA) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.TABATA) {
            TabataScreen()
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
