package com.silenceofthelambda.dergplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.Dispatchers
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

    private val _relatedSongs = MutableStateFlow<List<Song>>(emptyList())
    val relatedSongs: StateFlow<List<Song>> = _relatedSongs

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val _dominantColor = MutableStateFlow<Color>(Color.Black)
    val dominantColor: StateFlow<Color> = _dominantColor

    init {
        _player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = _player.duration
                } else if (playbackState == Player.STATE_ENDED) {
                    onSongEnded()
                }
            }
        })

        viewModelScope.launch {
            while (true) {
                if (_player.isPlaying) {
                    _playbackPosition.value = _player.currentPosition
                }
                kotlinx.coroutines.delay(1000)
            }
        }
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
        return currentIdx < _queue.value.size - 1 || _relatedSongs.value.isNotEmpty()
    }

    fun playSong(song: Song, contextQueue: List<Song> = emptyList()) {
        viewModelScope.launch {
            _currentSong.value = song
            _relatedSongs.value = emptyList()
            
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
            
            // Add related songs to queue if smart shuffle is enabled or queue is small
            if (_shuffleMode.value == ShuffleMode.SMART || _queue.value.size <= 1) {
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
        val currentList = _queue.value
        val currentIndex = currentList.indexOfFirst { it.id == _currentSong.value?.id }
        
        if (currentIndex < currentList.size - 1) {
            playSong(currentList[currentIndex + 1])
        } else if (_relatedSongs.value.isNotEmpty()) {
            // If at end of queue, but we have recommendations, play a random one or first
            val nextSong = if (_shuffleMode.value != ShuffleMode.OFF) {
                _relatedSongs.value.random()
            } else {
                _relatedSongs.value.first()
            }
            playSong(nextSong)
        } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && currentList.isNotEmpty()) {
            playSong(currentList.first())
        }
    }

    fun skipToPrevious() {
        if (_player.currentPosition > 3000) {
            _player.seekTo(0)
            return
        }

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
        _repeatMode.value = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
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
                val palette = Palette.from(bitmap).generate()
                val color = palette.getVibrantColor(palette.getMutedColor(0xFF121212.toInt()))
                _dominantColor.value = Color(color)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        _player.release()
    }
}
