package com.resonance.music.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.resonance.music.data.api.models.AlbumItem
import com.resonance.music.ui.components.AlbumCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAlbumClick: (String) -> Unit,
    onSeeAllClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // Stable reference — avoids new lambda allocation on every recomposition
    val coverArtUrlBuilder = remember<(String) -> String?> { { viewModel.getCoverArtUrl(it) } }

    LaunchedEffect(Unit) {
        viewModel.loadHome()
    }

    val pullState = rememberPullToRefreshState()
    if (pullState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refresh()
            pullState.endRefresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Resonance") })
        }
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            uiState.error != null -> ErrorState(
                error = uiState.error,
                onRetry = { viewModel.loadHome() },
                modifier = Modifier.fillMaxSize().padding(padding)
            )

            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .nestedScroll(pullState.nestedScrollConnection)
            ) {
              LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                item(key = "greeting") {
                    Text(
                        text = greeting(),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
                    )
                }

                uiState.featured?.let { featured ->
                    item(key = "hero") {
                        HeroCard(
                            album = featured,
                            coverArtUrl = featured.coverArt?.let { viewModel.getCoverArtUrl(it, 600) },
                            onOpen = { onAlbumClick(featured.id) },
                            onPlay = { viewModel.playAlbum(featured.id) }
                        )
                    }
                }

                item(key = "shuffle") {
                    FilledTonalButton(
                        onClick = { viewModel.shuffleAll() },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Shuffle all")
                    }
                }

                albumShelf("recent", "Recently played", uiState.recentAlbums, onAlbumClick, onSeeAllClick, coverArtUrlBuilder)
                albumShelf("newest", "Newest additions", uiState.newestAlbums, onAlbumClick, onSeeAllClick, coverArtUrlBuilder)
                albumShelf("frequent", "Most played", uiState.frequentAlbums, onAlbumClick, onSeeAllClick, coverArtUrlBuilder)
                albumShelf("random", "Random picks", uiState.randomAlbums, onAlbumClick, onSeeAllClick, coverArtUrlBuilder)
              }
              PullToRefreshContainer(
                  state = pullState,
                  // The 1.2.x container draws its circle even at rest; fade it with the
                  // pull progress so it only appears while pulling or refreshing.
                  modifier = Modifier
                      .align(Alignment.TopCenter)
                      .graphicsLayer {
                          alpha = if (pullState.isRefreshing) 1f
                                  else pullState.progress.coerceIn(0f, 1f)
                      }
              )
            }
        }
    }
}

private fun LazyListScope.albumShelf(
    key: String,
    title: String,
    albums: List<AlbumItem>,
    onAlbumClick: (String) -> Unit,
    onSeeAll: () -> Unit,
    coverArtUrlBuilder: (String) -> String?
) {
    if (albums.isEmpty()) return
    item(key = key) {
        Column(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onSeeAll) { Text("See all") }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(albums, key = { it.id }) { album ->
                    AlbumCard(
                        album = album,
                        coverArtUrl = album.coverArt?.let { coverArtUrlBuilder(it) },
                        modifier = Modifier.width(160.dp),
                        onClick = { onAlbumClick(album.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    album: AlbumItem,
    coverArtUrl: String?,
    onOpen: () -> Unit,
    onPlay: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpen)
    ) {
        if (coverArtUrl != null) {
            AsyncImage(
                model = coverArtUrl,
                contentDescription = album.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        // Scrim so the text stays legible over the artwork
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .fillMaxWidth(0.78f)
        ) {
            Text(
                text = "JUMP BACK IN",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            album.artist?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        FloatingActionButton(
            onClick = onPlay,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(52.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun ErrorState(error: String?, onRetry: () -> Unit, modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Failed to load", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                error ?: "Unknown error",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

private fun greeting(): String = when (java.time.LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}
