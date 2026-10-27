package com.resonance.music.playback

import android.os.Bundle
import android.os.Process
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.*
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.resonance.music.R
import com.resonance.music.data.download.OfflineLibrary
import com.resonance.music.data.download.OfflineStatus
import com.resonance.music.data.download.SongDownloader
import com.resonance.music.data.repository.MusicRepository
import com.resonance.music.data.repository.AuthRepository
import kotlinx.coroutines.*
import javax.inject.Inject

@androidx.annotation.OptIn(UnstableApi::class)
class LibrarySessionCallback @Inject constructor(
    private val library: CarLibrary,
    private val music: MusicRepository,
    private val items: MediaItems,
    private val offline: OfflineLibrary,
    private val downloader: SongDownloader,
    private val auth: AuthRepository,
    private val access: ControllerAccess
) : MediaLibrarySession.Callback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var favoriteBusy = false

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        if (!access.allowed(controller)) return MediaSession.ConnectionResult.reject()
        val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            .add(SessionCommand(FAVORITE, Bundle.EMPTY)).add(SessionCommand(REPEAT, Bundle.EMPTY)).build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(commands).build()
    }

    override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> = future {
        LibraryResult.ofItem(library.root(), LibraryParams.Builder().setExtras(Bundle().apply {
            putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
        }).build())
    }

    override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
        if (!library.signedIn()) LibraryResult.ofError(LibraryResult.RESULT_ERROR_SESSION_AUTHENTICATION_EXPIRED)
        else try {
            LibraryResult.ofItemList(libraryPage(withContext(Dispatchers.IO) { library.children(parentId) }, page, pageSize), params)
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { LibraryResult.ofError(LibraryResult.RESULT_ERROR_IO) }
    }

    override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> = future {
        if (mediaId == CarLibrary.ROOT) LibraryResult.ofItem(library.root(), null)
        else if (mediaId in listOf("downloads", "artists", "playlists", "favorites") || mediaId.contains(':')) {
            LibraryResult.ofItem(library.folder(mediaId, mediaId), null)
        } else {
            val result = withContext(Dispatchers.IO) { offline.refresh(); library.resolve(MediaItem.Builder().setMediaId(mediaId).build(), false) }.firstOrNull()
            if (result == null) LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            else LibraryResult.ofItem(result.buildUpon().setMediaId(mediaId).build(), null)
        }
    }

    override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> = future {
        val generation = auth.generation.value
        val result = withContext(Dispatchers.IO) { library.search(query) }
        if (auth.generation.value != generation) throw CancellationException("Account changed")
        session.notifySearchResultChanged(browser, query, result.size, params)
        LibraryResult.ofVoid(params)
    }

    override fun onGetSearchResult(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
        LibraryResult.ofItemList(libraryPage(withContext(Dispatchers.IO) { library.search(query) }, page, pageSize), params)
    }

    override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> = future {
        withContext(Dispatchers.IO) {
            offline.refresh()
            mediaItems.flatMap { library.resolve(it, controller.uid == Process.myUid()) }.toMutableList()
        }
    }

    override fun onSetMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
        val resolved = withContext(Dispatchers.IO) {
            offline.refresh()
            mediaItems.flatMap { library.resolve(it, controller.uid == Process.myUid()) }
        }
        var queue = resolved
        var index = if (startIndex == C.INDEX_UNSET) 0 else startIndex.coerceIn(0, (resolved.size - 1).coerceAtLeast(0))
        val request = mediaItems.singleOrNull()
        if (request != null && (CarLibrary.selection(request.mediaId) != null || !MediaItems.hasSong(request)) && resolved.size == 1) {
            val selection = CarLibrary.selection(request.mediaId)
            val browsedQueue = selection?.let {
                try {
                    withContext(Dispatchers.IO) {
                        library.children(it.parent).filter { item -> item.mediaMetadata.isPlayable == true }
                            .mapNotNull { item -> items.build(MediaItems.song(item)) }
                    }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { emptyList() }
            }.orEmpty()
            val albumId = MediaItems.song(resolved.first()).albumId
            if (browsedQueue.isNotEmpty()) {
                val selected = selection?.index?.takeIf { browsedQueue.getOrNull(it)?.mediaId == resolved.first().mediaId }
                    ?: browsedQueue.indexOfFirst { it.mediaId == resolved.first().mediaId }
                if (selected >= 0) { queue = browsedQueue; index = selected }
            } else if (selection == null && albumId != null) {
                val savedAlbum = offline.tracks.value.filter { it.status == OfflineStatus.READY && it.record.song.albumId == albumId }
                    .mapNotNull { items.build(it.record.song) }
                val album = if (savedAlbum.any { it.mediaId == resolved.first().mediaId }) savedAlbum
                    else try { withContext(Dispatchers.IO) { library.children("album:$albumId").mapNotNull { items.build(MediaItems.song(it)) } } }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { emptyList() }
                val selected = album.indexOfFirst { it.mediaId == resolved.first().mediaId }
                if (selected >= 0) { queue = album; index = selected }
            }
        }
        MediaSession.MediaItemsWithStartPosition(queue, index, startPositionMs)
    }

    override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> = future {
        when (customCommand.customAction) {
            REPEAT -> {
                session.player.repeatMode = when (session.player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                updateButtons(session)
                SessionResult(SessionResult.RESULT_SUCCESS)
            }
            FAVORITE -> toggleFavorite(session)
            else -> SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
        }
    }

    private suspend fun toggleFavorite(session: MediaSession): SessionResult {
        val item = session.player.currentMediaItem ?: return SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE)
        if (favoriteBusy) return SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE)
        val song = MediaItems.song(item)
        val account = offline.account()
        val generation = auth.generation.value
        favoriteBusy = true
        return try {
            withContext(Dispatchers.IO) { if (song.starred == null) music.star(song.id) else music.unstar(song.id) }
            if (auth.generation.value != generation || offline.account() != account) return SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE)
            val updated = song.copy(starred = if (song.starred == null) "starred" else null)
            val updatedItem = items.build(updated) ?: return SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE)
            for (index in 0 until session.player.mediaItemCount) {
                if (session.player.getMediaItemAt(index).mediaId == item.mediaId) session.player.replaceMediaItem(index, updatedItem)
            }
            withContext(Dispatchers.IO) {
                if (auth.generation.value != generation) throw CancellationException("Account changed")
                offline.records(account ?: "").filter { it.song.id == song.id }.forEach { offline.updateSong(it.key, updated) }
                if (updated.starred != null && account != null && offline.favoritesEnabled(account)) downloader.queue(listOf(updated), wifiOnly = true)
            }
            updateButtons(session)
            SessionResult(SessionResult.RESULT_SUCCESS)
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { SessionResult(SessionResult.RESULT_ERROR_IO) }
        finally { favoriteBusy = false }
    }

    fun updateButtons(session: MediaSession) {
        val favorite = session.player.currentMediaItem?.let(MediaItems::song)?.starred != null
        val repeat = when (session.player.repeatMode) {
            Player.REPEAT_MODE_ALL -> "Repeat all"
            Player.REPEAT_MODE_ONE -> "Repeat one"
            else -> "Repeat off"
        }
        session.setCustomLayout(listOf(
            CommandButton.Builder().setDisplayName(if (favorite) "Remove from favorites" else "Add to favorites")
                .setIconResId(if (favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
                .setSessionCommand(SessionCommand(FAVORITE, Bundle.EMPTY)).build(),
            CommandButton.Builder().setDisplayName(repeat).setIconResId(when (session.player.repeatMode) {
                Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat
                Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                else -> R.drawable.ic_repeat_off
            })
                .setSessionCommand(SessionCommand(REPEAT, Bundle.EMPTY)).build()
        ))
    }

    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        val generation = auth.generation.value
        val job = scope.launch {
            try {
                if (generation % 2L != 0L) throw CancellationException("Account is changing")
                val value = block()
                if (auth.generation.value != generation) throw CancellationException("Account changed")
                result.set(value)
            }
            catch (error: CancellationException) { result.cancel(false) }
            catch (error: Exception) { result.setException(error) }
        }
        result.addListener({ if (result.isCancelled) job.cancel() }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
        return result
    }

    fun release() { scope.cancel() }
    companion object {
        const val FAVORITE = "com.resonance.music.FAVORITE"
        const val REPEAT = "com.resonance.music.REPEAT"
    }
}
