package com.resonance.music.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.resonance.music.data.api.models.AlbumItem
import com.resonance.music.ui.components.AlbumCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAlbumClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // Stable reference — avoids new lambda allocation on every recomposition
    val coverArtUrlBuilder = remember<(String) -> String?> { { viewModel.getCoverArtUrl(it) } }

    LaunchedEffect(Unit) {
        viewModel.loadHome()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resonance") },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Failed to load", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        uiState.error ?: "Unknown error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FilledTonalButton(onClick = { viewModel.loadHome() }) {
                        Text("Retry")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp) // Room for mini player
            ) {
                // Recently Played
                if (uiState.recentAlbums.isNotEmpty()) {
                    item(key = "recent") {
                        AlbumSection(
                            title = "Recently Played",
                            albums = uiState.recentAlbums,
                            onAlbumClick = onAlbumClick,
                            coverArtUrlBuilder = coverArtUrlBuilder
                        )
                    }
                }

                // Newest Additions
                if (uiState.newestAlbums.isNotEmpty()) {
                    item(key = "newest") {
                        AlbumSection(
                            title = "Newest Additions",
                            albums = uiState.newestAlbums,
                            onAlbumClick = onAlbumClick,
                            coverArtUrlBuilder = coverArtUrlBuilder
                        )
                    }
                }

                // Most Played
                if (uiState.frequentAlbums.isNotEmpty()) {
                    item(key = "frequent") {
                        AlbumSection(
                            title = "Most Played",
                            albums = uiState.frequentAlbums,
                            onAlbumClick = onAlbumClick,
                            coverArtUrlBuilder = coverArtUrlBuilder
                        )
                    }
                }

                // Random picks
                if (uiState.randomAlbums.isNotEmpty()) {
                    item(key = "random") {
                        AlbumSection(
                            title = "Random Picks",
                            albums = uiState.randomAlbums,
                            onAlbumClick = onAlbumClick,
                            coverArtUrlBuilder = coverArtUrlBuilder
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumSection(
    title: String,
    albums: List<AlbumItem>,
    onAlbumClick: (String) -> Unit,
    coverArtUrlBuilder: (String) -> String?
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(albums, key = { it.id }) { album ->
                AlbumCard(
                    album = album,
                    coverArtUrl = album.coverArt?.let { coverArtUrlBuilder(it) },
                    onClick = { onAlbumClick(album.id) }
                )
            }
        }
    }
}
