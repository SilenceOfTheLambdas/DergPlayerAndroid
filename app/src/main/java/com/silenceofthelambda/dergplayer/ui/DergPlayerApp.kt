package com.silenceofthelambda.dergplayer.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.silenceofthelambda.dergplayer.api.YouTubeClient
import com.silenceofthelambda.dergplayer.ui.tui.*

@Composable
fun DergPlayerApp(viewModel: PlayerViewModel, youtubeClient: YouTubeClient, refreshTrigger: Int) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isPlayerScreen = currentRoute == Screen.Player.route
    val isQueueScreen = currentRoute == Screen.Queue.route

    val currentSong by viewModel.currentSong.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val tuiColors by viewModel.tuiColors.collectAsState()
    val tuiSchemeName by viewModel.tuiSchemeName.collectAsState()
    val isLiked by viewModel.isLiked.collectAsState()
    val shuffleMode by viewModel.shuffleMode.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val playbackPosition by viewModel.playbackPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val asciiArt by viewModel.asciiArt.collectAsState()
    val remainingQueue by viewModel.remainingQueue.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val systemStatus by viewModel.systemStatus.collectAsState()
    val visualizerMagnitudesState = viewModel.visualizerMagnitudes.collectAsState()
    val visualizerMagnitudesProvider = remember { { visualizerMagnitudesState.value } }

    TuiTheme(colors = tuiColors) {
        Box(modifier = Modifier.fillMaxSize().background(TuiTheme.colors.background)) {
            ScanlineOverlay()

            Column(modifier = Modifier.fillMaxSize()) {
                if (!isPlayerScreen && !isQueueScreen) {
                    TuiTopBar(
                        title = "DERG PLAYER V1.1.0",
                        onSearchClick = { navController.navigate(Screen.Search.route) },
                        showSearch = currentRoute != Screen.Search.route
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Playlists.route,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = { fadeIn(animationSpec = tween(200)) },
                        exitTransition = { fadeOut(animationSpec = tween(200)) }
                    ) {
                        composable(Screen.Playlists.route) {
                            TuiPlaylistsScreen(
                                youtubeClient = youtubeClient,
                                refreshTrigger = refreshTrigger,
                                onPlaylistClick = { playlistId ->
                                    navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                                },
                                onPlayPlaylist = { playlistId ->
                                    viewModel.playPlaylist(playlistId)
                                    navController.navigate(Screen.Player.route)
                                },
                                onShufflePlaylist = { playlistId ->
                                    viewModel.shufflePlaylist(playlistId)
                                    navController.navigate(Screen.Player.route)
                                }
                            )
                        }
                        composable(Screen.PlaylistDetail.route) { backStackEntry ->
                            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
                            TuiPlaylistDetailScreen(
                                playlistId = playlistId,
                                youtubeClient = youtubeClient,
                                playerViewModel = viewModel,
                                refreshTrigger = refreshTrigger,
                                currentSongId = currentSong?.id,
                                onSongClick = { song, list -> 
                                    viewModel.playSong(song, list)
                                    navController.navigate(Screen.Player.route)
                                },
                                onAddToQueue = { viewModel.addToQueue(it) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.Search.route) {
                            TuiSearchScreen(
                                youtubeClient = youtubeClient,
                                playerViewModel = viewModel,
                                currentSongId = currentSong?.id,
                                onSongClick = { song, list -> 
                                    viewModel.playSong(song, list)
                                    navController.navigate(Screen.Player.route)
                                },
                                onAddToQueue = { viewModel.addToQueue(it) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.Player.route) {
                            val onPrevious: () -> Unit = remember { { viewModel.skipToPrevious() } }
                            val onTogglePlay: () -> Unit = remember { { viewModel.togglePlayPause() } }
                            val onNext: () -> Unit = remember { { viewModel.skipToNext() } }
                            val onToggleLike: () -> Unit = remember { { viewModel.toggleLike() } }
                            val onToggleShuffle: () -> Unit = remember { { viewModel.toggleShuffle() } }
                            val onToggleRepeat: () -> Unit = remember { { viewModel.toggleRepeat() } }
                            val onSetScheme: (String) -> Unit = remember { { s -> viewModel.setTuiScheme(s) } }
                            val onSeek: (Float) -> Unit = remember(duration) { { p -> viewModel.seekTo((p * duration).toLong()) } }
                            val onVolumeChange: (Float) -> Unit = remember { { v -> viewModel.setVolume(v) } }
                            val onQueueClick: () -> Unit = remember { { navController.navigate(Screen.Queue.route) } }
                            val onBack: () -> Unit = remember { { 
                                navController.popBackStack()
                                Unit
                            } }

                            TuiPlayerScreen(
                                currentTitle = currentSong?.title ?: "Unknown Title",
                                currentArtist = currentSong?.artist ?: "Unknown Artist",
                                progress = if (duration > 0) playbackPosition.toFloat() / duration else 0f,
                                currentTime = formatDuration(playbackPosition),
                                totalTime = formatDuration(duration),
                                isPlaying = isPlaying,
                                isLiked = isLiked,
                                shuffleMode = shuffleMode,
                                repeatMode = repeatMode,
                                currentScheme = tuiSchemeName,
                                asciiArt = asciiArt,
                                nextTitle = remainingQueue.firstOrNull()?.title ?: "None",
                                volume = volume,
                                systemStatus = systemStatus,
                                visualizerMagnitudes = visualizerMagnitudesProvider,
                                onPrevious = onPrevious,
                                onTogglePlay = onTogglePlay,
                                onNext = onNext,
                                onToggleLike = onToggleLike,
                                onToggleShuffle = onToggleShuffle,
                                onToggleRepeat = onToggleRepeat,
                                onSetScheme = onSetScheme,
                                onSeek = onSeek,
                                onVolumeChange = onVolumeChange,
                                onQueueClick = onQueueClick,
                                onBack = onBack
                            )
                        }
                        composable(Screen.Queue.route) {
                            TuiQueueScreen(
                                queue = queue,
                                currentSongId = currentSong?.id,
                                onSongClick = { song ->
                                    viewModel.playSong(song)
                                    navController.navigate(Screen.Player.route)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }

                // MiniPlayer
                if (!isPlayerScreen && !isQueueScreen) {
                    currentSong?.let { song ->
                        TuiMiniPlayer(
                            song = song,
                            isPlaying = isPlaying,
                            onTogglePlay = { viewModel.togglePlayPause() },
                            onClick = { navController.navigate(Screen.Player.route) }
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
