package com.resonance.music.ui.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.resonance.music.data.api.models.ArtistItem
import com.resonance.music.playback.RepeatMode
import com.resonance.music.ui.components.AlbumListItem
import com.resonance.music.ui.components.SongActions
import com.resonance.music.ui.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    artistId: String,
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    viewModel: ArtistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(0) } // 0 = Albums, 1 = Tracks

    LaunchedEffect(artistId) {
        viewModel.loadArtist(artistId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.artistName) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            imageVector = if (uiState.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (uiState.isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (uiState.isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
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
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                // Artist header
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.imageUrl != null) {
                                AsyncImage(
                                    model = uiState.imageUrl,
                                    contentDescription = "Artist image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = uiState.artistName,
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Text(
                            text = "${uiState.albumCount} ${if (uiState.albumCount == 1) "album" else "albums"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.playAll(shuffle = false) },
                                enabled = uiState.tracks.isNotEmpty()
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp)); Text("Play")
                            }
                            OutlinedButton(
                                onClick = { viewModel.playAll(shuffle = true) },
                                enabled = uiState.tracks.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp)); Text("Shuffle")
                            }
                            // Loop toggle reflects + drives the global repeat mode.
                            FilledIconToggleButton(
                                checked = repeatMode != RepeatMode.OFF,
                                onCheckedChange = { viewModel.toggleRepeat() }
                            ) {
                                Icon(
                                    if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                    contentDescription = "Loop"
                                )
                            }
                        }
                    }
                }

                uiState.biography?.let { bio ->
                    item { ArtistBio(bio) }
                }

                if (uiState.similarArtists.isNotEmpty()) {
                    item {
                        SimilarArtists(
                            artists = uiState.similarArtists,
                            coverArtUrlBuilder = uiState.coverArtUrlBuilder,
                            onArtistClick = onArtistClick
                        )
                    }
                }

                // Albums | Tracks
                item {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Albums") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Tracks") }
                        )
                    }
                }

                if (selectedTab == 0) {
                    items(uiState.albums, key = { it.id }) { album ->
                        AlbumListItem(
                            album = album,
                            coverArtUrl = album.coverArt?.let { uiState.coverArtUrlBuilder?.invoke(it) },
                            onClick = { onAlbumClick(album.id) }
                        )
                    }
                } else {
                    if (uiState.tracksLoading && uiState.tracks.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    itemsIndexed(uiState.tracks, key = { index, s -> "${s.id}_$index" }) { index, song ->
                        SongListItem(
                            song = song,
                            onClick = { viewModel.playTrackAt(index) },
                            actions = SongActions(
                                onPlayNext = { viewModel.playNext(song) },
                                onAddToQueue = { viewModel.addToQueue(song) },
                                onGoToAlbum = song.albumId?.let { id -> { onAlbumClick(id) } }
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistBio(bio: String) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        text = bio,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (expanded) Int.MAX_VALUE else 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SimilarArtists(
    artists: List<ArtistItem>,
    coverArtUrlBuilder: ((String) -> String?)?,
    onArtistClick: (String) -> Unit
) {
    Column {
        Text(
            text = "Similar artists",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(artists, key = { it.id }) { artist ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(96.dp)
                        .clickable { onArtistClick(artist.id) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        val url = artist.coverArt?.let { coverArtUrlBuilder?.invoke(it) }
                        if (url != null) {
                            AsyncImage(
                                model = url,
                                contentDescription = artist.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
