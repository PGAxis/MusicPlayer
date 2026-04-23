package com.pg_axis.musicaxs.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.pg_axis.musicaxs.models.Song
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import com.google.common.collect.ImmutableList
import com.pg_axis.musicaxs.settings.SettingsSave

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    companion object {
        private var instance: MusicService? = null

        val COMMAND_SHUFFLE = SessionCommand("ACTION_SHUFFLE", Bundle.EMPTY)
        val COMMAND_LIKE = SessionCommand("ACTION_LIKE", Bundle.EMPTY)
        val COMMAND_PREVIOUS = SessionCommand("ACTION_PREVIOUS", Bundle.EMPTY)
        val COMMAND_NEXT = SessionCommand("ACTION_NEXT", Bundle.EMPTY)

        // State — toggled by the buttons
        var isShuffleOn = false
            private set
        var isLiked = false
            private set

        fun play(context: Context, song: Song, startPositionMs: Long = 0L) {
            instance?.setAndPlay(song, startPositionMs) ?: run {
                val intent = Intent(context, MusicService::class.java).apply {
                    putExtra(EXTRA_URI, song.uri.toString())
                    putExtra(EXTRA_TITLE, song.title)
                    putExtra(EXTRA_ARTIST, song.artist)
                    putExtra(EXTRA_POSITION_MS, startPositionMs)
                }
                context.startForegroundService(intent)
            }
        }

        //fun pause() { instance?.mediaSession?.player?.pause() }
        //fun resume() { instance?.mediaSession?.player?.play() }
        //fun next() { instance?.mediaSession?.player?.seekToNextMediaItem() }
        fun previous() {
            val player = instance?.mediaSession?.player
            player?.currentPosition?.let {
                if (it > 3000) {
                    player.seekTo(0)
                } else {
                    player.seekToPreviousMediaItem()
                }
            }
        }

        //val isPlaying get() = instance?.mediaSession?.player?.isPlaying ?: false

        private const val EXTRA_URI = "extra_uri"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_ARTIST = "extra_artist"
        private const val EXTRA_POSITION_MS = "extra_position_ms"
    }

    @OptIn(UnstableApi::class)
    private inner class MusicSessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(COMMAND_SHUFFLE)
                .add(COMMAND_PREVIOUS)
                .add(COMMAND_NEXT)
                .add(COMMAND_LIKE)
                .build()

            val playerCommands = Player.Commands.Builder()
                .addAll(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                .remove(Player.COMMAND_SEEK_TO_NEXT)
                .build()

            return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                COMMAND_SHUFFLE.customAction -> {
                    isShuffleOn = !isShuffleOn
                    session.player.shuffleModeEnabled = isShuffleOn
                }
                COMMAND_LIKE.customAction -> {
                    isLiked = !isLiked
                    // TODO: persist liked state
                }
                COMMAND_PREVIOUS.customAction -> { previous() }
                COMMAND_NEXT.customAction -> session.player.seekToNextMediaItem()
            }
            updateNotificationButtons(session)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        @Deprecated("Deprecated in Java")
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val settings = SettingsSave.getInstance(this@MusicService)
            val lastUri = settings.lastSongUri

            if (lastUri.isEmpty()) {
                return Futures.immediateFailedFuture(
                    UnsupportedOperationException("No last song saved")
                )
            }

            val mediaItem = MediaItem.Builder()
                .setUri(lastUri)
                .build()

            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    listOf(mediaItem),
                    /* startIndex = */ 0,
                    /* startPositionMs = */ settings.lastPositionMs
                )
            )
        }
    }

    @OptIn(UnstableApi::class)
    private class CustomNotificationProvider(context: Context)
        : DefaultMediaNotificationProvider(context) {

        private var cachedButtons: ImmutableList<CommandButton> = ImmutableList.of()

        override fun getMediaButtons(
            session: MediaSession,
            playerCommands: Player.Commands,
            customLayout: ImmutableList<CommandButton>,
            showPauseButton: Boolean
        ): ImmutableList<CommandButton> {
            if (customLayout.isNotEmpty()) cachedButtons = customLayout
            return cachedButtons
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        setMediaNotificationProvider(CustomNotificationProvider(this))

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(MusicSessionCallback())
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateNotificationButtons(mediaSession!!)

                if (!isPlaying) {
                    val settings = SettingsSave.getInstance(this@MusicService)
                    settings.lastPositionMs = player.currentPosition
                    settings.save()
                }
            }
        })

        updateNotificationButtons(mediaSession!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        intent?.let {
            val uri = it.getStringExtra(EXTRA_URI) ?: return@let
            val title = it.getStringExtra(EXTRA_TITLE) ?: "Unknown"
            val artist = it.getStringExtra(EXTRA_ARTIST) ?: "Unknown"
            val position = it.getLongExtra(EXTRA_POSITION_MS, 0L)
            setAndPlay(uri, title, artist, position)
        }

        return START_NOT_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        mediaSession?.player?.let { player ->
            val settings = SettingsSave.getInstance(this)
            settings.lastPositionMs = player.currentPosition
            settings.save()
        }

        instance = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private fun setAndPlay(song: Song, startPositionMs: Long = 0L) = setAndPlay(
        uri = song.uri.toString(),
        title = song.title,
        artist = song.artist,
        startPositionMs = startPositionMs
    )

    private fun setAndPlay(uri: String, title: String, artist: String, startPositionMs: Long = 0L) {
        mediaSession?.player?.apply {
            setMediaItem(
                MediaItem.Builder()
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtist(artist)
                            .build()
                    )
                    .build(),
                startPositionMs
            )
            prepare()
            play()
        }
    }

    @OptIn(UnstableApi::class)
    private fun updateNotificationButtons(session: MediaSession) {
        val buttons = listOf(
            // Shuffle
            CommandButton.Builder(
                if (isShuffleOn) CommandButton.ICON_SHUFFLE_ON
                else CommandButton.ICON_SHUFFLE_OFF
            )
                .setSessionCommand(COMMAND_SHUFFLE)
                .setDisplayName("Shuffle")
                .build(),

            // Previous
            CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setSessionCommand(COMMAND_PREVIOUS)
                .setDisplayName("Previous")
                .build(),

            // Next
            CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(COMMAND_NEXT)
                .setDisplayName("Next")
                .build(),

            // Like
            CommandButton.Builder(
                if (isLiked) CommandButton.ICON_HEART_FILLED
                else CommandButton.ICON_HEART_UNFILLED
            )
                .setSessionCommand(COMMAND_LIKE)
                .setDisplayName("Like")
                .build()
        )

        session.setMediaButtonPreferences(buttons)
    }
}