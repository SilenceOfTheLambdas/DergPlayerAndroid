package com.silenceofthelambda.dergplayer.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class MediaPlaybackService : MediaSessionService() {

    companion object {
        const val COMMAND_SKIP_NEXT = "com.silenceofthelambda.dergplayer.SKIP_NEXT"
        const val COMMAND_SKIP_PREV = "com.silenceofthelambda.dergplayer.SKIP_PREV"
        const val COMMAND_SET_AUDIO_SESSION_ID = "com.silenceofthelambda.dergplayer.SET_AUDIO_SESSION_ID"
    }

    private var mediaSession: MediaSession? = null
    private lateinit var player: Player
    private var exoPlayer: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        val p = ExoPlayer.Builder(this).build()
        exoPlayer = p
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        p.setAudioAttributes(audioAttributes, true)
        
        p.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                mediaSession?.connectedControllers?.forEach {
                    val extras = Bundle().apply {
                        putInt("audio_session_id", audioSessionId)
                    }
                    mediaSession?.sendCustomCommand(it, SessionCommand(COMMAND_SET_AUDIO_SESSION_ID, extras), extras)
                }
            }
        })
        
        // Wrap player in ForwardingPlayer to force skip commands to be available
        val forwardingPlayer = object : ForwardingPlayer(p) {
            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .build()
            }
        }
        player = forwardingPlayer
        
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(CustomMediaSessionCallback())
            .build()
    }

    private inner class CustomMediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(COMMAND_SKIP_NEXT, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SKIP_PREV, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SET_AUDIO_SESSION_ID, Bundle.EMPTY))
                .build()
            
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(availableSessionCommands)
                .build()
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            exoPlayer?.let {
                val extras = Bundle().apply {
                    putInt("audio_session_id", it.audioSessionId)
                }
                session.sendCustomCommand(controller, SessionCommand(COMMAND_SET_AUDIO_SESSION_ID, extras), extras)
            }
        }

        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            when (playerCommand) {
                Player.COMMAND_SEEK_TO_NEXT -> {
                    session.connectedControllers.forEach {
                        session.sendCustomCommand(it, SessionCommand(COMMAND_SKIP_NEXT, Bundle.EMPTY), Bundle.EMPTY)
                    }
                    return SessionResult.RESULT_SUCCESS
                }
                Player.COMMAND_SEEK_TO_PREVIOUS -> {
                    session.connectedControllers.forEach {
                        session.sendCustomCommand(it, SessionCommand(COMMAND_SKIP_PREV, Bundle.EMPTY), Bundle.EMPTY)
                    }
                    return SessionResult.RESULT_SUCCESS
                }
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
