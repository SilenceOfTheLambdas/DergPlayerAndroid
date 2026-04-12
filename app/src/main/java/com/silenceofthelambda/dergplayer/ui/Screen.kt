package com.silenceofthelambda.dergplayer.ui

sealed class Screen(val route: String) {
    object Playlists : Screen("playlists")
    object PlaylistDetail : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist/$playlistId"
    }
    object Search : Screen("search")
    object Player : Screen("player")
}
