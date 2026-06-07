package dev.pgaxis.musicaxs.services

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
import dev.pgaxis.musicaxs.models.Song
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import com.google.common.collect.ImmutableList
import dev.pgaxis.musicaxs.settings.FavouritesSave
import dev.pgaxis.musicaxs.settings.SettingsSave
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.core.net.toUri
import dev.pgaxis.musicaxs.repositories.SongRepository
import dev.pgaxis.musicaxs.settings.PlayCountTracker
import dev.pgaxis.musicaxs.settings.ShuffleSave
import dev.pgaxis.musicaxs.settings.SettingsSave.QueueEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class QueueSource { PLAYLIST, MANUAL }

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    // -- Queue state
    private var currentSource: QueueSource = QueueSource.MANUAL

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var playCountJob: Job? = null
    private var timelineSaveJob: Job? = null
    private var playStartTime: Long = 0L

    private val favourites by lazy { FavouritesSave.getInstance(applicationContext) }
    private val settings by lazy { SettingsSave.getInstance(applicationContext) }
    private val songRepo = SongRepository.getInstance()

    companion object {
        private var instance: MusicService? = null

        val COMMAND_SHUFFLE = SessionCommand("ACTION_SHUFFLE", Bundle.EMPTY)
        val COMMAND_LIKE = SessionCommand("ACTION_LIKE", Bundle.EMPTY)
        val COMMAND_PREVIOUS = SessionCommand("ACTION_PREVIOUS", Bundle.EMPTY)
        val COMMAND_NEXT = SessionCommand("ACTION_NEXT", Bundle.EMPTY)
        val currentUri: Uri? get() = instance?.mediaSession?.player?.currentMediaItem?.localConfiguration?.uri
        val currentUriState = MutableStateFlow<Uri?>(null)
        val queueState = MutableStateFlow<List<MediaItem>>(emptyList())
        val isPlayingState = MutableStateFlow(false)
        val currentIndexState = MutableStateFlow(-1)

        var isShuffleOn = false
            private set
        var isLiked = false
            private set

        // -- Public queue API

        val playerInstance get() = instance?.mediaSession?.player

        val isShuffled get() = instance?.let { ShuffleSave.getInstance(it).isShuffled } ?: false

        fun toggleShuffle(context: Context) {
            val save = ShuffleSave.getInstance(context)
            val player = instance?.mediaSession?.player ?: return

            if (!save.isShuffled) {
                val currentUris = (0 until player.mediaItemCount)
                    .map { player.getMediaItemAt(it).localConfiguration?.uri?.toString() ?: "" }
                save.setOriginalQueue(currentUris)
                save.updateShuffled(true)

                isShuffleOn = true

                applyQueueReorder()
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

        fun replaceQueue(context: Context, songs: List<Song>, playlistId: Long = -1L) {
            if (songs.isEmpty()) return
            instance?.replaceQueueInternal(songs, playlistId) ?: run {
                val intent = Intent(context, MusicService::class.java).apply {
                    putStringArrayListExtra(EXTRA_QUEUE_URIS, ArrayList(songs.map { it.uri.toString() }))
                    putStringArrayListExtra(EXTRA_QUEUE_TITLES, ArrayList(songs.map { it.title }))
                    putStringArrayListExtra(EXTRA_QUEUE_ARTISTS, ArrayList(songs.map { it.artist }))
                }
                context.startForegroundService(intent)
            }
        }

        fun playShuffled(context: Context, songs: List<Song>, playlistId: Long = -1L) {
            if (songs.isEmpty()) return
            instance?.playShuffledInternal(songs, playlistId) ?: run {
                val intent = Intent(context, MusicService::class.java).apply {
                    putStringArrayListExtra(EXTRA_QUEUE_URIS, ArrayList(songs.map { it.uri.toString() }))
                    putStringArrayListExtra(EXTRA_QUEUE_TITLES, ArrayList(songs.map { it.title }))
                    putStringArrayListExtra(EXTRA_QUEUE_ARTISTS, ArrayList(songs.map { it.artist }))
                    putExtra(EXTRA_SHUFFLED, true)
                }
                context.startForegroundService(intent)
            }
        }

        fun addToQueue(context: Context, song: Song, applyShuffleRandomness: Boolean = false, resetPlaylist: Boolean = true) {
            val save = ShuffleSave.getInstance(context)
            if (save.isShuffled) save.addToOriginal(song.uri.toString())
            instance?.addToQueueInternal(song, applyShuffleRandomness, resetPlaylist)
                ?: if (isAppInForeground(context)) {
                    playSingular(context, song)
                } else {
                    addToSettingsQueue(context, song, applyShuffleRandomness, resetPlaylist)
                }
        }

        private fun addToSettingsQueue(context: Context, song: Song, applyShuffleRandomness: Boolean, resetPlaylist: Boolean) {
            val settings = SettingsSave.getInstance(context)
            val queue = settings.lastQueue.toMutableList()

            if (applyShuffleRandomness && queue.isNotEmpty()) {
                val randomIndex = (0 until queue.size).random()
                queue.add(randomIndex, QueueEntry(song.uri.toString(), song.title, song.artist))
            } else {
                queue.add(QueueEntry(song.uri.toString(), song.title, song.artist))
            }

            settings.lastQueue = queue

            if (resetPlaylist) {
                settings.lastPlaylistId = -1L
                settings.queueSource = QueueSource.MANUAL
            }
        }

        fun moveQueueItem(from: Int, to: Int) {
            instance?.mediaSession?.player?.moveMediaItem(from, to)
        }

        fun removeFromQueue(context: Context, index: Int) {
            val settings = SettingsSave.getInstance(context)
            val player = instance?.mediaSession?.player ?: return
            val uri = player.getMediaItemAt(index).localConfiguration?.uri?.toString()
            if (uri != null && ShuffleSave.getInstance(instance!!).isShuffled) {
                ShuffleSave.getInstance(instance!!).removeFromOriginal(uri)
            }
            player.removeMediaItem(index)
            if (index < settings.lastQueueIndex) settings.lastQueueIndex--
        }

        fun removeFromQueue(context: Context, uri: String) {
            val player = instance?.mediaSession?.player ?: return
            val index = (0 until player.mediaItemCount)
                .firstOrNull { player.getMediaItemAt(it).localConfiguration?.uri?.toString() == uri }
                ?: return
            removeFromQueue(context, index)
        }

        fun removeAllFromQueue(context: Context, uri: String) {
            val player = instance?.mediaSession?.player ?: return
            (0 until player.mediaItemCount)
                .filter { player.getMediaItemAt(it).localConfiguration?.uri?.toString() == uri }
                .reversed()
                .forEach { removeFromQueue(context, it) }
        }

        private fun applyQueueReorder(targetUris: List<String>? = null) {
            val player = instance?.mediaSession?.player ?: return
            val currentItem = player.currentMediaItem ?: return
            val currentUri = currentItem.localConfiguration?.uri?.toString() ?: return
            val currentIndex = player.currentMediaItemIndex

            player.moveMediaItem(currentIndex, 0)
            val count = player.mediaItemCount

            if (targetUris == null) {
                // Shuffle in place
                val rest = (1 until count).map { player.getMediaItemAt(it) }.shuffled()

                player.removeMediaItems(1, count)
                player.addMediaItems(rest)

                val randomIndex = (1 until player.mediaItemCount).random()
                player.moveMediaItem(0, randomIndex)
            } else {
                // Reorder to match targetUris
                val itemsByUri = (1 until count).associate {
                    player.getMediaItemAt(it).localConfiguration?.uri?.toString() to player.getMediaItemAt(it)
                }
                val rest = targetUris.filter { it != currentUri }.mapNotNull { itemsByUri[it] }

                player.removeMediaItems(1, count)
                player.addMediaItems(rest)

                val targetIndex = targetUris.indexOf(currentUri).takeIf { it >= 0 } ?: 0
                player.moveMediaItem(0, targetIndex)
            }
        }

        fun reorderQueue(from: Int, to: Int) {
            instance?.mediaSession?.player?.moveMediaItem(from, to)
        }

        fun initFromSettings(context: Context) {
            val settings = SettingsSave.getInstance(context)
            if (settings.lastQueue.isNotEmpty()) {
                queueState.value = settings.lastQueue.map { entry ->
                    MediaItem.Builder()
                        .setUri(entry.uri.toUri())
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(entry.title)
                                .setArtist(entry.artist)
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
        private const val EXTRA_SHUFFLED = "extra_shuffled"
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
    }

    private fun playSingularInternal(song: Song, startPositionMs: Long = 0L) {
        val player = mediaSession?.player ?: return

        when (currentSource) {
            QueueSource.PLAYLIST -> {
                currentSource = QueueSource.MANUAL
                settings.queueSource = QueueSource.MANUAL
                player.setMediaItem(song.toMediaItem(), startPositionMs)
            }
            QueueSource.MANUAL -> {
                player.addMediaItem(0, song.toMediaItem())
                player.seekTo(0, startPositionMs)
            }
        }

        settings.lastPlaylistId = -1L

        player.prepare()
        player.play()
    }

    private fun replaceQueueInternal(songs: List<Song>, playlistId: Long = -1L) {
        val player = mediaSession?.player ?: return
        currentSource = QueueSource.PLAYLIST
        settings.queueSource = QueueSource.PLAYLIST
        settings.lastPlaylistId = playlistId

        val save = ShuffleSave.getInstance(this)
        save.updateShuffled(false)
        isShuffleOn = false
        save.setOriginalQueue(songs.map { it.uri.toString() })

        player.setMediaItems(songs.map { it.toMediaItem() })
        player.prepare()
        player.play()
    }

    private fun playShuffledInternal(songs: List<Song>, playlistId: Long = -1L) {
        val player = mediaSession?.player ?: return
        currentSource = QueueSource.PLAYLIST
        settings.queueSource = QueueSource.PLAYLIST
        settings.lastPlaylistId = playlistId

        val save = ShuffleSave.getInstance(this)
        val shuffled = songs.shuffled()

        save.setOriginalQueue(songs.map { it.uri.toString() })
        save.updateShuffled(true)
        isShuffleOn = true

        player.setMediaItems(shuffled.map { it.toMediaItem() })
        player.seekTo(0, 0)
        player.prepare()
        player.play()

        mediaSession?.let { updateNotificationButtons(it) }
    }

    private fun addToQueueInternal(song: Song, applyShuffleRandomness: Boolean, resetPlaylist: Boolean) {
        val player = mediaSession?.player ?: return
        val insertIndex = if (applyShuffleRandomness && player.mediaItemCount > 0)
            (0 until player.mediaItemCount).random()
        else
            player.mediaItemCount

        player.addMediaItem(insertIndex, song.toMediaItem())
        if (insertIndex <= settings.lastQueueIndex) settings.lastQueueIndex++

        if (currentSource == QueueSource.PLAYLIST && resetPlaylist) {
            settings.lastPlaylistId = -1L
            currentSource = QueueSource.MANUAL
            settings.queueSource = QueueSource.MANUAL
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

            val queue = settings.lastQueue.ifEmpty {
                val song = songRepo.resolveSong(lastUri.toUri()) ?: songRepo.resolveSong(this@MusicService, lastUri.toUri())
                listOf(QueueEntry(uri = lastUri, title = song?.title ?: "", artist = song?.artist ?: ""))
            }
            val savedIndex = settings.lastQueueIndex.coerceIn(0, queue.lastIndex)
            val currentIndex = if (queue[savedIndex].uri == lastUri) {
                savedIndex
            } else {
                queue.indices
                    .filter { queue[it].uri == lastUri }
                    .minByOrNull { abs(it - savedIndex) }
                    ?: 0
            }
            val items = queue.map { entry ->
                MediaItem.Builder()
                    .setUri(entry.uri.toUri())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(entry.title)
                            .setArtist(entry.artist)
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
                    settings.lastPositionMs = player.currentPosition
                    settings.lastDurationMs = player.duration.coerceAtLeast(0L)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem == null) {
                    currentUriState.value = null
                    return
                }
                val uri = mediaItem.localConfiguration?.uri ?: return

                isLiked = favourites.isFavourite(uri)
                currentIndexState.value = instance?.mediaSession?.player?.currentMediaItemIndex ?: -1
                currentUriState.value = uri
                settings.lastSongUri = uri.toString()
                settings.lastQueueIndex = player.currentMediaItemIndex
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

                queueState.value = items
                currentIndexState.value = player.currentMediaItemIndex
                currentUriState.value = player.currentMediaItem?.localConfiguration?.uri

                timelineSaveJob?.cancel()
                timelineSaveJob = serviceScope.launch {
                    delay(if (items.isEmpty()) 200L else 100L)
                    settings.lastPositionMs = player.currentPosition.coerceAtLeast(0L)
                    settings.lastQueue = items.mapNotNull { QueueEntry(it.localConfiguration?.uri?.toString() ?: return@mapNotNull null, it.mediaMetadata.title?.toString() ?: "", it.mediaMetadata.artist?.toString() ?: "") }
                }
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
                val uris = settings.lastQueue
                if (uris.isNotEmpty()) {
                    val currentIndex = uris.indexOfFirst { entry -> entry.uri == settings.lastSongUri }.coerceAtLeast(0)
                    val items = uris.map { entry ->
                        val song by lazy { songRepo.resolveSong(entry.uri.toUri()) ?: songRepo.resolveSong(this, entry.uri.toUri()) }
                        MediaItem.Builder()
                            .setUri(entry.uri.toUri())
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(entry.title.ifBlank { song?.title ?: "Unknown" })
                                    .setArtist(entry.artist.ifBlank { song?.artist ?: "Unknown" })
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
                val titles = it.getStringArrayListExtra(EXTRA_QUEUE_TITLES) ?: arrayListOf()
                val artists = it.getStringArrayListExtra(EXTRA_QUEUE_ARTISTS) ?: arrayListOf()
                val shuffled = it.getBooleanExtra(EXTRA_SHUFFLED, false)
                if (shuffled) {
                    val songs = queueUris.mapIndexed { i, uri ->
                        val song by lazy { songRepo.resolveSong(uri.toUri()) ?: songRepo.resolveSong(this, uri.toUri()) }
                        Song(
                            id = uri.hashCode().toLong(),
                            title = titles.getOrElse(i) { "" }.ifBlank { song?.title ?: "Unknown" },
                            artist = artists.getOrElse(i) { "" }.ifBlank { song?.artist ?: "Unknown" },
                            album = song?.album ?: "",
                            albumId = song?.albumId ?: 0,
                            uri = uri.toUri(),
                            durationMs = song?.durationMs ?: 0L,
                            albumArtUri = song?.albumArtUri ?: Uri.EMPTY,
                            track = song?.track ?: 0
                        )
                    }
                    playShuffledInternal(songs)
                } else {
                    replaceQueueFromExtras(queueUris, titles, artists)
                }
                return START_NOT_STICKY
            }

            // Single song (playSingular / cold start)
            val uri = it.getStringExtra(EXTRA_URI) ?: return@let
            val song by lazy { songRepo.resolveSong(uri.toUri()) ?: songRepo.resolveSong(this, uri.toUri()) }
            val title = it.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { song?.title ?: "Unknown" }
            val artist = it.getStringExtra(EXTRA_ARTIST).orEmpty().ifBlank { song?.artist ?: "Unknown" }
            val position = it.getLongExtra(EXTRA_POSITION_MS, 0L)
            setAndPlay(uri, title, artist, position)
        }

        return START_NOT_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        mediaSession?.player?.let { player ->
            settings.lastPositionMs = player.currentPosition
            settings.lastDurationMs = player.duration.coerceAtLeast(0L)
        }
        settings.flush()
        instance = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        settings.flush()
        super.onTaskRemoved(rootIntent)
        stopSelf()
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