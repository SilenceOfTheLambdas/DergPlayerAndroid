package com.silenceofthelambda.dergplayer.ui.tui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
    onPlaylistClick: (String) -> Unit
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

    Column(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TuiLoadingIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1), // List style for TUI looks better
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlists) { playlist ->
                    TuiBorderBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlaylistClick(playlist.id) }
                    ) {
                        Column {
                            TuiText(text = playlist.title, fontWeight = FontWeight.Bold)
                            TuiText(text = "${playlist.count} tracks", style = TuiTheme.typography.copy(fontSize = 12.sp))
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
    onSongClick: (Song, List<Song>) -> Unit,
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

    Column(modifier = Modifier.fillMaxSize()) {
        TuiText(
            text = "< BACK",
            modifier = Modifier.padding(16.dp).clickable { onBack() },
            fontWeight = FontWeight.Bold
        )
        
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TuiLoadingIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(songs) { song ->
                    TuiBorderBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSongClick(song, songs.toList()) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                TuiText(text = song.title, fontWeight = FontWeight.Bold)
                                TuiText(text = song.artist, style = TuiTheme.typography.copy(fontSize = 12.sp))
                            }
                            TuiText(text = song.duration)
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
    onSongClick: (Song, List<Song>) -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<Song>() }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        TuiText(
            text = "< BACK",
            modifier = Modifier.padding(16.dp).clickable { onBack() },
            fontWeight = FontWeight.Bold
        )

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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TuiLoadingIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(results) { song ->
                    TuiBorderBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSongClick(song, results.toList()) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                TuiText(text = song.title, fontWeight = FontWeight.Bold)
                                TuiText(text = song.artist, style = TuiTheme.typography.copy(fontSize = 12.sp))
                            }
                            TuiText(text = song.duration)
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
