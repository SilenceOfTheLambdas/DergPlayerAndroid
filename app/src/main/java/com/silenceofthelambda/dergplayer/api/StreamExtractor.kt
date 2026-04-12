package com.silenceofthelambda.dergplayer.api

import android.util.Log
import com.silenceofthelambda.dergplayer.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class StreamExtractor {

    data class FullStreamInfo(
        val streamUrl: String?,
        val relatedSongs: List<Song>
    )

    suspend fun getFullStreamInfo(videoId: String): FullStreamInfo = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            val url = "https://www.youtube.com/watch?v=$videoId"
            val streamInfo = StreamInfo.getInfo(service, url)
            
            // Prefer audio-only streams for music
            val audioStreams = streamInfo.audioStreams
            val streamUrl = if (audioStreams.isNotEmpty()) {
                audioStreams[0].content
            } else {
                // Fallback to video streams
                streamInfo.videoStreams.firstOrNull()?.content
            }
            
            val relatedItems = streamInfo.relatedItems
            val relatedSongs = relatedItems.mapNotNull { item ->
                if (item is StreamInfoItem) {
                    // Filter out non-music content (e.g., videos > 10 mins)
                    if (item.duration > 600) return@mapNotNull null
                    
                    Song(
                        id = item.url.substringAfter("v=").substringBefore("&"),
                        title = item.name,
                        artist = item.uploaderName,
                        duration = formatDuration(item.duration),
                        thumbnail = item.thumbnails.firstOrNull()?.url ?: "",
                        publishedAt = null
                    )
                } else null
            }
            
            FullStreamInfo(streamUrl, relatedSongs)
        } catch (e: Exception) {
            Log.e("StreamExtractor", "Error fetching info for $videoId: ${e.message}", e)
            FullStreamInfo(null, emptyList())
        }
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "0:00"
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }
}
