package com.silenceofthelambda.dergplayer.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.silenceofthelambda.dergplayer.api.YouTubeClient
import com.silenceofthelambda.dergplayer.model.Playlist
import com.silenceofthelambda.dergplayer.model.Song
import com.silenceofthelambda.dergplayer.model.ShuffleMode
import com.silenceofthelambda.dergplayer.ui.theme.TidalAccent
import com.silenceofthelambda.dergplayer.ui.theme.TidalGrey
import com.silenceofthelambda.dergplayer.ui.theme.TidalLightGrey
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DergPlayerApp(viewModel: PlayerViewModel, youtubeClient: YouTubeClient, refreshTrigger: Int) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isPlayerScreen = currentRoute == Screen.Player.route

    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val dominantColor by viewModel.dominantColor.collectAsState()

    Scaffold(
        topBar = {
            if (!isPlayerScreen) {
                CenterAlignedTopAppBar(
                    title = { Text("DergPlayer", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)) },
                    actions = {
                        IconButton(onClick = { navController.navigate(Screen.Search.route) }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // Background Dynamic Gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(dominantColor.copy(alpha = 0.4f), Color.Black)
                        )
                    )
            )

            NavHost(
                navController = navController,
                startDestination = Screen.Playlists.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isPlayerScreen) PaddingValues(0.dp) else padding),
                enterTransition = { fadeIn(animationSpec = tween(400)) + slideInHorizontally(animationSpec = tween(400)) { it } },
                exitTransition = { fadeOut(animationSpec = tween(400)) + slideOutHorizontally(animationSpec = tween(400)) { -it } },
                popEnterTransition = { fadeIn(animationSpec = tween(400)) + slideInHorizontally(animationSpec = tween(400)) { -it } },
                popExitTransition = { fadeOut(animationSpec = tween(400)) + slideOutHorizontally(animationSpec = tween(400)) { it } }
            ) {
                composable(Screen.Playlists.route) {
                    PlaylistsScreen(
                        youtubeClient = youtubeClient,
                        refreshTrigger = refreshTrigger,
                        onPlaylistClick = { playlistId ->
                            navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                        }
                    )
                }
                composable(Screen.PlaylistDetail.route) { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
                    PlaylistDetailScreen(
                        playlistId = playlistId,
                        youtubeClient = youtubeClient,
                        refreshTrigger = refreshTrigger,
                        onSongClick = { song, list -> 
                            viewModel.playSong(song, list)
                            navController.navigate(Screen.Player.route)
                        }
                    )
                }
                composable(Screen.Search.route) {
                    SearchScreen(
                        youtubeClient = youtubeClient,
                        onSongClick = { song, list -> 
                            viewModel.playSong(song, list)
                            navController.navigate(Screen.Player.route)
                        }
                    )
                }
                composable(Screen.Player.route) {
                    PlayerScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }
            }

            // Floating MiniPlayer Pill (Glassmorphism)
            if (!isPlayerScreen) {
                currentSong?.let { song ->
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 32.dp)
                    ) {
                        MiniPlayer(
                            song = song,
                            isPlaying = isPlaying,
                            dominantColor = dominantColor,
                            onTogglePlay = { viewModel.togglePlayPause() },
                            onClick = { navController.navigate(Screen.Player.route) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    dominantColor: Color,
    onTogglePlay: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        color = TidalLightGrey,
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, dominantColor.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(32.dp))
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(52.dp)
            ) {
                AsyncImage(
                    model = song.thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    color = Color.White
                )
                Text(
                    song.artist,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun PlaylistsScreen(youtubeClient: YouTubeClient, refreshTrigger: Int, onPlaylistClick: (String) -> Unit) {
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

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = TidalAccent)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 120.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Text(
                    "Your Library",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(playlists) { playlist ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlaylistClick(playlist.id) }
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    ) {
                        AsyncImage(
                            model = playlist.thumbnail,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        playlist.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${playlist.count} songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailScreen(playlistId: String, youtubeClient: YouTubeClient, refreshTrigger: Int, onSongClick: (Song, List<Song>) -> Unit) {
    val songs = remember { mutableStateListOf<Song>() }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId, refreshTrigger) {
        isLoading = true
        songs.clear()
        songs.addAll(youtubeClient.getPlaylistItems(playlistId))
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = TidalAccent)
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            items(songs) { song ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSongClick(song, songs.toList()) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(56.dp)
                    ) {
                        AsyncImage(
                            model = song.thumbnail,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            song.title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            song.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1
                        )
                    }
                    Text(
                        song.duration,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun SearchScreen(youtubeClient: YouTubeClient, onSongClick: (Song, List<Song>) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<Song>() }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search songs, artists...", color = Color.White.copy(alpha = 0.5f)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.1f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            trailingIcon = {
                IconButton(onClick = {
                    scope.launch {
                        isLoading = true
                        results.clear()
                        results.addAll(youtubeClient.search(query))
                        isLoading = false
                    }
                }) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                }
            }
        )
        Box(modifier = Modifier.weight(1f)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = TidalAccent)
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(results) { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSongClick(song, results.toList()) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(56.dp)
                        ) {
                            AsyncImage(
                                model = song.thumbnail,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                song.title,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                song.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.6f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: PlayerViewModel, onBack: () -> Unit) {
    val currentSong by viewModel.currentSong.collectAsState()
    val remainingQueue by viewModel.remainingQueue.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val dominantColor by viewModel.dominantColor.collectAsState()
    val shuffleMode by viewModel.shuffleMode.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isLiked by viewModel.isLiked.collectAsState()

    var showQueue by remember { mutableStateOf(false) }
    val scaffoldState = rememberBottomSheetScaffoldState()

    if (showQueue) {
        PlayQueueScreen(viewModel = viewModel, onBack = { showQueue = false })
    } else {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetContent = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp)
                        .padding(horizontal = 24.dp)
                ) {
                    if (remainingQueue.isNotEmpty()) {
                        item {
                            Text(
                                "Queue",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
                            )
                        }
                        items(remainingQueue) { song ->
                            SongItem(
                                song = song,
                                onClick = { viewModel.playSong(song) }
                            )
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            },
            sheetPeekHeight = 100.dp,
            sheetContainerColor = TidalLightGrey.copy(alpha = 0.98f),
            sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            sheetDragHandle = {
                BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f))
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().background(Color.Black).padding(paddingValues)) {
                // Blurred background art
                currentSong?.let { song ->
                    AsyncImage(
                        model = song.thumbnail,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(0.4f)
                            .blur(50.dp),
                        contentScale = ContentScale.Crop
                    )
                }
                
                // Dynamic Gradient Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(dominantColor.copy(alpha = 0.5f), Color.Black)
                            )
                        )
                )
                
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)
                ) {
                    PlayerHeader(onBack = onBack, onShowQueue = { showQueue = true })
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    currentSong?.let { song ->
                        AlbumArt(
                            song = song,
                            isPlaying = isPlaying,
                            onSkipNext = { viewModel.skipToNext() },
                            onSkipPrevious = { viewModel.skipToPrevious() }
                        )
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    currentSong?.let { song ->
                        SongInfo(song = song, isLiked = isLiked, onToggleLike = { viewModel.toggleLike() })
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    PlaybackProgress(
                        positionFlow = viewModel.playbackPosition,
                        durationFlow = viewModel.duration,
                        onSeek = { viewModel.seekTo(it) }
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    PlaybackControls(
                        isPlaying = isPlaying,
                        shuffleMode = shuffleMode,
                        repeatMode = repeatMode,
                        onToggleShuffle = { viewModel.toggleShuffle() },
                        onSkipPrevious = { viewModel.skipToPrevious() },
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onSkipNext = { viewModel.skipToNext() },
                        onToggleRepeat = { viewModel.toggleRepeat() }
                    )
                    
                    Spacer(modifier = Modifier.height(120.dp))
                }
            }
        }
    }
}

