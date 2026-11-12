package com.resonance.music.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import com.resonance.music.playback.RepeatMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.resonance.music.ui.components.MarqueeText
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBackClick: () -> Unit,
    onLyricsClick: () -> Unit = {},
    onQueueClick: () -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val castDevice by viewModel.castSession.collectAsStateWithLifecycle()
    var castSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Swipe down to minimize back to the mini player
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    detectVerticalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onVerticalDrag = { _, dy -> totalDrag += dy },
                        onDragEnd = { if (totalDrag > 250f) onBackClick() }
                    )
                }
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Album art
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    // Swipe the art left/right to change tracks
                    .pointerInput(Unit) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { _, dx -> totalDrag += dx },
                            onDragEnd = {
                                if (totalDrag < -120f) viewModel.next()
                                else if (totalDrag > 120f) viewModel.previous()
                            }
                        )
                    },
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
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Song info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                MarqueeText(
                    text = uiState.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                MarqueeText(
                    text = uiState.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().then(
                        uiState.artistId?.let { id -> Modifier.clickable { onArtistClick(id) } } ?: Modifier
                    )
                )
                MarqueeText(
                    text = uiState.album,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().then(
                        uiState.albumId?.let { id -> Modifier.clickable { onAlbumClick(id) } } ?: Modifier
                    )
                )
            }

            // Progress slider (collects position in its own scope so ticks
            // don't recompose the art, marquee text, or controls)
            PlayerProgress(
                positionFlow = viewModel.position,
                duration = uiState.duration,
                onSeek = viewModel::onSeek
            )

            // Playback controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = viewModel::toggleShuffle) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (uiState.shuffleEnabled) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = viewModel::previous) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(36.dp)
                    )
                }

                FilledIconButton(
                    onClick = viewModel::togglePlayPause,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(onClick = viewModel::next) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = viewModel::toggleRepeat) {
                    Icon(
                        imageVector = when (uiState.repeatMode) {
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (uiState.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Favorite / queue row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        imageVector = if (uiState.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (uiState.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onLyricsClick) {
                    Icon(Icons.Default.Lyrics, contentDescription = "Lyrics")
                }
                IconButton(onClick = onQueueClick) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue")
                }
                IconButton(onClick = { castSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.Cast,
                        contentDescription = if (castDevice != null) "Casting to ${castDevice?.name}" else "Cast",
                        tint = if (castDevice != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (castSheet) {
        CastSheet(
            viewModel = viewModel,
            onDismiss = { castSheet = false }
        )
    }
}

@Composable
private fun PlayerProgress(
    positionFlow: StateFlow<Long>,
    duration: Long,
    onSeek: (Float) -> Unit
) {
    val position by positionFlow.collectAsStateWithLifecycle()
    // While dragging, the thumb follows the finger and the player is left alone; the seek
    // is sent once on release. Seeking on every drag frame restarted the stream each time.
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = dragProgress
                ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            onValueChange = { dragProgress = it },
            onValueChangeFinished = {
                dragProgress?.let(onSeek)
                dragProgress = null
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatDuration(dragProgress?.let { (it * duration).toLong() } ?: position),
                style = MaterialTheme.typography.labelSmall
            )
            Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/** Speaker picker. Same flow as any cast UI: scan, pick, or stop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CastSheet(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val devices by viewModel.castDevices.collectAsStateWithLifecycle()
    val session by viewModel.castSession.collectAsStateWithLifecycle()
    val busy by viewModel.castBusy.collectAsStateWithLifecycle()
    val error by viewModel.castError.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { if (devices.isEmpty()) viewModel.scanForCastDevices() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cast to", style = MaterialTheme.typography.titleMedium)
                if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else TextButton(onClick = { viewModel.scanForCastDevices() }) { Text("Rescan") }
            }

            session?.let { device ->
                ListItem(
                    headlineContent = { Text(device.name) },
                    supportingContent = { Text("Casting") },
                    trailingContent = {
                        TextButton(onClick = {
                            viewModel.stopCasting()
                            onDismiss()
                        }) { Text("Stop") }
                    }
                )
            }

            if (devices.isEmpty() && !busy) {
                Text(
                    error ?: "No speakers found on this network",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            devices.filter { it.id != session?.id }.forEach { device ->
                ListItem(
                    headlineContent = { Text(device.name) },
                    modifier = Modifier.clickable {
                        viewModel.castTo(device)
                        onDismiss()
                    }
                )
            }

            error?.takeIf { devices.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
