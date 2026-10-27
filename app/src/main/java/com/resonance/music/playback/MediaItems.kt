package com.resonance.music.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.HeartRating
import com.google.gson.Gson
import com.resonance.music.data.api.SubsonicApiHelper
import com.resonance.music.data.api.models.SongItem
import com.resonance.music.data.download.OfflineLibrary
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaItems @Inject constructor(private val api: SubsonicApiHelper, private val offline: OfflineLibrary, private val artwork: ArtworkStore) {
    fun build(song: SongItem): MediaItem? {
        val uri = offline.localUri(song.id)?.toString() ?: api.getStreamUrl(song.id) ?: return null
        val art = song.coverArt?.let(artwork::uri)
        return MediaItem.Builder().setMediaId(song.id).setUri(uri).setMediaMetadata(
            MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).setAlbumTitle(song.album)
                .setDisplayTitle(song.title).setSubtitle(song.artist).setDescription(song.album)
                .setUserRating(HeartRating(song.starred != null))
                .setArtworkUri(art).setIsBrowsable(false).setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(Bundle().apply { putString(SONG, Gson().toJson(song)) }).build()
        ).build()
    }

    companion object {
        private const val SONG = "resonance.song"
        fun hasSong(item: MediaItem) = item.mediaMetadata.extras?.containsKey(SONG) == true
        fun song(item: MediaItem): SongItem {
            val metadata = item.mediaMetadata
            return metadata.extras?.getString(SONG)?.let {
                runCatching { Gson().fromJson(it, SongItem::class.java) }.getOrNull()
            } ?: SongItem(id = item.mediaId, title = metadata.title?.toString().orEmpty(),
                artist = metadata.artist?.toString(), album = metadata.albumTitle?.toString(), coverArt = metadata.extras?.getString("coverArt"))
        }
    }
}
