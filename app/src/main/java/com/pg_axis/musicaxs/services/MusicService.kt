package com.pg_axis.musicaxs.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
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
import com.pg_axis.musicaxs.settings.FavouritesSave
import com.pg_axis.musicaxs.settings.SettingsSave
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.core.net.toUri

enum class QueueSource { PLAYLIST, MANUAL }

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    // ── Queue state ───────────────────────────────────────────────────────────
    private var currentSource: QueueSource = QueueSource.MANUAL

    private val favourites by lazy { FavouritesSave.getInstance(applicationContext) }
    private val settings by lazy { SettingsSave.getInstance(applicationContext) }

    companion object {
        private var instance: MusicService? = null

        val COMMAND_SHUFFLE = SessionCommand("ACTION_SHUFFLE", Bundle.EMPTY)
        val COMMAND_LIKE = SessionCommand("ACTION_LIKE", Bundle.EMPTY)
        val COMMAND_PREVIOUS = SessionCommand("ACTION_PREVIOUS", Bundle.EMPTY)
        val COMMAND_NEXT = SessionCommand("ACTION_NEXT", Bundle.EMPTY)
        val currentUri: Uri? get() = instance?.mediaSession?.player?.currentMediaItem?.localConfiguration?.uri
        val queueState = MutableStateFlow<List<MediaItem>>(emptyList())
        val currentIndexState = MutableStateFlow(-1)

        var isShuffleOn = false
            private set
        var isLiked = false
            private set

        // ── Public queue API ──────────────────────────────────────────────────

        val playerInstance get() = instance?.mediaSession?.player

        fun seekTo(positionMs: Long) {
            instance?.mediaSession?.player?.seekTo(positionMs)
        }

        fun seekBy(offsetMs: Long) {
            instance?.mediaSession?.player?.let {
                it.seekTo((it.currentPosition + offsetMs).coerceAtLeast(0L))
            }
        }

        /**
         * Play a single song.
         * - PLAYLIST mode: clears queue, plays only this song, switches to MANUAL.
         * - MANUAL mode: inserts at index 0, plays from there, rest of queue follows.
         */
        fun playSingular(context: Context, song: Song, startPositionMs: Long = 0L) {
            instance?.playSingularInternal(song, startPositionMs) ?: run {
                val intent = Intent(context, MusicService::class.java).apply {
                    putExtra(EXTRA_URI, song.uri.toString())
                    putExtra(EXTRA_TITLE, song.title)
                    putExtra(EXTRA_ARTIST, song.artist)
                    putExtra(EXTRA_POSITION_MS, startPositionMs)
                }
                context.startForegroundService(intent)
            }
        }

        /**
         * Replace the current queue with a playlist and start playback from the first item.
         */
        fun replaceQueue(context: Context, songs: List<Song>) {
            if (songs.isEmpty()) return
            instance?.replaceQueueInternal(songs) ?: run {
                val intent = Intent(context, MusicService::class.java).apply {
                    putStringArrayListExtra(EXTRA_QUEUE_URIS, ArrayList(songs.map { it.uri.toString() }))
                    putStringArrayListExtra(EXTRA_QUEUE_TITLES, ArrayList(songs.map { it.title }))
                    putStringArrayListExtra(EXTRA_QUEUE_ARTISTS, ArrayList(songs.map { it.artist }))
                }
                context.startForegroundService(intent)
            }
        }

        /**
         * Append a song to the end of the current queue without interrupting playback.
         * Falls back to playSingular if the service isn't running.
         */
        fun addToQueue(context: Context, song: Song) {
            instance?.addToQueueInternal(song) ?: playSingular(context, song)
        }

        fun moveQueueItem(from: Int, to: Int) {
            instance?.mediaSession?.player?.moveMediaItem(from, to)
        }

        fun removeFromQueue(index: Int) {
            instance?.mediaSession?.player?.removeMediaItem(index)
        }

        fun initFromSettings(context: Context) {
            val settings = SettingsSave.getInstance(context)
            Log.d("Settings initialized", "Queue is initialized, ${settings.lastQueueUris}")
            if (settings.lastQueueUris.isNotEmpty()) {
                queueState.value = settings.lastQueueUris.map { uri ->
                    MediaItem.Builder().setUri(uri.toUri()).build()
                }
            }
        }

        fun initializeService(context: Context) {
            if (instance != null) return
            val intent = Intent(context, MusicService::class.java).apply {
                putExtra(EXTRA_INIT_ONLY, true)
            }
            context.startService(intent)
        }

        fun like(favourites: FavouritesSave, updateNotification: Boolean = true) {
            val inst = instance ?: return
            val player = instance?.mediaSession?.player

            player?.currentMediaItem?.localConfiguration?.uri?.let {
                isLiked = !isLiked
                favourites.toggle(it, isLiked)
                if (updateNotification) inst.mediaSession?.let { session -> inst.updateNotificationButtons(session) }
            }
        }

        fun previous() {
            val player = instance?.mediaSession?.player
            player?.currentPosition?.let {
                if (it > 3000) player.seekTo(0)
                else player.seekToPreviousMediaItem()
            }
        }

        fun next() {
            val player = instance?.mediaSession?.player
            player?.seekToNextMediaItem()
        }

        private const val EXTRA_URI = "extra_uri"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_ARTIST = "extra_artist"
        private const val EXTRA_POSITION_MS = "extra_position_ms"
        private const val EXTRA_QUEUE_URIS = "extra_queue_uris"
        private const val EXTRA_QUEUE_TITLES  = "extra_queue_titles"
        private const val EXTRA_QUEUE_ARTISTS = "extra_queue_artists"
        private const val EXTRA_INIT_ONLY = "extra_init_only"
    }

    // ── Internal queue operations ─────────────────────────────────────────────

    private fun Song.toMediaItem() = MediaItem.Builder()
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .build()
        )
        .build()

    private fun playSingularInternal(song: Song, startPositionMs: Long = 0L) {
        val player = mediaSession?.player ?: return

        when (currentSource) {
            QueueSource.PLAYLIST -> {
                currentSource = QueueSource.MANUAL
                player.setMediaItem(song.toMediaItem(), startPositionMs)
            }
            QueueSource.MANUAL -> {
                player.addMediaItem(0, song.toMediaItem())
                player.seekTo(0, startPositionMs)
            }
        }
        player.prepare()
        player.play()
    }

    private fun replaceQueueInternal(songs: List<Song>) {
        val player = mediaSession?.player ?: return
        currentSource = QueueSource.PLAYLIST
        player.setMediaItems(songs.map { it.toMediaItem() })
        player.prepare()
        player.play()
    }

    private fun addToQueueInternal(song: Song) {
        mediaSession?.player?.addMediaItem(song.toMediaItem())
    }

    private fun replaceQueueFromExtras(
        uris: List<String>,
        titles: List<String>,
        artists: List<String>
    ) {
        val player = mediaSession?.player ?: return
        val items = uris.mapIndexed { i, uri ->
            MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(titles.getOrElse(i) { "Unknown" })
                        .setArtist(artists.getOrElse(i) { "Unknown" })
                        .build()
                )
                .build()
        }
        currentSource = QueueSource.PLAYLIST
        player.setMediaItems(items)
        player.prepare()
        player.play()
    }

    // ── Session callback ──────────────────────────────────────────────────────

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
                COMMAND_SHUFFLE.customAction  -> {
                    isShuffleOn = !isShuffleOn
                    session.player.shuffleModeEnabled = isShuffleOn
                }
                COMMAND_LIKE.customAction -> like(favourites, false)
                COMMAND_PREVIOUS.customAction -> previous()
                COMMAND_NEXT.customAction -> next()
            }
            updateNotificationButtons(session)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        @Deprecated("Deprecated in Java")
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val lastUri = settings.lastSongUri
            if (lastUri.isEmpty()) {
                return Futures.immediateFailedFuture(
                    UnsupportedOperationException("No last song saved")
                )
            }

            val queueUris = settings.lastQueueUris.ifEmpty { listOf(lastUri) }
            val currentIndex = queueUris.indexOf(lastUri).coerceAtLeast(0)
            val items = queueUris.map { MediaItem.Builder().setUri(it.toUri()).build() }

            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    items,
                    currentIndex,
                    settings.lastPositionMs
                )
            )
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

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
                    settings.lastDurationMs = player.duration.coerceAtLeast(0L)
                    settings.save()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem ?: return
                val uri = mediaItem.localConfiguration?.uri ?: return
                isLiked = favourites.isFavourite(uri)
                currentIndexState.value = instance?.mediaSession?.player?.currentMediaItemIndex ?: -1
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                val player = instance?.mediaSession?.player ?: return
                val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }

                if (items.isEmpty()) return

                queueState.value = items
                currentIndexState.value = player.currentMediaItemIndex

                val settings = SettingsSave.getInstance(this@MusicService)
                settings.lastQueueUris = items.mapNotNull { it.localConfiguration?.uri?.toString() }
                settings.save()
            }
        })

        queueState.value = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        currentIndexState.value = player.currentMediaItemIndex

        updateNotificationButtons(mediaSession!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        intent?.let {
            if (it.getBooleanExtra(EXTRA_INIT_ONLY, false)) {
                val settings = SettingsSave.getInstance(this)
                val uris = settings.lastQueueUris
                if (uris.isNotEmpty()) {
                    val currentIndex = uris.indexOf(settings.lastSongUri).coerceAtLeast(0)
                    val items = uris.map { uri ->
                        MediaItem.Builder().setUri(uri.toUri()).build()
                    }
                    mediaSession?.player?.apply {
                        setMediaItems(items, currentIndex, settings.lastPositionMs)
                        prepare()
                    }
                }
                return START_NOT_STICKY
            }

            // Queue replacement via intent (service was not running)
            val queueUris = it.getStringArrayListExtra(EXTRA_QUEUE_URIS)
            if (queueUris != null) {
                val titles = it.getStringArrayListExtra(EXTRA_QUEUE_TITLES)  ?: arrayListOf()
                val artists = it.getStringArrayListExtra(EXTRA_QUEUE_ARTISTS) ?: arrayListOf()
                replaceQueueFromExtras(queueUris, titles, artists)
                return START_NOT_STICKY
            }

            // Single song (playSingular / cold start)
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
            settings.lastDurationMs = player.duration.coerceAtLeast(0L)
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

    // ── Helpers ───────────────────────────────────────────────────────────────

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
            CommandButton.Builder(
                if (isShuffleOn) CommandButton.ICON_SHUFFLE_ON
                else CommandButton.ICON_SHUFFLE_OFF
            ).setSessionCommand(COMMAND_SHUFFLE).setDisplayName("Shuffle").build(),

            CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setSessionCommand(COMMAND_PREVIOUS).setDisplayName("Previous").build(),

            CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(COMMAND_NEXT).setDisplayName("Next").build(),

            CommandButton.Builder(
                if (isLiked) CommandButton.ICON_HEART_FILLED
                else CommandButton.ICON_HEART_UNFILLED
            ).setSessionCommand(COMMAND_LIKE).setDisplayName("Like").build()
        )
        session.setMediaButtonPreferences(buttons)
    }
}