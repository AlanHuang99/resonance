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
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp) // Room for mini player
            ) {
                // Recently Played
                if (uiState.recentAlbums.isNotEmpty()) {
                    item {
                        AlbumSection(
                            title = "Recently Played",
                            albums = uiState.recentAlbums,
                            onAlbumClick = onAlbumClick
                        )
                    }
                }

                // Newest Additions
                if (uiState.newestAlbums.isNotEmpty()) {
                    item {
                        AlbumSection(
                            title = "Newest Additions",
                            albums = uiState.newestAlbums,
                            onAlbumClick = onAlbumClick
                        )
                    }
                }

                // Most Played
                if (uiState.frequentAlbums.isNotEmpty()) {
                    item {
                        AlbumSection(
                            title = "Most Played",
                            albums = uiState.frequentAlbums,
                            onAlbumClick = onAlbumClick
                        )
                    }
                }

                // Random picks
                if (uiState.randomAlbums.isNotEmpty()) {
                    item {
                        AlbumSection(
                            title = "Random Picks",
                            albums = uiState.randomAlbums,
                            onAlbumClick = onAlbumClick
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
    onAlbumClick: (String) -> Unit
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
            items(albums) { album ->
                AlbumCard(
                    album = album,
                    onClick = { onAlbumClick(album.id) }
                )
            }
        }
    }
}
