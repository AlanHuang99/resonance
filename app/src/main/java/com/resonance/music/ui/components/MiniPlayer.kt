package com.resonance.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.resonance.music.playback.NowPlaying
import com.resonance.music.ui.components.MarqueeText
import kotlinx.coroutines.flow.StateFlow

@Composable
fun MiniPlayer(
    nowPlaying: NowPlaying,
    positionFlow: StateFlow<Long>,
    coverArtUrl: String?,
    onPlayerClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit
) {
    val song = nowPlaying.song ?: return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlayerClick),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp
    ) {
        Column {
            MiniPlayerProgress(positionFlow = positionFlow, duration = nowPlaying.duration)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (coverArtUrl != null) {
                        AsyncImage(
                            model = coverArtUrl,
                            contentDescription = "Album art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Song info
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    MarqueeText(
                        text = song.title,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = song.artist ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Controls
                IconButton(onClick = onPlayPauseClick) {
                    Icon(
                        imageVector = if (nowPlaying.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (nowPlaying.isPlaying) "Pause" else "Play"
                    )
                }

                IconButton(onClick = onNextClick) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next")
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerProgress(positionFlow: StateFlow<Long>, duration: Long) {
    val position by positionFlow.collectAsStateWithLifecycle()
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(2.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
    )
}
