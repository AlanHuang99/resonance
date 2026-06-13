package com.resonance.music.playback

/**
 * Pure, side-effect-free transforms for the playback queue. Kept separate from
 * [PlaybackManager] so the index bookkeeping (the part that silently corrupts a
 * queue when it is wrong) is unit-testable without Android or Media3.
 *
 * Every function is bounds-safe: an out-of-range index yields a sensible result
 * (clamp for insert, no-op for move/remove) rather than throwing.
 */
object QueueEdits {

    fun <T> insertAt(list: List<T>, index: Int, items: List<T>): List<T> {
        if (items.isEmpty()) return list
        val at = index.coerceIn(0, list.size)
        return ArrayList<T>(list.size + items.size).apply {
            addAll(list.subList(0, at))
            addAll(items)
            addAll(list.subList(at, list.size))
        }
    }

    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun <T> removeAt(list: List<T>, index: Int): List<T> {
        if (index !in list.indices) return list
        return list.toMutableList().apply { removeAt(index) }
    }
}
