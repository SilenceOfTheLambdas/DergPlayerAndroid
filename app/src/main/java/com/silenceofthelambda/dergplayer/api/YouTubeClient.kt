package com.silenceofthelambda.dergplayer.api

import android.content.Context
import android.content.Intent
import com.silenceofthelambda.dergplayer.model.Playlist
import com.silenceofthelambda.dergplayer.model.Song
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.PlaylistItem
import com.google.api.services.youtube.model.SearchResult
import com.google.api.services.youtube.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

class YouTubeClient(private val context: Context, private val credential: GoogleAccountCredential) {
    private val _authRecoveryIntents = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val authRecoveryIntents = _authRecoveryIntents.asSharedFlow()

    private val youtube: YouTube by lazy {
        YouTube.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("DergPlayer")
            .build()
    }

    suspend fun getPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        try {
            val response = youtube.playlists().list(listOf("snippet", "contentDetails"))
                .setMine(true)
                .setMaxResults(50L)
                .execute()

            val playlists = response.items.map { item ->
                Playlist(
                    id = item.id,
                    title = item.snippet.title,
                    count = item.contentDetails.itemCount,
                    thumbnail = item.snippet.thumbnails.default.url
                )
            }.toMutableList()

            // Fetch Liked Music system playlist info
            var likedCount = 0L
            var likedId = "LL"
            val possibleLikedIds = listOf("VLLM", "LM", "LL")

            for (id in possibleLikedIds) {
                try {
                    // Get the count from playlistItems
                    val likedPlaylistResponse = youtube.playlistItems().list(listOf("id"))
                        .setPlaylistId(id)
                        .setMaxResults(1L)
                        .execute()
                    likedId = id
                    likedCount = likedPlaylistResponse.pageInfo.totalResults.toLong()
                    break
                } catch (e: Exception) {
                    // Try next ID
                }
            }

            // Add Liked Music system playlist
            playlists.add(0, Playlist(
                id = likedId,
                title = "Liked Music",
                count = likedCount,
                thumbnail = "https://www.gstatic.com/youtube/media/ytm/images/p_liked_songs.png"
            ))

            playlists
        } catch (e: UserRecoverableAuthIOException) {
            _authRecoveryIntents.tryEmit(e.intent)
            emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun filterMusic(songs: List<Song>): List<Song> = withContext(Dispatchers.IO) {
        if (songs.isEmpty()) return@withContext emptyList()
        try {
            // Get video details to check category
            val videoIds = songs.map { it.id }
            val response = youtube.videos().list(listOf("snippet"))
                .setId(videoIds)
                .execute()
            
            val musicVideoIds = response.items
                .filter { it.snippet.categoryId == "10" } // 10 is Music
                .map { it.id }
                .toSet()
            
            songs.filter { it.id in musicVideoIds }
        } catch (e: Exception) {
            e.printStackTrace()
            songs // Return original if error to avoid losing all recommendations
        }
    }

    suspend fun getRelatedVideosFromRadio(videoId: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            // "RD" + videoId is the standard Mix/Radio playlist ID
            val playlistId = "RD$videoId"
            val response = youtube.playlistItems().list(listOf("snippet", "contentDetails"))
                .setPlaylistId(playlistId)
                .setMaxResults(25L)
                .execute()

            val videoIds = response.items.map { it.contentDetails.videoId }
            if (videoIds.isEmpty()) return@withContext emptyList()

            val videoDetailsResponse = youtube.videos().list(listOf("snippet", "contentDetails"))
                .setId(videoIds)
                .execute()

            val videoDetails = videoDetailsResponse.items.associateBy { it.id }

            response.items.mapNotNull { item ->
                val vId = item.contentDetails.videoId
                val details = videoDetails[vId] ?: return@mapNotNull null
                Song(
                    id = vId,
                    title = item.snippet.title,
                    artist = cleanArtist(details.snippet.channelTitle),
                    duration = parseDuration(details.contentDetails.duration),
                    thumbnail = item.snippet.thumbnails.default?.url,
                    publishedAt = item.snippet.publishedAt.toString(),
                    playlistTitle = "Radio"
                )
            }
        } catch (e: Exception) {
            // If RD playlist fails or is unsupported (can happen via API), return empty
            emptyList()
        }
    }

