package com.silenceofthelambda.dergplayer.ui

import android.app.Application
import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.silenceofthelambda.dergplayer.api.YouTubeClient
import com.silenceofthelambda.dergplayer.api.StreamExtractor
import com.silenceofthelambda.dergplayer.model.Song
import com.silenceofthelambda.dergplayer.model.ShuffleMode
import com.silenceofthelambda.dergplayer.service.MediaPlaybackService
import com.silenceofthelambda.dergplayer.ui.tui.AmberColors
import com.silenceofthelambda.dergplayer.ui.tui.CyberpunkColors
import com.silenceofthelambda.dergplayer.ui.tui.MatrixColors
import com.silenceofthelambda.dergplayer.ui.tui.TuiColors
import com.silenceofthelambda.dergplayer.ui.tui.TuiUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(
    application: Application,
    private val youtubeClient: YouTubeClient
) : AndroidViewModel(application) {

    private val extractor = StreamExtractor()
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue

    private val _remainingQueue = MutableStateFlow<List<Song>>(emptyList())
    val remainingQueue: StateFlow<List<Song>> = _remainingQueue

    private val _originalQueue = MutableStateFlow<List<Song>>(emptyList())

    private val _shuffleMode = MutableStateFlow(ShuffleMode.OFF)
    val shuffleMode: StateFlow<ShuffleMode> = _shuffleMode

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _isLiked = MutableStateFlow(false)
    val isLiked: StateFlow<Boolean> = _isLiked

    private val _relatedSongs = MutableStateFlow<List<Song>>(emptyList())
    // Note: Public StateFlow removed as it is no longer used in UI, only internally for Smart Shuffle.

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val _dominantColor = MutableStateFlow<Color>(Color(0xFF00FF41))
    val dominantColor: StateFlow<Color> = _dominantColor

    private val _asciiArt = MutableStateFlow("")
    val asciiArt: StateFlow<String> = _asciiArt

    private val _tuiSchemeName = MutableStateFlow("Dynamic")
    val tuiSchemeName: StateFlow<String> = _tuiSchemeName

    private val _tuiColors = MutableStateFlow(
        TuiColors(
            background = Color(0xFF0D0208),
            surface = Color(0xFF0D0208),
            primary = Color(0xFF00FF41),
            onBackground = Color(0xFF00FF41),
            onSurface = Color(0xFF00FF41),
            scanlineColor = Color(0xFF00FF41).copy(alpha = 0.05f)
        )
    )
    val tuiColors: StateFlow<TuiColors> = _tuiColors

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume

    private val _systemStatus = MutableStateFlow("")
    val systemStatus: StateFlow<String> = _systemStatus

    private val _visualizerMagnitudes = MutableStateFlow<List<Float>>(emptyList())
    val visualizerMagnitudes: StateFlow<List<Float>> = _visualizerMagnitudes

    private var visualizer: Visualizer? = null
    private var visualizerSessionId = 0
    private var currentAudioSessionId = 0
    private var statusJob: Job? = null

    private var positionUpdateJob: Job? = null

    private val _dominantColorCache = mutableMapOf<String, Color>()
    private val _asciiArtCache = mutableMapOf<String, String>()

    private var visualizerThread: android.os.HandlerThread? = null
    private var visualizerHandler: android.os.Handler? = null
    private var binCache: List<Pair<Int, Int>>? = null
    private var lastN = -1
    private var lastVisualizerUpdate = 0L

    private val playerListener = object : MediaController.Listener, Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startPositionUpdates()
                updateVisualizer(true)
            } else {
                stopPositionUpdates()
                updateVisualizer(false)
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            android.util.Log.d("PlayerViewModel", "Audio session ID changed: $audioSessionId")
            currentAudioSessionId = audioSessionId
            if (_isPlaying.value) {
                updateVisualizer(true)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                _duration.value = controller?.duration ?: 0L
            } else if (playbackState == Player.STATE_ENDED) {
                onSongEnded()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return
            
            mediaItem?.let { item ->
                val songId = item.mediaId
                val song = _queue.value.find { it.id == songId }
                if (song != null) {
                    if (_currentSong.value?.id != songId) {
                        android.util.Log.d("PlayerViewModel", "Transitioning to song: ${song.title}")
                        _currentSong.value = song
                        updateRemainingQueue()
                        
                        // Trigger UI metadata update (from cache if ready)
                        viewModelScope.launch {
                            fetchSongMetadataUI(song)
                        }
                    }
                    // Always try to prefetch the next one
                    prefetchNextSong()
                }
            }
        }

        override fun onCustomCommand(
            controller: MediaController,
            command: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (command.customAction) {
                MediaPlaybackService.COMMAND_SKIP_NEXT -> skipToNext()
                MediaPlaybackService.COMMAND_SKIP_PREV -> skipToPrevious()
                MediaPlaybackService.COMMAND_SET_AUDIO_SESSION_ID -> {
                    val sessionId = args.getInt("audio_session_id", 0)
                    if (sessionId != 0) {
                        currentAudioSessionId = sessionId
                        if (_isPlaying.value) {
                            updateVisualizer(true)
                        }
                    }
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    init {
        val sessionToken = SessionToken(application, ComponentName(application, MediaPlaybackService::class.java))
        val future = MediaController.Builder(application, sessionToken)
            .setListener(playerListener)
            .buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val ctrl = future.get()
                controller = ctrl
                ctrl.addListener(playerListener)
                
                // Sync initial state
                _isPlaying.value = ctrl.isPlaying
                _duration.value = ctrl.duration
                _playbackPosition.value = ctrl.currentPosition
                _volume.value = ctrl.volume
                ctrl.repeatMode = _repeatMode.value
                
                if (ctrl.isPlaying) {
                    startPositionUpdates()
                    updateVisualizer(true)
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Error connecting to MediaController", e)
            }
        }, ContextCompat.getMainExecutor(application))
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (true) {
                _playbackPosition.value = controller?.currentPosition ?: 0L
                // Periodically try to re-init visualizer if it should be enabled but failed (e.g. permission was missing)
                try {
                    val isVisActive = try { visualizer?.enabled ?: false } catch (e: Exception) { false }
                    if (_isPlaying.value && !isVisActive) {
                        updateVisualizer(true)
                    }
                } catch (e: Exception) {
                    // Ignore
                }
                delay(1000)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun updateRemainingQueue() {
        val currentSong = _currentSong.value ?: return
        val currentQueue = _queue.value
        val currentIndex = currentQueue.indexOfFirst { it.id == currentSong.id }
        if (currentIndex != -1 && currentIndex < currentQueue.size - 1) {
            _remainingQueue.value = currentQueue.drop(currentIndex + 1)
        } else {
            _remainingQueue.value = emptyList()
        }
    }

    fun onSongEnded() {
        when (_repeatMode.value) {
            Player.REPEAT_MODE_ONE -> {
                controller?.seekTo(0)
                controller?.play()
            }
            Player.REPEAT_MODE_ALL -> {
                skipToNext()
            }
            Player.REPEAT_MODE_OFF -> {
                if (hasNext()) {
                    skipToNext()
                }
            }
        }
    }

    private fun hasNext(): Boolean {
        val currentIdx = _queue.value.indexOfFirst { it.id == _currentSong.value?.id }
        return currentIdx < _queue.value.size - 1
    }

    private fun getNextSong(): Song? {
        val currentList = _queue.value
        val currentIndex = currentList.indexOfFirst { it.id == _currentSong.value?.id }

        return if (currentIndex != -1 && currentIndex < currentList.size - 1) {
            currentList[currentIndex + 1]
        } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && currentList.isNotEmpty()) {
            currentList.first()
        } else {
            null
        }
    }

    private fun prefetchNextSong() {
        val nextSong = getNextSong()
        if (nextSong == null) {
            controller?.let { ctrl ->
                if (ctrl.mediaItemCount > 1) {
                    ctrl.removeMediaItems(1, ctrl.mediaItemCount)
                }
            }
            return
        }
        prefetchSong(nextSong)
        
        // Add to MediaController playlist for gapless playback
        viewModelScope.launch {
            val info = extractor.getFullStreamInfo(nextSong.id)
            if (info.streamUrl != null) {
                controller?.let { ctrl ->
                    // Clean up previous tracks from player playlist
                    val currentIdx = ctrl.currentMediaItemIndex
                    if (currentIdx > 0) {
                        ctrl.removeMediaItems(0, currentIdx)
                    }

                    // Only add if not already there as the next item
                    var alreadyInPlaylist = false
                    if (ctrl.mediaItemCount > 1) {
                        val nextItem = ctrl.getMediaItemAt(1)
                        if (nextItem.mediaId == nextSong.id) {
                            alreadyInPlaylist = true
                        }
                    }
                    
                    if (!alreadyInPlaylist) {
                        // Clear any items after the current one and add the new next item
                        if (ctrl.mediaItemCount > 1) {
                            ctrl.removeMediaItems(1, ctrl.mediaItemCount)
                        }
                        
                        val mediaMetadata = MediaMetadata.Builder()
                            .setTitle(nextSong.title)
                            .setArtist(nextSong.artist)
                            .setArtworkUri(Uri.parse(nextSong.thumbnail))
                            .build()
                        val mediaItem = MediaItem.Builder()
                            .setMediaId(nextSong.id)
                            .setUri(info.streamUrl)
                            .setMediaMetadata(mediaMetadata)
                            .build()
                        
                        ctrl.addMediaItem(mediaItem)
                        android.util.Log.d("PlayerViewModel", "Added next song to playlist: ${nextSong.title}")
                    }
                }
            }
        }
    }

    fun prefetchSong(song: Song) {
        viewModelScope.launch {
            android.util.Log.d("PlayerViewModel", "Prefetching song: ${song.title}")
            // Pre-calculate UI metadata
            launch { fetchSongMetadataUI(song, updateState = false) }
            // Pre-fetch stream info into StreamExtractor's cache
            extractor.getFullStreamInfo(song.id)
        }
    }

    private suspend fun processRecommendations(song: Song, info: StreamExtractor.FullStreamInfo) {
        // Prioritize Radio playlist for Smart Shuffle if enabled
        val recommendations = if (_shuffleMode.value == ShuffleMode.SMART) {
            val radioSongs = youtubeClient.getRelatedVideosFromRadio(song.id)
            if (radioSongs.isNotEmpty()) {
                youtubeClient.filterMusic(radioSongs)
            } else {
                // Fallback to related songs from extractor, but filter them too
                youtubeClient.filterMusic(info.relatedSongs)
            }
        } else {
            info.relatedSongs
        }

        if (_currentSong.value?.id != song.id) return

        _relatedSongs.value = recommendations

        // Add related songs to queue if smart shuffle is enabled
        if (_shuffleMode.value == ShuffleMode.SMART) {
            // Read latest queue state after potential suspension
            val currentIds = _queue.value.map { it.id }.toSet()
            // Limit to 3 recommendations
            val newRelated = recommendations.filterNot { it.id in currentIds }.take(3)
            
            if (newRelated.isNotEmpty()) {
                val currentQueue = _queue.value.toMutableList()
                val currentIdx = currentQueue.indexOfFirst { it.id == song.id }
                val remainingIdx = if (currentIdx != -1) currentIdx + 1 else currentQueue.size
                val remainingCount = currentQueue.size - remainingIdx

                if (remainingCount > 0) {
                    // Insert at regular intervals within the remaining queue
                    val interval = (remainingCount / newRelated.size).coerceAtLeast(1)
                    newRelated.forEachIndexed { i, relSong ->
                        val insertPos = (remainingIdx + (i + 1) * interval + i).coerceAtMost(currentQueue.size)
                        currentQueue.add(insertPos, relSong)
                    }
                } else {
                    // Append if remaining queue is empty
                    currentQueue.addAll(newRelated)
                }
                _queue.value = currentQueue
                updateRemainingQueue()
                showStatus("SMART: +${newRelated.size} SONGS")
                prefetchNextSong()
            }
        }
    }

    fun playSong(song: Song, contextQueue: List<Song> = emptyList()) {
        viewModelScope.launch {
            _currentSong.value = song
            _relatedSongs.value = emptyList()
            _isLiked.value = false

            // Parallelize non-essential tasks
            launch {
                val rating = youtubeClient.getVideoRating(song.id)
                _isLiked.value = rating == "like"
            }

            launch {
                fetchSongMetadataUI(song)
            }

            // Set queue if provided, otherwise maintain current or add song
            if (contextQueue.isNotEmpty()) {
                _originalQueue.value = contextQueue
                if (_shuffleMode.value != ShuffleMode.OFF) {
                    val firstSong = song
                    val others = contextQueue.filter { it.id != firstSong.id }.shuffled()
                    _queue.value = listOf(firstSong) + others
                } else {
                    _queue.value = contextQueue
                }
            } else {
                if (!_queue.value.any { it.id == song.id }) {
                    _queue.value = _queue.value + song
                }
                if (!_originalQueue.value.any { it.id == song.id }) {
                    _originalQueue.value = _originalQueue.value + song
                }
            }
            updateRemainingQueue()

            // Fetch stream info (essential for playback)
            val info = extractor.getFullStreamInfo(song.id)

            if (info.streamUrl != null) {
                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setArtworkUri(Uri.parse(song.thumbnail))
                    .build()
                val mediaItem = MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(info.streamUrl)
                    .setMediaMetadata(mediaMetadata)
                    .build()

                controller?.let {
                    it.setMediaItem(mediaItem)
                    it.prepare()
                    it.play()
                }

                // Start background tasks after playback has started
                launch { processRecommendations(song, info) }
                prefetchNextSong()
            } else {
                android.util.Log.e("PlayerViewModel", "Could not get stream URL for song ${song.id}")
            }
        }
    }

    fun playPlaylist(playlistId: String) {
        viewModelScope.launch {
            showStatus("LOADING PLAYLIST...")
            val songs = youtubeClient.getPlaylistItems(playlistId)
            if (songs.isNotEmpty()) {
                playSong(songs.first(), songs)
            } else {
                showStatus("EMPTY PLAYLIST")
            }
        }
    }

    fun shufflePlaylist(playlistId: String) {
        viewModelScope.launch {
            showStatus("SHUFFLING PLAYLIST...")
            val songs = youtubeClient.getPlaylistItems(playlistId)
            if (songs.isNotEmpty()) {
                if (_shuffleMode.value == ShuffleMode.OFF) {
                    _shuffleMode.value = ShuffleMode.ON
                }
                val shuffled = songs.shuffled()
                playSong(shuffled.first(), shuffled)
            } else {
                showStatus("EMPTY PLAYLIST")
            }
        }
    }

    fun addToQueue(song: Song) {
        if (!_queue.value.any { it.id == song.id }) {
            _queue.value = _queue.value + song
            showStatus("ADDED TO QUEUE")
        } else {
            showStatus("ALREADY IN QUEUE")
        }
        if (!_originalQueue.value.any { it.id == song.id }) {
            _originalQueue.value = _originalQueue.value + song
        }
        updateRemainingQueue()
        prefetchNextSong()
    }

    fun seekTo(position: Long) {
        controller?.seekTo(position)
        _playbackPosition.value = position
    }

    fun togglePlayPause() {
        if (controller?.isPlaying == true) {
            controller?.pause()
        } else {
            controller?.play()
        }
    }

    fun skipToNext() {
        showStatus("SKIPPING NEXT")
        controller?.let { ctrl ->
            if (ctrl.mediaItemCount > 1 && ctrl.hasNextMediaItem()) {
                ctrl.seekToNext()
                return
            }
        }
        
        val currentList = _queue.value
        val currentIndex = currentList.indexOfFirst { it.id == _currentSong.value?.id }
        
        if (currentIndex < currentList.size - 1) {
            playSong(currentList[currentIndex + 1])
        } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && currentList.isNotEmpty()) {
            playSong(currentList.first())
        }
    }

    fun skipToPrevious() {
        if ((controller?.currentPosition ?: 0L) > 3000) {
            showStatus("RESTARTING TRACK")
            controller?.seekTo(0)
            return
        }

        showStatus("SKIPPING PREV")
        val currentList = _queue.value
        val currentIndex = currentList.indexOfFirst { it.id == _currentSong.value?.id }
        
        if (currentIndex > 0) {
            playSong(currentList[currentIndex - 1])
        } else {
            controller?.seekTo(0)
        }
    }

    fun toggleShuffle() {
        val newMode = when (_shuffleMode.value) {
            ShuffleMode.OFF -> ShuffleMode.ON
            ShuffleMode.ON -> ShuffleMode.SMART
            ShuffleMode.SMART -> ShuffleMode.OFF
        }
        _shuffleMode.value = newMode
        
        showStatus("SHUFFLE: $newMode")
        
        if (newMode == ShuffleMode.ON) {
            // Shuffle the remaining queue
            val currentSong = _currentSong.value
            val currentQueue = _queue.value
            val currentIndex = currentQueue.indexOfFirst { it.id == currentSong?.id }
            if (currentIndex != -1) {
                val before = currentQueue.take(currentIndex + 1)
                val after = currentQueue.drop(currentIndex + 1).shuffled()
                _queue.value = before + after
            }
        } else if (newMode == ShuffleMode.OFF) {
            // Restore original order
            _queue.value = _originalQueue.value
        } else if (newMode == ShuffleMode.SMART) {
            _currentSong.value?.let { current ->
                viewModelScope.launch {
                    val info = extractor.getFullStreamInfo(current.id)
                    processRecommendations(current, info)
                }
            }
        }
        updateRemainingQueue()
        prefetchNextSong()
    }

    fun toggleRepeat() {
        val newMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = newMode
        controller?.repeatMode = newMode

        val modeText = when(newMode) {
            Player.REPEAT_MODE_ONE -> "ONE"
            Player.REPEAT_MODE_ALL -> "ALL"
            else -> "OFF"
        }
        showStatus("REPEAT: $modeText")
        prefetchNextSong()
    }

    fun toggleLike() {
        val song = _currentSong.value ?: return
        val newLiked = !_isLiked.value
        _isLiked.value = newLiked
        
        showStatus(if (newLiked) "FAVORITED" else "UNFAVORITED")
        
        viewModelScope.launch {
            youtubeClient.rateVideo(song.id, if (newLiked) "like" else "none")
        }
    }

    fun removeFromQueue(songId: String) {
        _queue.value = _queue.value.filter { it.id != songId }
        _originalQueue.value = _originalQueue.value.filter { it.id != songId }
        updateRemainingQueue()
        prefetchNextSong()
    }

    fun clearQueue() {
        _queue.value = emptyList()
        _originalQueue.value = emptyList()
        updateRemainingQueue()
    }

    private suspend fun fetchSongMetadataUI(song: Song, updateState: Boolean = true) {
        if (_asciiArtCache.containsKey(song.id) && _dominantColorCache.containsKey(song.id)) {
            if (updateState) {
                _asciiArt.value = _asciiArtCache[song.id] ?: ""
                _dominantColor.value = _dominantColorCache[song.id] ?: Color(0xFF00FF41)
                if (_tuiSchemeName.value == "Dynamic") updateTuiColors()
            }
            return
        }

        val imageUrl = song.thumbnail ?: return
        withContext(Dispatchers.IO) {
            val loader = ImageLoader(getApplication())
            val request = ImageRequest.Builder(getApplication())
                .data(imageUrl)
                .allowHardware(false) // Required for Palette
                .build()

            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as BitmapDrawable).bitmap
                
                // Offload CPU intensive tasks to Default dispatcher
                val (ascii, color) = withContext(Dispatchers.Default) {
                    val asciiArt = TuiUtils.bitmapToAscii(bitmap, 64, 40)
                    val palette = Palette.from(bitmap).generate()
                    val colorInt = palette.getVibrantColor(palette.getMutedColor(0xFF121212.toInt()))
                    asciiArt to Color(colorInt)
                }

                _asciiArtCache[song.id] = ascii
                _dominantColorCache[song.id] = color

                if (updateState) {
                    _asciiArt.value = ascii
                    _dominantColor.value = color
                    if (_tuiSchemeName.value == "Dynamic") {
                        updateTuiColors()
                    }
                }
            }
        }
    }

    fun setTuiScheme(name: String) {
        _tuiSchemeName.value = name
        updateTuiColors()
        showStatus("SCHEME: $name")
    }

    fun setVolume(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _volume.value = clamped
        controller?.volume = clamped
        showStatus("VOLUME: ${(clamped * 100).toInt()}%")
    }

    fun showStatus(message: String) {
        statusJob?.cancel()
        _systemStatus.value = message
        statusJob = viewModelScope.launch {
            delay(3000)
            _systemStatus.value = ""
        }
    }

    private fun updateTuiColors() {
        _tuiColors.value = when (_tuiSchemeName.value) {
            "Amber" -> AmberColors
            "Cyberpunk" -> CyberpunkColors
            "Dynamic" -> {
                val dominant = _dominantColor.value
                TuiColors(
                    background = Color(0xFF0D0208),
                    surface = Color(0xFF0D0208),
                    primary = dominant,
                    onBackground = dominant,
                    onSurface = dominant,
                    scanlineColor = dominant.copy(alpha = 0.05f)
                )
            }
            else -> MatrixColors
        }
    }

    private fun updateVisualizer(enabled: Boolean) {
        if (enabled) {
            // Check for RECORD_AUDIO permission
            if (ContextCompat.checkSelfPermission(
                    getApplication(),
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                android.util.Log.w("PlayerViewModel", "Visualizer disabled: RECORD_AUDIO permission not granted")
                return
            }

            val sessionId = currentAudioSessionId
            if (sessionId <= 0) {
                android.util.Log.w("PlayerViewModel", "Visualizer sessionId is $sessionId")
                return 
            }
            
            if (visualizerThread == null) {
                visualizerThread = android.os.HandlerThread("VisualizerThread").apply { start() }
                visualizerHandler = android.os.Handler(visualizerThread!!.looper)
            }

            visualizerHandler?.post {
                if (visualizer == null || visualizerSessionId != sessionId) {
                    try {
                        android.util.Log.d("PlayerViewModel", "Initializing visualizer for session $sessionId")
                        visualizer?.release()
                        visualizer = Visualizer(sessionId).apply {
                            captureSize = Visualizer.getCaptureSizeRange()[1]
                            setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                                override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}

                                override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                                    if (fft != null) {
                                        val now = System.currentTimeMillis()
                                        if (now - lastVisualizerUpdate >= 33) { // ~30 FPS
                                            _visualizerMagnitudes.value = calculateMagnitudes(fft)
                                            lastVisualizerUpdate = now
                                        }
                                    }
                                }
                            }, Visualizer.getMaxCaptureRate(), false, true)
                            this.enabled = true
                        }
                        visualizerSessionId = sessionId
                        android.util.Log.d("PlayerViewModel", "Visualizer initialized successfully")
                    } catch (e: Exception) {
                        android.util.Log.e("PlayerViewModel", "Error initializing visualizer", e)
                    }
                } else {
                    try {
                        visualizer?.enabled = true
                    } catch (e: Exception) {
                        android.util.Log.e("PlayerViewModel", "Error enabling visualizer", e)
                        visualizer = null
                    }
                }
            }
        } else {
            visualizerHandler?.post {
                try {
                    visualizer?.enabled = false
                } catch (e: Exception) {
                    // Ignore errors on disable
                }
            }
            _visualizerMagnitudes.value = emptyList()
        }
    }

    private fun calculateMagnitudes(fft: ByteArray): List<Float> {
        val n = fft.size / 2
        val numBars = 80 
        
        // Cache bin indices as they only depend on n and numBars
        if (n != lastN || binCache == null || binCache!!.size != numBars) {
            val bins = mutableListOf<Pair<Int, Int>>()
            for (i in 0 until numBars) {
                val startBin = Math.pow(n.toDouble(), (i.toDouble() / numBars)).toInt().coerceIn(1, n - 1)
                val endBin = Math.pow(n.toDouble(), ((i + 1).toDouble() / numBars)).toInt().coerceIn(1, n - 1)
                bins.add(startBin to endBin)
            }
            binCache = bins
            lastN = n
        }

        val magnitudes = ArrayList<Float>(numBars)
        val currentBins = binCache!!

        for (i in 0 until numBars) {
            val (startBin, endBin) = currentBins[i]
            var maxMag = 0f
            
            for (j in startBin..endBin) {
                val r = fft[2 * j].toInt()
                val im = fft[2 * j + 1].toInt()
                // Fast magnitude approximation: |r| + |im|
                val mag = (if (r < 0) -r else r).toFloat() + (if (im < 0) -im else im).toFloat()
                if (mag > maxMag) maxMag = mag
            }

            // Normalize and scale. FFT values are signed bytes (-128 to 127).
            // Max |r| + |im| is ~254. Let's divide by 150 for decent sensitivity.
            val normalized = (maxMag / 150f).coerceIn(0f, 1f)
            val scaledMag = Math.sqrt(normalized.toDouble()).toFloat()
            magnitudes.add(scaledMag)
        }
        return magnitudes
    }

    override fun onCleared() {
        super.onCleared()
        visualizer?.release()
        visualizerThread?.quitSafely()
        controller?.removeListener(playerListener)
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
