package com.resonance.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.resonance.music.data.api.models.SongItem

@Composable
fun SongListItem(
    song: SongItem,
    trackNumber: Int? = null,
    onClick: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null,
    onGoToArtist: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                text = listOfNotNull(song.artist, song.duration?.let { formatSongDuration(it) })
                    .joinToString(" \u2022 "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            if (trackNumber != null) {
                Text(
                    text = trackNumber.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(24.dp)
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Play next") },
                        onClick = {
                            showMenu = false
                            onPlayNext?.invoke() ?: onClick()
                        },
                        leadingIcon = { Icon(Icons.Default.QueuePlayNext, contentDescription = null) }
                    )
                    if (onGoToAlbum != null) {
                        DropdownMenuItem(
                            text = { Text("Go to album") },
                            onClick = {
                                showMenu = false
                                onGoToAlbum()
                            },
                            leadingIcon = { Icon(Icons.Default.Album, contentDescription = null) }
                        )
                    }
                    if (onGoToArtist != null) {
                        DropdownMenuItem(
                            text = { Text("Go to artist") },
                            onClick = {
                                showMenu = false
                                onGoToArtist()
                            },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                        )
                    }
                    if (onDownload != null) {
                        DropdownMenuItem(
                            text = { Text("Download") },
                            onClick = {
                                showMenu = false
                                onDownload()
                            },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                        )
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun formatSongDuration(seconds: Int): String {
    val min = seconds / 60
    val sec = seconds % 60
    return "%d:%02d".format(min, sec)
}
