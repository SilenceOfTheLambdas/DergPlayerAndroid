package com.silenceofthelambda.dergplayer.api

import android.util.Log
import android.util.LruCache
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

    private val cache = LruCache<String, FullStreamInfo>(20)

    suspend fun getFullStreamInfo(videoId: String): FullStreamInfo = withContext(Dispatchers.IO) {
        cache.get(videoId)?.let { return@withContext it }
        
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
                    // Filter out likely non-music content (e.g., videos > 10 mins)
                    // and prioritize tracks that look like music (from Topic channels)
                    val isLikelyMusic = item.uploaderName.endsWith("- Topic")
                    val durationLimit = if (isLikelyMusic) 1200 else 600 // More lenient for official music
                    
                    if (item.duration > durationLimit) return@mapNotNull null
                    if (item.duration <= 0) return@mapNotNull null // Filter out weird/live items if needed
                    
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
            
            val result = FullStreamInfo(streamUrl, relatedSongs)
            if (streamUrl != null) {
                cache.put(videoId, result)
            }
            result
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
