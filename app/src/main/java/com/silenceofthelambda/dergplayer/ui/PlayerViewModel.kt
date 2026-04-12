package com.silenceofthelambda.dergplayer.ui

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.audiofx.Visualizer
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.palette.graphics.Palette
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
    private val _player = ExoPlayer.Builder(application).build()
    val player: Player = _player

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

    init {
        _player.addListener(object : Player.Listener {
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
                    _duration.value = _player.duration
                } else if (playbackState == Player.STATE_ENDED) {
                    onSongEnded()
                }
            }
        })
        currentAudioSessionId = _player.audioSessionId
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (true) {
                _playbackPosition.value = _player.currentPosition
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

    private fun onSongEnded() {
        when (_repeatMode.value) {
            Player.REPEAT_MODE_ONE -> {
                _player.seekTo(0)
                _player.play()
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
            _relatedSongs.value = info.relatedSongs
            
            // Add related songs to queue if smart shuffle is enabled
            if (_shuffleMode.value == ShuffleMode.SMART) {
                val currentIds = _queue.value.map { it.id }.toSet()
                val newRelated = info.relatedSongs.filterNot { it.id in currentIds }
                _queue.value = _queue.value + newRelated
            }
            
            if (info.streamUrl != null) {
                val mediaItem = MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(info.streamUrl)
                    .build()

                _player.setMediaItem(mediaItem)
                _player.prepare()
                _player.play()
            } else {
                android.util.Log.e("PlayerViewModel", "Could not get stream URL for song ${song.id}")
            }
            updateRemainingQueue()
        }
    }

    fun seekTo(position: Long) {
        _player.seekTo(position)
        _playbackPosition.value = position
    }

    fun togglePlayPause() {
        if (_player.isPlaying) {
            _player.pause()
        } else {
            _player.play()
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
        if (_player.currentPosition > 3000) {
            showStatus("RESTARTING TRACK")
            _player.seekTo(0)
            return
        }

        showStatus("SKIPPING PREV")
        val currentList = _queue.value
        val currentIndex = currentList.indexOfFirst { it.id == _currentSong.value?.id }
        
        if (currentIndex > 0) {
            playSong(currentList[currentIndex - 1])
        } else {
            _player.seekTo(0)
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
        _player.volume = clamped
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

            val sessionId = if (currentAudioSessionId == 0) _player.audioSessionId else currentAudioSessionId
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
        // FFT format: [real DC, real Nyquist, real bin1, imag bin1, real bin2, imag bin2, ...]
        val n = (fft.size / 2).coerceAtMost(64)
        val magnitudes = mutableListOf<Float>()
        
        for (i in 1 until n) { // Skip DC and Nyquist for visual simplicity
            val r = fft[2 * i].toInt()
            val im = fft[2 * i + 1].toInt()
            val mag = Math.hypot(r.toDouble(), im.toDouble()).toFloat()
            
            // Normalize and scale. FFT values are signed bytes (-128 to 127).
            // hypot(128, 128) is ~181.
            // Using a more aggressive scaling and sqrt for better visual response.
            val normalized = (mag / 100f).coerceIn(0f, 1f)
            val scaledMag = Math.sqrt(normalized.toDouble()).toFloat()
            
            magnitudes.add(scaledMag)
        }
        return magnitudes
    }

    override fun onCleared() {
        super.onCleared()
        visualizer?.release()
        _player.release()
    }
}
