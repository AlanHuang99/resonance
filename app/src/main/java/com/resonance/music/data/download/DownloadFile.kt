package com.resonance.music.data.download

import com.resonance.music.data.api.models.SongItem
import java.security.MessageDigest
import java.util.Locale

/** A stable name prevents repeated taps from creating copies and separates different servers' tracks. */
internal fun downloadKey(server: String, username: String, songId: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest("$server\u0000$username\u0000$songId".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

internal fun downloadPath(song: SongItem, key: String): String {
    val artist = safeComponent(song.artist, "Unknown artist")
    val album = safeComponent(song.album, "Unknown album")
    val title = safeComponent(song.title, "Untitled")
    val numbering = listOfNotNull(
        song.discNumber?.takeIf { it > 0 }?.toString(),
        song.track?.takeIf { it > 0 }?.toString()?.padStart(2, '0')
    ).joinToString("-").let { if (it.isEmpty()) "" else "$it - " }
    val suffix = song.suffix?.lowercase(Locale.ROOT)
        ?.takeIf { it.matches(Regex("[a-z0-9]{1,10}")) }
        ?: when (song.contentType?.lowercase(Locale.ROOT)?.substringBefore(';')) {
            "audio/mpeg" -> "mp3"
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/mp4", "audio/x-m4a" -> "m4a"
            "audio/ogg", "application/ogg" -> "ogg"
            "audio/opus" -> "opus"
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/aac" -> "aac"
            else -> null
        }
    val extension = suffix?.let { ".$it" }.orEmpty()
    return "Resonance/$artist/$album/$numbering$title [${key.take(12)}]$extension"
}

private fun safeComponent(value: String?, fallback: String): String {
    val cleaned = value.orEmpty().map { char ->
        if (char.isISOControl() || char in "/\\:*?\"<>|") '_' else char
    }.joinToString("").trim().trim('.').trim()
    // Bound UTF-8 bytes, including emoji and CJK, so the complete filename fits the filesystem limit.
    val limited = StringBuilder()
    var bytes = 0
    for (codePoint in cleaned.codePoints().toArray()) {
        val text = String(Character.toChars(codePoint))
        bytes += text.toByteArray(Charsets.UTF_8).size
        if (bytes > 160) break
        limited.append(text)
    }
    return limited.toString().trim().trimEnd('.').ifBlank { fallback }
}
