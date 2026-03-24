package com.resonance.music.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.resonance.music.ui.components.AlbumListItem
import com.resonance.music.ui.components.SongListItem

enum class LibraryTab { Artists, Albums, Playlists, Favorites }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableStateOf(LibraryTab.Artists) }
    val uiState by viewModel.uiState.collectAsState()

    // Load data when tab changes
    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            LibraryTab.Artists -> viewModel.loadArtists()
            LibraryTab.Albums -> viewModel.loadAlbums()
            LibraryTab.Playlists -> viewModel.loadPlaylists()
            LibraryTab.Favorites -> viewModel.loadFavorites()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Library") })
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                LibraryTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.name) }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                when (selectedTab) {
                    LibraryTab.Artists -> ArtistsTab(uiState, onArtistClick)
                    LibraryTab.Albums -> AlbumsTab(uiState, onAlbumClick)
                    LibraryTab.Playlists -> PlaylistsTab(uiState, onPlaylistClick)
                    LibraryTab.Favorites -> FavoritesTab(uiState, onAlbumClick, onArtistClick, viewModel)
                }
            }
        }
    }
}

@Composable
private fun ArtistsTab(uiState: LibraryUiState, onArtistClick: (String) -> Unit) {
    if (uiState.artists.isEmpty()) {
        EmptyState(Icons.Default.Person, "Artists", "No artists found")
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
            items(uiState.artists, key = { it.id }) { artist ->
                ListItem(
                    headlineContent = {
                        Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        artist.albumCount?.let { Text("$it albums") }
                    },
                    leadingContent = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onArtistClick(artist.id) }
                )
            }
        }
    }
}

@Composable
private fun AlbumsTab(uiState: LibraryUiState, onAlbumClick: (String) -> Unit) {
    if (uiState.albums.isEmpty()) {
        EmptyState(Icons.Default.Album, "Albums", "No albums found")
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
            items(uiState.albums, key = { it.id }) { album ->
                AlbumListItem(
                    album = album,
                    coverArtUrl = album.coverArt?.let { uiState.coverArtUrlBuilder?.invoke(it) },
                    onClick = { onAlbumClick(album.id) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistsTab(uiState: LibraryUiState, onPlaylistClick: (String) -> Unit) {
    if (uiState.playlists.isEmpty()) {
        EmptyState(Icons.AutoMirrored.Filled.QueueMusic, "Playlists", "No playlists found")
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
            items(uiState.playlists, key = { it.id }) { playlist ->
                ListItem(
                    headlineContent = {
                        Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        val details = listOfNotNull(
                            playlist.songCount?.let { "$it songs" },
                            playlist.owner
                        ).joinToString(" \u2022 ")
                        if (details.isNotEmpty()) Text(details)
                    },
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onPlaylistClick(playlist.id) }
                )
            }
        }
    }
}

@Composable
private fun FavoritesTab(
    uiState: LibraryUiState,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    viewModel: LibraryViewModel
) {
    val hasContent = uiState.starredArtists.isNotEmpty() ||
            uiState.starredAlbums.isNotEmpty() ||
            uiState.starredSongs.isNotEmpty()

    if (!hasContent) {
        EmptyState(Icons.Default.Favorite, "Favorites", "No favorites yet")
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
            if (uiState.starredArtists.isNotEmpty()) {
                item {
                    Text(
                        "Artists",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(uiState.starredArtists, key = { it.id }) { artist ->
                    ListItem(
                        headlineContent = { Text(artist.name) },
                        leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.clickable { onArtistClick(artist.id) }
                    )
                }
            }

            if (uiState.starredAlbums.isNotEmpty()) {
                item {
                    Text(
                        "Albums",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(uiState.starredAlbums, key = { it.id }) { album ->
                    AlbumListItem(
                        album = album,
                        coverArtUrl = album.coverArt?.let { uiState.coverArtUrlBuilder?.invoke(it) },
                        onClick = { onAlbumClick(album.id) }
                    )
                }
            }

            if (uiState.starredSongs.isNotEmpty()) {
                item {
                    Text(
                        "Songs",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(uiState.starredSongs, key = { it.id }) { song ->
                    SongListItem(
                        song = song,
                        onClick = { viewModel.playStarredSong(song) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