    suspend fun getPlaylistItems(playlistId: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val response = youtube.playlistItems().list(listOf("snippet", "contentDetails"))
                .setPlaylistId(playlistId)
                .setMaxResults(50L)
                .execute()

            val videoIds = response.items.map { it.contentDetails.videoId }
            if (videoIds.isEmpty()) return@withContext emptyList()

            val videoDetailsResponse = youtube.videos().list(listOf("snippet", "contentDetails"))
                .setId(videoIds)
                .execute()

            val videoDetails = videoDetailsResponse.items.associateBy { it.id }

            response.items.mapNotNull { item ->
                val vId = item.contentDetails.videoId
                val details = videoDetails[vId] ?: return@mapNotNull null
                
                // If it's the Liked Videos playlist (LL), strictly filter for music (ID 10)
                if (playlistId == "LL" && details.snippet.categoryId != "10") {
                    return@mapNotNull null
                }

                Song(
                    id = vId,
                    title = item.snippet.title,
                    artist = cleanArtist(details.snippet.channelTitle),
                    duration = parseDuration(details.contentDetails.duration),
                    thumbnail = item.snippet.thumbnails.default?.url,
                    publishedAt = item.snippet.publishedAt.toString()
                )
            }
        } catch (e: UserRecoverableAuthIOException) {
            _authRecoveryIntents.tryEmit(e.intent)
            emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val response = youtube.search().list(listOf("snippet"))
                .setQ(query)
                .setType(listOf("video"))
                .setMaxResults(20L)
                .execute()

            val videoIds = response.items.map { it.id.videoId }
            if (videoIds.isEmpty()) return@withContext emptyList()

            val videoDetailsResponse = youtube.videos().list(listOf("snippet", "contentDetails"))
                .setId(videoIds)
                .execute()

            val durations = videoDetailsResponse.items.associate { it.id to it.contentDetails.duration }

            response.items.map { item ->
                val vId = item.id.videoId
                Song(
                    id = vId,
                    title = item.snippet.title,
                    artist = cleanArtist(item.snippet.channelTitle),
                    duration = parseDuration(durations[vId] ?: "PT0S"),
                    thumbnail = item.snippet.thumbnails.default.url,
                    publishedAt = item.snippet.publishedAt.toString(),
                    playlistTitle = "YouTube Music"
                )
            }
        } catch (e: UserRecoverableAuthIOException) {
            _authRecoveryIntents.tryEmit(e.intent)
            emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun rateVideo(videoId: String, rating: String) = withContext(Dispatchers.IO) {
        try {
            youtube.videos().rate(videoId, rating).execute()
        } catch (e: UserRecoverableAuthIOException) {
            _authRecoveryIntents.tryEmit(e.intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getVideoRating(videoId: String): String = withContext(Dispatchers.IO) {
        try {
            val response = youtube.videos().getRating(listOf(videoId)).execute()
            response.items.firstOrNull()?.rating ?: "none"
        } catch (e: UserRecoverableAuthIOException) {
            _authRecoveryIntents.tryEmit(e.intent)
            "none"
        } catch (e: Exception) {
            e.printStackTrace()
            "none"
        }
    }

    private fun cleanArtist(artist: String): String {
        return artist.replace(Regex("\\s*-\\s*Topic$"), "")
    }

    private fun parseDuration(durationStr: String): String {
        val regex = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?")
        val match = regex.matchEntire(durationStr) ?: return "0:00"
        
        val hours = match.groups[1]?.value?.toInt() ?: 0
        val minutes = match.groups[2]?.value?.toInt() ?: 0
        val seconds = match.groups[3]?.value?.toInt() ?: 0
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}
