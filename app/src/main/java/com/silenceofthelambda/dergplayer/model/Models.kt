package com.silenceofthelambda.dergplayer.model

data class Playlist(
    val id: String,
    val title: String,
    val count: Long,
    val thumbnail: String?
)

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val duration: String,
    val thumbnail: String?,
    val publishedAt: String? = null,
    val playlistTitle: String? = null
)

enum class ShuffleMode {
    OFF,
    ON,
    SMART
}
