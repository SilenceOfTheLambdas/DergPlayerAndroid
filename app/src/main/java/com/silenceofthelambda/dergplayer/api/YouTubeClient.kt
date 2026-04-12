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

            response.items.map { item ->
                Playlist(
                    id = item.id,
                    title = item.snippet.title,
                    count = item.contentDetails.itemCount,
                    thumbnail = item.snippet.thumbnails.default.url
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
