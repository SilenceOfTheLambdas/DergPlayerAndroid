package com.silenceofthelambda.dergplayer.ui

import android.app.Application
import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.audiofx.Visualizer
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
import com.google.common.util.concurrent.ListenableFuture
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.silenceofthelambda.dergplayer.api.YouTubeClient
import com.silenceofthelambda.dergplayer.api.StreamExtractor
import com.silenceofthelambda.dergplayer.model.Song
import com.silenceofthelambda.dergplayer.model.ShuffleMode
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

    private val playerListener = object : Player.Listener {
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
    }

    init {
        val sessionToken = SessionToken(application, ComponentName(application, com.silenceofthelambda.dergplayer.service.MediaPlaybackService::class.java))
        val future = MediaController.Builder(application, sessionToken).buildAsync()
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
                
                if (ctrl.isPlaying) {
                    startPositionUpdates()
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

    fun playSong(song: Song, contextQueue: List<Song> = emptyList()) {
        viewModelScope.launch {
            _currentSong.value = song
            _relatedSongs.value = emptyList()
            _isLiked.value = false

            launch {
                val rating = youtubeClient.getVideoRating(song.id)
                _isLiked.value = rating == "like"
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

            song.thumbnail?.let { updateDominantColor(it) }
            
            val info = extractor.getFullStreamInfo(song.id)
            
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
            
            _relatedSongs.value = recommendations
            
            // Add related songs to queue if smart shuffle is enabled
            if (_shuffleMode.value == ShuffleMode.SMART) {
                val currentIds = _queue.value.map { it.id }.toSet()
                // Limit to 3 recommendations as requested
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
                    showStatus("SMART: +${newRelated.size} SONGS")
                }
            }
            
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
            } else {
                android.util.Log.e("PlayerViewModel", "Could not get stream URL for song ${song.id}")
            }
            updateRemainingQueue()
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
        }
        updateRemainingQueue()
    }

    fun toggleRepeat() {
        val newMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = newMode

        val modeText = when(newMode) {
            Player.REPEAT_MODE_ONE -> "ONE"
            Player.REPEAT_MODE_ALL -> "ALL"
            else -> "OFF"
        }
        showStatus("REPEAT: $modeText")
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
    }

    fun clearQueue() {
        _queue.value = emptyList()
        _originalQueue.value = emptyList()
        updateRemainingQueue()
    }

    private suspend fun updateDominantColor(imageUrl: String) {
        withContext(Dispatchers.IO) {
            val loader = ImageLoader(getApplication())
            val request = ImageRequest.Builder(getApplication())
                .data(imageUrl)
                .allowHardware(false) // Required for Palette
                .build()

            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as BitmapDrawable).bitmap
                
                // Generate ASCII art
                val ascii = TuiUtils.bitmapToAscii(bitmap, 64, 40)
                _asciiArt.value = ascii

                val palette = Palette.from(bitmap).generate()
                val color = palette.getVibrantColor(palette.getMutedColor(0xFF121212.toInt()))
                _dominantColor.value = Color(color)
                if (_tuiSchemeName.value == "Dynamic") {
                    updateTuiColors()
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
            if (sessionId == 0) {
                android.util.Log.w("PlayerViewModel", "Visualizer sessionId is 0")
                return 
            }
            
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
                                    _visualizerMagnitudes.value = calculateMagnitudes(fft)
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
                    // If it failed to enable, maybe it was released or permission issue, try re-init next time
                    visualizer = null
                }
            }
        } else {
            try {
                visualizer?.enabled = false
            } catch (e: Exception) {
                // Ignore errors on disable
            }
            _visualizerMagnitudes.value = emptyList()
        }
    }

    private fun calculateMagnitudes(fft: ByteArray): List<Float> {
        val n = fft.size / 2
        val magnitudes = mutableListOf<Float>()
        val numBars = 80 // Increased from 64 for more detail

        for (i in 0 until numBars) {
            // Logarithmic distribution to cover more frequencies across the audible spectrum
            val startBin = Math.pow(n.toDouble(), (i.toDouble() / numBars)).toInt().coerceIn(1, n - 1)
            val endBin = Math.pow(n.toDouble(), ((i + 1).toDouble() / numBars)).toInt().coerceIn(1, n - 1)

            var maxMag = 0f
            // Use the maximum magnitude in the range for a more dynamic feel
            for (j in startBin..endBin) {
                val r = fft[2 * j].toInt()
                val im = fft[2 * j + 1].toInt()
                val mag = Math.hypot(r.toDouble(), im.toDouble()).toFloat()
                if (mag > maxMag) maxMag = mag
            }

            // Normalize and scale. FFT values are signed bytes (-128 to 127).
            // hypot(128, 128) is ~181. Using 100f as divisor for sensitivity.
            val normalized = (maxMag / 100f).coerceIn(0f, 1f)
            val scaledMag = Math.sqrt(normalized.toDouble()).toFloat()
            magnitudes.add(scaledMag)
        }
        return magnitudes
    }

    override fun onCleared() {
        super.onCleared()
        visualizer?.release()
        controller?.removeListener(playerListener)
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