@Composable
fun PlayerHeader(onBack: () -> Unit, onShowQueue: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize", tint = Color.White, modifier = Modifier.size(36.dp))
        }
        Text(
            "NOW PLAYING",
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
            color = Color.White.copy(alpha = 0.7f)
        )
        IconButton(onClick = onShowQueue) {
            Icon(Icons.Default.PlaylistPlay, contentDescription = "Queue", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun AlbumArt(
    song: Song,
    isPlaying: Boolean,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val albumArtScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.85f,
        animationSpec = tween(durationMillis = 500),
        label = "AlbumArtScale"
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer(
                scaleX = albumArtScale,
                scaleY = albumArtScale,
                translationX = offsetX.value
            )
            .padding(horizontal = 8.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount)
                        }
                    },
                    onDragEnd = {
                        if (offsetX.value > 200f) {
                            onSkipPrevious()
                        } else if (offsetX.value < -200f) {
                            onSkipNext()
                        }
                        scope.launch {
                            offsetX.animateTo(0f, tween(300))
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            offsetX.animateTo(0f, tween(300))
                        }
                    }
                )
            }
    ) {
        AsyncImage(
            model = song.thumbnail,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun SongInfo(song: Song, isLiked: Boolean, onToggleLike: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onToggleLike) {
            Icon(
                if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isLiked) "Like" else "Unlike",
                tint = if (isLiked) TidalAccent else Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun PlaybackProgress(
    positionFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    durationFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    onSeek: (Long) -> Unit
) {
    val position by positionFlow.collectAsState()
    val duration by durationFlow.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = if (duration > 0) position.toFloat() / duration.toFloat() else 0f,
            onValueChange = { onSeek((it * duration).toLong()) },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatDuration(position),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f)
            )
            Text(
                formatDuration(duration),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    shuffleMode: ShuffleMode,
    repeatMode: Int,
    onToggleShuffle: () -> Unit,
    onSkipPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleRepeat: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                if (shuffleMode == ShuffleMode.SMART) Icons.Default.AutoAwesome else Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                tint = if (shuffleMode != ShuffleMode.OFF) TidalAccent else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
        IconButton(onClick = onSkipPrevious, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(42.dp))
        }
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable { onTogglePlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = Color.Black,
                modifier = Modifier.size(48.dp)
            )
        }
        IconButton(onClick = onSkipNext, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(42.dp))
        }
        IconButton(onClick = onToggleRepeat) {
            Icon(
                if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                contentDescription = "Repeat",
                tint = if (repeatMode != Player.REPEAT_MODE_OFF) TidalAccent else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun PlayQueueScreen(viewModel: PlayerViewModel, onBack: () -> Unit) {
    val queue by viewModel.queue.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().background(Color.Black).padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Play Queue", 
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black), 
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { viewModel.clearQueue() }) {
                Text("Clear", color = TidalAccent)
            }
        }
        
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(queue) { song ->
                SongItem(
                    song = song,
                    isCurrent = song.id == currentSong?.id,
                    onClick = { viewModel.playSong(song) },
                    onRemove = { viewModel.removeFromQueue(song.id) }
                )
            }
        }
    }
}

@Composable
fun SongItem(
    song: Song, 
    onClick: () -> Unit, 
    isCurrent: Boolean = false,
    onRemove: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(48.dp)
        ) {
            AsyncImage(
                model = song.thumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold
                ),
                color = if (isCurrent) TidalAccent else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onRemove != null && !isCurrent) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove", tint = Color.White.copy(alpha = 0.4f))
            }
        } else if (isCurrent) {
            Icon(Icons.Default.Equalizer, contentDescription = "Playing", tint = TidalAccent)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
