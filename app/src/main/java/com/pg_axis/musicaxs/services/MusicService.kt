package com.pg_axis.musicaxs.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
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
import com.pg_axis.musicaxs.settings.PlayCountTracker
import com.pg_axis.musicaxs.settings.ShuffleSave
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class QueueSource { PLAYLIST, MANUAL }

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    // -- Queue state
    private var currentSource: QueueSource = QueueSource.MANUAL

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var playCountJob: Job? = null
    private var playStartTime: Long = 0L

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
        val isPlayingState = MutableStateFlow(false)
        val currentIndexState = MutableStateFlow(-1)

        var isShuffleOn = false
            private set
        var isLiked = false
            private set

        // -- Public queue API

        val playerInstance get() = instance?.mediaSession?.player

        val isShuffled get() = ShuffleSave.getInstance(instance!!).isShuffled

        fun toggleShuffle(context: Context) {
            val save = ShuffleSave.getInstance(context)
            val player = instance?.mediaSession?.player ?: return

            if (!save.isShuffled) {
                val currentUris = (0 until player.mediaItemCount)
                    .map { player.getMediaItemAt(it).localConfiguration?.uri?.toString() ?: "" }
                save.setOriginalQueue(currentUris)
                save.updateShuffled(true)

                isShuffleOn = true

                val shuffled = currentUris.shuffled()
                applyQueueReorder(shuffled)
            } else {
                // turning off — restore original
                save.updateShuffled(false)
                isShuffleOn = false

                val original = save.getOriginalQueue()
                applyQueueReorder(original)
            }

            instance?.mediaSession?.let { instance?.updateNotificationButtons(it) }
        }

        fun seekTo(positionMs: Long) {
            instance?.mediaSession?.player?.seekTo(positionMs)
        }

        fun setRepeatMode(repeatMode: Int) {
            instance?.mediaSession?.player?.repeatMode = repeatMode
            instance?.setRepeatInternal(repeatMode)
        }

        fun seekBy(offsetMs: Long) {
            instance?.mediaSession?.player?.let {
                it.seekTo((it.currentPosition + offsetMs).coerceAtLeast(0L))
            }
        }

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

        fun addToQueue(context: Context, song: Song) {
            val save = ShuffleSave.getInstance(context)
            if (save.isShuffled) save.addToOriginal(song.uri.toString())
            instance?.addToQueueInternal(song) ?: playSingular(context, song)
        }

        fun moveQueueItem(from: Int, to: Int) {
            instance?.mediaSession?.player?.moveMediaItem(from, to)
        }

        fun removeFromQueue(index: Int) {
            val player = instance?.mediaSession?.player ?: return
            val uri = player.getMediaItemAt(index).localConfiguration?.uri?.toString()
            if (uri != null && ShuffleSave.getInstance(instance!!).isShuffled) {
                ShuffleSave.getInstance(instance!!).removeFromOriginal(uri)
            }
            player.removeMediaItem(index)
        }

        private fun applyQueueReorder(targetUris: List<String>) {
            val player = instance?.mediaSession?.player ?: return

            // Mirror of the current player queue
            val current = (0 until player.mediaItemCount)
                .map { player.getMediaItemAt(it).localConfiguration?.uri?.toString() ?: "" }
                .toMutableList()

            for (targetIndex in targetUris.indices) {
                val targetUri = targetUris[targetIndex]

                // Find first occurrence at or after targetIndex
                var currentIndex = -1
                for (i in targetIndex until current.size) {
                    if (current[i] == targetUri) {
                        currentIndex = i
                        break
                    }
                }

                if (currentIndex == -1 || currentIndex == targetIndex) continue

                player.moveMediaItem(currentIndex, targetIndex)
                current.add(targetIndex, current.removeAt(currentIndex))
            }
        }

        fun reorderQueue(from: Int, to: Int) {
            instance?.mediaSession?.player?.moveMediaItem(from, to)
        }

        fun initFromSettings(context: Context) {
            val settings = SettingsSave.getInstance(context)
            if (settings.lastQueueUris.isNotEmpty()) {
                queueState.value = settings.lastQueueUris.mapIndexed { i, uri ->
                    MediaItem.Builder()
                        .setUri(uri.toUri())
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(settings.lastQueueTitles.getOrElse(i) { "" })
                                .setArtist(settings.lastQueueArtists.getOrElse(i) { "" })
                                .build()
                        )
                        .build()
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
            val player = instance?.mediaSession?.player ?: return
            val isFirst = player.currentMediaItemIndex == 0

            if (player.currentPosition > 3000) player.seekTo(0)
            else if (player.repeatMode != Player.REPEAT_MODE_ALL && isFirst) player.seekTo(player.mediaItemCount - 1, 0)
            else player.seekToPreviousMediaItem()
        }

        fun next() {
            val player = instance?.mediaSession?.player ?: return
            val isLast = player.currentMediaItemIndex == player.mediaItemCount - 1

            if (player.repeatMode != Player.REPEAT_MODE_ALL && isLast) player.seekTo(0, 0)
            else player.seekToNextMediaItem()
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

    // -- Internal queue operations

    private fun Song.toMediaItem() = MediaItem.Builder()
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .build()
        )
        .build()

    private fun setRepeatInternal(repeatMode: Int) {
        settings.repeatMode = repeatMode
        settings.save()
    }

    private fun playSingularInternal(song: Song, startPositionMs: Long = 0L) {
        val player = mediaSession?.player ?: return

        when (currentSource) {
            QueueSource.PLAYLIST -> {
                currentSource = QueueSource.MANUAL
                settings.queueSource = QueueSource.MANUAL
                settings.save()
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
        settings.queueSource = QueueSource.PLAYLIST
        settings.save()

        val save = ShuffleSave.getInstance(this)
        save.updateShuffled(false)
        save.setOriginalQueue(songs.map { it.uri.toString() })

        player.setMediaItems(songs.map { it.toMediaItem() })
        player.prepare()
        player.play()
    }

    private fun addToQueueInternal(song: Song) {
        mediaSession?.player?.addMediaItem(song.toMediaItem())
        if (currentSource == QueueSource.PLAYLIST) {
            currentSource = QueueSource.MANUAL
            settings.queueSource = QueueSource.MANUAL
            settings.save()
        }
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
        settings.queueSource = QueueSource.PLAYLIST
        settings.save()
        player.setMediaItems(items)
        player.prepare()
        player.play()
    }

    // -- Session callback

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
                .build()

            return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
        }

        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }

            when (keyEvent?.keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    if (keyEvent.action == KeyEvent.ACTION_DOWN) next()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    if (keyEvent.action == KeyEvent.ACTION_DOWN) previous()
                    return true
                }
            }

            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                COMMAND_SHUFFLE.customAction  -> toggleShuffle(this@MusicService)
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
            val items = queueUris.mapIndexed { i, uri ->
                MediaItem.Builder()
                    .setUri(uri.toUri())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(settings.lastQueueTitles.getOrElse(i) { "" })
                            .setArtist(settings.lastQueueArtists.getOrElse(i) { "" })
                            .build()
                    )
                    .build()
            }

            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    items,
                    currentIndex,
                    settings.lastPositionMs
                )
            )
        }
    }

    // -- Notification

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

    // -- Lifecycle

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
                isPlayingState.value = isPlaying
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
                updateNotificationButtons(mediaSession!!)

                playCountJob?.cancel()
                playStartTime = 0L
                playCountJob = serviceScope.launch {
                    while (true) {
                        delay(500)
                        val player = instance?.mediaSession?.player ?: return@launch
                        if (player.isPlaying) {
                            playStartTime += 500
                            if (playStartTime >= 5000) {
                                PlayCountTracker.getInstance(applicationContext).recordPlay(uri)
                                break
                            }
                        }
                    }
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                val player = instance?.mediaSession?.player ?: return
                val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }

                if (items.isEmpty()) return

                queueState.value = items
                currentIndexState.value = player.currentMediaItemIndex

                val settings = SettingsSave.getInstance(this@MusicService)
                settings.lastQueueUris = items.mapNotNull { it.localConfiguration?.uri?.toString() }
                settings.lastQueueTitles = items.map { it.mediaMetadata.title?.toString() ?: "" }
                settings.lastQueueArtists = items.map { it.mediaMetadata.artist?.toString() ?: "" }
                settings.save()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && player.repeatMode == Player.REPEAT_MODE_OFF) {
                    player.seekTo(0, 0)
                    player.pause()
                }
            }
        })

        queueState.value = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        currentIndexState.value = player.currentMediaItemIndex
        currentSource = settings.queueSource
        player.repeatMode = settings.repeatMode
        isShuffleOn = ShuffleSave.getInstance(this).isShuffled

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
                    val items = uris.mapIndexed { i, uri ->
                        MediaItem.Builder()
                            .setUri(uri.toUri())
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(settings.lastQueueTitles.getOrElse(i) { "Unknown" })
                                    .setArtist(settings.lastQueueArtists.getOrElse(i) { "Unknown" })
                                    .build()
                            )
                            .build()
                    }
                    mediaSession?.player?.apply {
                        setMediaItems(items, currentIndex, settings.lastPositionMs)
                        prepare()
                    }
                    mediaSession?.player?.repeatMode = settings.repeatMode
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
        serviceScope.cancel()
        super.onDestroy()
    }

    // -- Helpers

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