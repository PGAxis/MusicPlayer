package dev.pgaxis.musicaxs.services

import androidx.annotation.Keep

fun List<Long>.stableHash(playlistId: Long): Long {
    var hash = -3750763034362895579L
    fun foldLong(v0: Long) {
        var v = v0
        repeat(8) {
            hash = hash xor (v and 0xFF)
            hash *= 1099511628211L
            v = v ushr 8
        }
    }
    foldLong(playlistId)
    for (id in this) foldLong(id)
    return hash
}

@Keep
enum class Theme(private val displayName: String) {
    CYAN("theme_cyan"),
    GRAYSCALE("theme_gray"),
    EMBER("theme_ember"),
    AETHER("theme_aether"),
    PHOSPHOR("theme_phosphor"),
    CHALK("theme_chalk"),
    SUNSHINE("theme_sunshine"),
    BORDO("theme_bordo"),
    VOID("theme_void");

    override fun toString(): String = displayName
}