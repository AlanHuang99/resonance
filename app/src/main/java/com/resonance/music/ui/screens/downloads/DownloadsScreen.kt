package com.resonance.music.ui.screens.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.resonance.music.data.download.*
import com.resonance.music.ui.components.LocalSongDownload

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit, viewModel: DownloadsViewModel = hiltViewModel()) {
    val tracks by viewModel.tracks.collectAsState()
    val sync by viewModel.syncFavorites.collectAsState()
    val download = LocalSongDownload.current
    var removing by remember { mutableStateOf<OfflineTrack?>(null) }
    removing?.let { track ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remove download?") },
            text = { Text("Delete the saved copy of ${track.record.song.title} from this device?") },
            confirmButton = { TextButton(onClick = { viewModel.remove(track); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } })
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Downloads") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                ListItem(headlineContent = { Text("Download favorite songs") },
                    supportingContent = { Text("Sync over Wi-Fi every 6 hours. Saved songs stay until you remove them.") },
                    trailingContent = { Switch(checked = sync, onCheckedChange = viewModel::setSyncFavorites) })
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val ready = tracks.any { it.status == OfflineStatus.READY }
                    Button(enabled = ready, onClick = { viewModel.play() }) { Icon(Icons.Default.PlayArrow, null); Text("Play all") }
                    OutlinedButton(enabled = ready, onClick = { viewModel.play(shuffle = true) }) { Icon(Icons.Default.Shuffle, null); Text("Shuffle") }
                }
                Text("${tracks.count { it.status == OfflineStatus.READY }} songs available offline", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                if (tracks.isEmpty()) Text("Download songs, albums, playlists, or artists to listen here without a connection.", Modifier.padding(16.dp))
            }
            items(tracks, key = { it.record.key }) { track ->
                val song = track.record.song
                Column {
                    ListItem(
                        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(when (track.status) {
                            OfflineStatus.READY -> listOfNotNull(song.artist, song.album).joinToString(" • ")
                            OfflineStatus.QUEUED -> "Queued; waiting for a connection"
                            OfflineStatus.DOWNLOADING -> if (track.total > 0) "Downloading ${(track.bytes * 100 / track.total).coerceIn(0, 100)}%" else "Downloading"
                            OfflineStatus.FAILED -> track.record.error ?: "Download failed"
                        }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { AsyncImage(model = song.coverArt?.let(viewModel::coverArt), contentDescription = null, modifier = Modifier.size(48.dp)) },
                        trailingContent = {
                            Row {
                                if (track.status == OfflineStatus.FAILED) IconButton(onClick = { download?.invoke(song) }) { Icon(Icons.Default.Refresh, "Retry ${song.title}") }
                                IconButton(onClick = { removing = track }) { Icon(Icons.Default.DeleteOutline, "Remove ${song.title}") }
                            }
                        },
                        modifier = Modifier.clickable(enabled = track.status == OfflineStatus.READY) { viewModel.play(song.id) }
                    )
                    if (track.status == OfflineStatus.DOWNLOADING) {
                        if (track.total > 0) LinearProgressIndicator(progress = { (track.bytes.toFloat() / track.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
