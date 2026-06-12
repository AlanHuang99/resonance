package com.resonance.music.ui.screens.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.resonance.music.ui.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    albumId: String,
    onBackClick: () -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: ((String) -> Unit)? = null,
    viewModel: AlbumViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(albumId) {
        viewModel.loadAlbum(albumId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.albumName) },
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
            val multiDisc = remember(uiState.songs) {
                uiState.songs.mapNotNull { it.discNumber }.distinct().size > 1
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Album header
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.coverArtUrl != null) {
                                AsyncImage(
                                    model = uiState.coverArtUrl,
                                    contentDescription = "Album art",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.Album,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = uiState.albumName,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = uiState.artistName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (uiState.year != null || uiState.genre != null) {
                            Text(
                                text = listOfNotNull(uiState.year?.toString(), uiState.genre)
                                    .joinToString(" \u2022 "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Play / Shuffle buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { viewModel.playAll(shuffle = false) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Play")
                            }
                            OutlinedButton(onClick = { viewModel.playAll(shuffle = true) }) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Shuffle")
                            }
                        }
                    }
                }

                // Song list
                itemsIndexed(uiState.songs, key = { _, song -> song.id }) { index, song ->
                    Column {
                        if (multiDisc && (index == 0 || uiState.songs[index - 1].discNumber != song.discNumber)) {
                            Text(
                                text = "Disc ${song.discNumber ?: 1}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                            )
                        }
                        SongListItem(
                            song = song,
                            trackNumber = song.track,
                            onClick = { viewModel.playSongAt(index) },
                            onGoToArtist = song.artistId?.let { id -> { onArtistClick(id) } },
                            onGoToAlbum = song.albumId?.let { id -> onAlbumClick?.let { nav -> { nav(id) } } }
                        )
                    }
                }
            }
        }
    }
}
