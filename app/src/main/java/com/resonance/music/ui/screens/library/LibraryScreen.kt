package com.resonance.music.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.resonance.music.ui.components.AlbumCard
import com.resonance.music.ui.components.AlbumListItem
import com.resonance.music.ui.components.SongListItem

enum class LibraryTab { Artists, Albums, Genres, Playlists, Favorites }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableStateOf(LibraryTab.Artists) }
    val uiState by viewModel.uiState.collectAsState()

    // Load data when tab changes
    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            LibraryTab.Artists -> viewModel.loadArtists()
            LibraryTab.Albums -> viewModel.loadAlbums()
            LibraryTab.Genres -> viewModel.loadGenres()
            LibraryTab.Playlists -> viewModel.loadPlaylists()
            LibraryTab.Favorites -> viewModel.loadFavorites()
        }
    }

    val pullState = rememberPullToRefreshState()
    if (pullState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refresh(selectedTab)
            pullState.endRefresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Library") })
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab.ordinal, edgePadding = 0.dp) {
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
                Box(modifier = Modifier.fillMaxSize().nestedScroll(pullState.nestedScrollConnection)) {
                    when (selectedTab) {
                        LibraryTab.Artists -> ArtistsTab(uiState, onArtistClick)
                        LibraryTab.Albums -> AlbumsTab(uiState, onAlbumClick, viewModel::setAlbumSort)
                        LibraryTab.Genres -> GenresTab(uiState, onGenreClick)
                        LibraryTab.Playlists -> PlaylistsTab(uiState, onPlaylistClick)
                        LibraryTab.Favorites -> FavoritesTab(uiState, onAlbumClick, onArtistClick, viewModel)
                    }
                    PullToRefreshContainer(
                        state = pullState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
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
        LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
            items(uiState.artists, key = { it.id }, contentType = { "artist" }) { artist ->
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
private fun AlbumsTab(
    uiState: LibraryUiState,
    onAlbumClick: (String) -> Unit,
    onSortChange: (AlbumSort) -> Unit
) {
    Column {
        var menuOpen by remember { mutableStateOf(false) }
        Box(modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
            TextButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(uiState.albumSort.label)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                AlbumSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(sort.label) },
                        onClick = {
                            menuOpen = false
                            onSortChange(sort)
                        },
                        trailingIcon = {
                            if (sort == uiState.albumSort) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                }
            }
        }
        if (uiState.albums.isEmpty()) {
            EmptyState(Icons.Default.Album, "Albums", "No albums found")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uiState.albums, key = { it.id }) { album ->
                    AlbumCard(
                        album = album,
                        coverArtUrl = album.coverArt?.let { uiState.coverArtUrlBuilder?.invoke(it) },
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onAlbumClick(album.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GenresTab(uiState: LibraryUiState, onGenreClick: (String) -> Unit) {
    if (uiState.genres.isEmpty()) {
        EmptyState(Icons.Default.Category, "Genres", "No genres found")
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
            items(uiState.genres, key = { it.name }, contentType = { "genre" }) { genre ->
                ListItem(
                    headlineContent = { Text(genre.name) },
                    supportingContent = { genre.albumCount?.let { Text("$it albums") } },
                    leadingContent = { Icon(Icons.Default.Category, contentDescription = null) },
                    modifier = Modifier.clickable { onGenreClick(genre.name) }
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
        LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
            items(uiState.playlists, key = { it.id }, contentType = { "playlist" }) { playlist ->
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
        LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
            if (uiState.starredArtists.isNotEmpty()) {
                item {
                    Text(
                        "Artists",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(uiState.starredArtists, key = { it.id }, contentType = { "artist" }) { artist ->
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
                items(uiState.starredAlbums, key = { it.id }, contentType = { "album" }) { album ->
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
                items(uiState.starredSongs, key = { it.id }, contentType = { "song" }) { song ->
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
