package com.resonance.music.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.resonance.music.data.repository.AuthRepository
import com.resonance.music.playback.PlaybackManager
import com.resonance.music.ui.components.MiniPlayer
import com.resonance.music.ui.screens.album.AlbumScreen
import com.resonance.music.ui.screens.artist.ArtistScreen
import com.resonance.music.ui.screens.home.HomeScreen
import com.resonance.music.ui.screens.library.LibraryScreen
import com.resonance.music.ui.screens.login.LoginScreen
import com.resonance.music.ui.screens.player.PlayerScreen
import com.resonance.music.ui.screens.search.SearchScreen

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val PLAYER = "player"
    const val ALBUM = "album/{albumId}"
    const val ARTIST = "artist/{artistId}"

    fun album(id: String) = "album/$id"
    fun artist(id: String) = "artist/$id"
}

@Composable
fun ResonanceNavHost(
    authRepository: AuthRepository = hiltViewModel<NavViewModel>().authRepository,
    playbackManager: PlaybackManager = hiltViewModel<NavViewModel>().playbackManager
) {
    val navController = rememberNavController()
    val isLoggedIn by authRepository.isLoggedIn.collectAsState(initial = false)
    val nowPlaying by playbackManager.nowPlaying.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(Routes.HOME, Routes.LIBRARY)
    val showMiniPlayer = nowPlaying.song != null && currentRoute != Routes.PLAYER

    val startDestination = if (isLoggedIn) Routes.HOME else Routes.LOGIN

    Scaffold(
        bottomBar = {
            Column {
                if (showMiniPlayer) {
                    val coverArtUrl = nowPlaying.song?.coverArt?.let {
                        playbackManager.nowPlaying.value.song?.coverArt
                    }
                    MiniPlayer(
                        nowPlaying = nowPlaying,
                        coverArtUrl = coverArtUrl,
                        onPlayerClick = { navController.navigate(Routes.PLAYER) },
                        onPlayPauseClick = { playbackManager.togglePlayPause() },
                        onNextClick = { playbackManager.next() }
                    )
                }

                if (showBottomBar) {
                    NavigationBar {
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home") },
                            selected = currentRoute == Routes.HOME,
                            onClick = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.HOME) { inclusive = true }
                                }
                            }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                            label = { Text("Library") },
                            selected = currentRoute == Routes.LIBRARY,
                            onClick = {
                                navController.navigate(Routes.LIBRARY) {
                                    popUpTo(Routes.HOME)
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    onAlbumClick = { navController.navigate(Routes.album(it)) },
                    onSearchClick = { navController.navigate(Routes.SEARCH) },
                    onSettingsClick = { /* TODO: settings */ }
                )
            }

            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onArtistClick = { navController.navigate(Routes.artist(it)) },
                    onAlbumClick = { navController.navigate(Routes.album(it)) },
                    onPlaylistClick = { /* TODO */ }
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    onBackClick = { navController.popBackStack() },
                    onArtistClick = { navController.navigate(Routes.artist(it)) },
                    onAlbumClick = { navController.navigate(Routes.album(it)) }
                )
            }

            composable(Routes.PLAYER) {
                PlayerScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.ALBUM,
                arguments = listOf(navArgument("albumId") { type = NavType.StringType })
            ) { backStackEntry ->
                AlbumScreen(
                    albumId = backStackEntry.arguments?.getString("albumId") ?: "",
                    onBackClick = { navController.popBackStack() },
                    onArtistClick = { navController.navigate(Routes.artist(it)) }
                )
            }

            composable(
                route = Routes.ARTIST,
                arguments = listOf(navArgument("artistId") { type = NavType.StringType })
            ) { backStackEntry ->
                ArtistScreen(
                    artistId = backStackEntry.arguments?.getString("artistId") ?: "",
                    onBackClick = { navController.popBackStack() },
                    onAlbumClick = { navController.navigate(Routes.album(it)) }
                )
            }
        }
    }
}
