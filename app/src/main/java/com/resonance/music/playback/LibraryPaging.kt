package com.resonance.music.playback

internal fun <T> libraryPage(items: List<T>, page: Int, pageSize: Int): List<T> {
    if (page < 0 || pageSize <= 0) return emptyList()
    val start = page.toLong() * pageSize
    if (start >= items.size) return emptyList()
    return items.subList(start.toInt(), (start + pageSize).coerceAtMost(items.size.toLong()).toInt())
}
