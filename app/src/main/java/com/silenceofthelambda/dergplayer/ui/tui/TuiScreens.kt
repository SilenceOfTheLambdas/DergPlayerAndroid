package com.silenceofthelambda.dergplayer.ui.tui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silenceofthelambda.dergplayer.api.YouTubeClient
import com.silenceofthelambda.dergplayer.model.Playlist
import com.silenceofthelambda.dergplayer.model.Song
import kotlinx.coroutines.launch

@Composable
fun TuiTopBar(
    title: String,
    onSearchClick: () -> Unit = {},
    showSearch: Boolean = true
) {
    TuiBorderBox(
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TuiText(text = title, fontWeight = FontWeight.Bold, style = TuiTheme.typography.copy(fontSize = 18.sp))
            if (showSearch) {
                TuiText(
                    text = "[ SEARCH ]",
                    modifier = Modifier.clickable { onSearchClick() },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TuiPlaylistsScreen(
    youtubeClient: YouTubeClient,
    refreshTrigger: Int,
    onPlaylistClick: (String) -> Unit,
    onPlayPlaylist: (String) -> Unit
) {
    val playlists = remember { mutableStateListOf<Playlist>() }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTrigger) {
        if (playlists.isEmpty()) {
            isLoading = true
            playlists.clear()
            playlists.addAll(youtubeClient.getPlaylists())
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        TuiText(
            text = "SYSTEM@DERGPLAYER:~/music/playlists$",
            style = TuiTheme.typography.copy(fontSize = 12.sp, color = TuiTheme.colors.primary.copy(alpha = 0.8f)),
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TuiLoadingIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
            ) {
                itemsIndexed(playlists) { index, playlist ->
                    TuiBorderBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlaylistClick(playlist.id) },
                        title = String.format("%02d", index + 1)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                TuiText(text = playlist.title.uppercase(), fontWeight = FontWeight.Bold)
                                TuiText(
                                    text = "FILE: /lib/${playlist.id.take(8)}.pls",
                                    style = TuiTheme.typography.copy(fontSize = 10.sp, color = TuiTheme.colors.primary.copy(alpha = 0.5f))
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                TuiText(
                                    text = "[ ${playlist.count} ]",
                                    fontWeight = FontWeight.Bold
                                )
                                TuiText(
                                    text = "ITEMS",
                                    style = TuiTheme.typography.copy(fontSize = 9.sp, color = TuiTheme.colors.primary.copy(alpha = 0.5f))
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            TuiButton(
                                text = "PLAY",
                                onClick = { onPlayPlaylist(playlist.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TuiPlaylistDetailScreen(
    playlistId: String,
    youtubeClient: YouTubeClient,
    refreshTrigger: Int,
    currentSongId: String?,
    onSongClick: (Song, List<Song>) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onBack: () -> Unit
) {
    val songs = remember { mutableStateListOf<Song>() }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId, refreshTrigger) {
        isLoading = true
        songs.clear()
        songs.addAll(youtubeClient.getPlaylistItems(playlistId))
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TuiText(
                text = "[ BACK ]",
                modifier = Modifier.clickable { onBack() },
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(16.dp))
            TuiText(
                text = "DIRECTORY: /playlists/${playlistId.take(8)}",
                style = TuiTheme.typography.copy(fontSize = 11.sp, color = TuiTheme.colors.primary.copy(alpha = 0.7f)),
                modifier = Modifier.weight(1f)
            )
        }
        
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TuiLoadingIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
            ) {
                itemsIndexed(songs) { index, song ->
                    TuiBorderBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSongClick(song, songs.toList()) },
                        title = String.format("%03d", index + 1)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                TuiText(text = song.title.uppercase(), fontWeight = FontWeight.Bold, maxLines = 1)
                                TuiText(
                                    text = "BY: ${song.artist.uppercase()}", 
                                    style = TuiTheme.typography.copy(fontSize = 10.sp, color = TuiTheme.colors.primary.copy(alpha = 0.6f)),
                                    maxLines = 1
                                )
                            }
                            if (song.id != currentSongId) {
                                Spacer(modifier = Modifier.width(8.dp))
                                TuiButton(
                                    text = "PLAY",
                                    onClick = { onSongClick(song, songs.toList()) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TuiButton(
                                    text = "QUEU",
                                    onClick = { onAddToQueue(song) }
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TuiText(text = song.duration, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TuiSearchScreen(
    youtubeClient: YouTubeClient,
    currentSongId: String?,
    onSongClick: (Song, List<Song>) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<Song>() }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TuiText(
                text = "[ BACK ]",
                modifier = Modifier.clickable { onBack() },
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(16.dp))
            TuiText(
                text = "QUERY: ${if (query.isEmpty()) "*" else query.uppercase()}",
                style = TuiTheme.typography.copy(fontSize = 11.sp, color = TuiTheme.colors.primary.copy(alpha = 0.7f)),
                modifier = Modifier.weight(1f)
            )
        }

        TuiTextField(
            value = query,
            onValueChange = { 
                query = it
                if (it.length > 2) {
                    scope.launch {
                        isLoading = true
                        results.clear()
                        results.addAll(youtubeClient.search(it))
                        isLoading = false
                    }
                }
            },
            placeholder = "SEARCH MUSIC...",
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TuiLoadingIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
            ) {
                itemsIndexed(results) { index, song ->
                    TuiBorderBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSongClick(song, results.toList()) },
                        title = String.format("%03d", index + 1)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                TuiText(text = song.title.uppercase(), fontWeight = FontWeight.Bold, maxLines = 1)
                                TuiText(
                                    text = "BY: ${song.artist.uppercase()}", 
                                    style = TuiTheme.typography.copy(fontSize = 10.sp, color = TuiTheme.colors.primary.copy(alpha = 0.6f)),
                                    maxLines = 1
                                )
                            }
                            if (song.id != currentSongId) {
                                Spacer(modifier = Modifier.width(8.dp))
                                TuiButton(
                                    text = "PLAY",
                                    onClick = { onSongClick(song, results.toList()) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TuiButton(
                                    text = "QUEU",
                                    onClick = { onAddToQueue(song) }
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TuiText(text = song.duration, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TuiMiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onClick: () -> Unit
) {
    TuiBorderBox(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onClick() },
        title = "NOW PLAYING"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                TuiText(text = song.title, fontWeight = FontWeight.Bold, maxLines = 1)
                TuiText(text = song.artist, style = TuiTheme.typography.copy(fontSize = 11.sp), maxLines = 1)
            }
            TuiText(
                text = if (isPlaying) "[ PAUSE ]" else "[ PLAY ]",
                modifier = Modifier.clickable { onTogglePlay() }.padding(start = 8.dp),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
