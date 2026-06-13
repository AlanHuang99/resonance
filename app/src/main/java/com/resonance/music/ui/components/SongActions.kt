package com.resonance.music.ui.components

/**
 * Per-song overflow-menu actions. Bundled so screens pass one object instead of
 * threading four lambdas through every composable signature. Any field left null
 * hides its menu entry.
 */
data class SongActions(
    val onPlayNext: (() -> Unit)? = null,
    val onAddToQueue: (() -> Unit)? = null,
    val onGoToAlbum: (() -> Unit)? = null,
    val onGoToArtist: (() -> Unit)? = null
)
