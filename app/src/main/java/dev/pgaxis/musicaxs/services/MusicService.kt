package dev.pgaxis.musicaxs.services

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.audiofx.AudioEffect
import android.media.audiofx.Visualizer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dev.pgaxis.musicaxs.MainActivity
import dev.pgaxis.musicaxs.MusicAxsApplication
import dev.pgaxis.musicaxs.funcs.toMediaItem
import dev.pgaxis.musicaxs.models.PodcastEpisode
import dev.pgaxis.musicaxs.models.PodcastFeed
import dev.pgaxis.musicaxs.models.QueueItemSource
import dev.pgaxis.musicaxs.models.Song
import dev.pgaxis.musicaxs.repositories.SongRepository
import dev.pgaxis.musicaxs.settings.FavouritesSave
import dev.pgaxis.musicaxs.settings.PlayCountTracker
import dev.pgaxis.musicaxs.settings.SettingsSave
import dev.pgaxis.musicaxs.settings.SettingsSave.QueueEntry
import dev.pgaxis.musicaxs.settings.ShuffleSave
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

enum class QueueSource { PLAYLIST, MANUAL }

@UnstableApi
class EmbeddedArtBitmapLoader(
    private val context: Context,
    private val onBitmapReady: ((uri: Uri, artworkData: ByteArray) -> Unit)? = null
) : BitmapLoader {
    private val executor = Executors.newSingleThreadExecutor()
    private val TARGET_BYTES = 80_000

    override fun supportsMimeType(mimeType: String) = false

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        Futures.immediateFuture(BitmapFactory.decodeByteArray(data, 0, data.size))

    private fun Bitmap.constrained(size: Int = 512): Bitmap {
        val scale = maxOf(size.toFloat() / this.width, size.toFloat() / this.height)
        val scaledWidth = (this.width * scale).toInt()
        val scaledHeight = (this.height * scale).toInt()
        val scaled = this.scale(scaledWidth, scaledHeight)
        val x = (scaledWidth - size) / 2
        val y = (scaledHeight - size) / 2
        return Bitmap.createBitmap(scaled, x, y, size, size)
    }

    private fun cachedArtFor(uri: Uri): Bitmap? {
        if (uri.authority != "media") return null
        val songId = uri.lastPathSegment ?: return null
        val dir = File(context.cacheDir, "album_art")

        val sentinel = File(dir, "song_$songId.none")
        if (sentinel.exists()) return null

        val cacheFile = File(dir, "song_$songId.jpg")
        if (!cacheFile.exists() || cacheFile.length() == 0L) return null

        return try {
            BitmapFactory.decodeFile(cacheFile.path)
        } catch (_: Exception) {
            null
        }
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        return MoreExecutors.listeningDecorator(executor).submit<Bitmap> {
            val bitmap = cachedArtFor(uri) ?: run {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(context, uri)
                    mmr.embeddedPicture?.let {
                        BitmapFactory.decodeByteArray(it, 0, it.size)
                    } ?: context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    } ?: throw IllegalStateException("No artwork found for $uri")
                } finally {
                    mmr.release()
                }
            }

            val constrained = bitmap.constrained()

            val artworkBytes = constrained.compressToMaxBytes(maxBytes = TARGET_BYTES)
            onBitmapReady?.invoke(uri, artworkBytes)

            constrained
        }
    }
}

private fun Bitmap.compressToMaxBytes(maxBytes: Int, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG): ByteArray {
    var low = 0
    var high = 100
    var best = ByteArrayOutputStream().apply { this@compressToMaxBytes.compress(format, low, this) }.toByteArray()

    if (best.size > maxBytes) return best

    while (low <= high) {
        val mid = (low + high) / 2
        val stream = ByteArrayOutputStream()
        compress(format, mid, stream)
        val bytes = stream.toByteArray()

        if (bytes.size <= maxBytes) {
            best = bytes
            low = mid + 1
        } else {
            high = mid - 1
        }
    }

    return best
}

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    // -- Queue state
    private var currentSource: QueueSource = QueueSource.MANUAL
    private var visualizer: Visualizer? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var playCountJob: Job? = null
    private var timelineSaveJob: Job? = null
    private var playStartTime: Long = 0L
    private var wasPlayingBeforeTransition = false

    private val favourites by lazy { FavouritesSave.getInstance(applicationContext) }
    private val shuffleSave by lazy { ShuffleSave.getInstance(applicationContext) }
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
        val queueSourceState = MutableStateFlow(QueueSource.MANUAL)
        val playlistIdState = MutableStateFlow<Long>(-1)
        val playlistRenamedState = MutableStateFlow(false)
        val isPlayingState = MutableStateFlow(false)
        val currentIndexState = MutableStateFlow(-1)
        val audioBandsState = MutableStateFlow(Triple(0f, 0f, 0f)) // bass, mid, treble
        val realBarsEnabledState = MutableStateFlow(false)
        val visualizerActiveState = MutableStateFlow(false)

        var isShuffleOn = false
            private set
        var isLiked = false
            private set

        // -- Public queue API

        val playerInstance get() = instance?.mediaSession?.player

        val isShuffled get() = instance?.let { ShuffleSave.getInstance(it).isShuffled } ?: false

        fun changePlaylistRenamedState(state: Boolean) {
            playlistRenamedState.value = state
        }

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

        @OptIn(UnstableApi::class)
        fun setRealBarsEnabled(enabled: Boolean) {
            instance?.settings?.realBars = enabled
            realBarsEnabledState.value = enabled
            val inst = instance ?: return
            if (enabled) {
                inst.mediaSession?.player?.let { inst.attachVisualizer(it.audioSessionId) }
            } else {
                inst.releaseVisualizer()
            }
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
                val newPosition = it.currentPosition + offsetMs
                when {
                    newPosition < 0L -> it.seekTo(0L)
                    newPosition >= it.duration -> it.seekToNextMediaItem()
                    else -> it.seekTo(newPosition)
                }
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

        fun playNormal(context: Context, songs: List<Song>, playlistId: Long = -1L) {
            if (songs.isEmpty()) return
            instance?.playNormalInternal(songs, playlistId) ?: run {
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

            val inst = instance
            if (inst != null && MusicAxsApplication.hasUIBeenActive) {
                inst.addToQueueInternal(song, applyShuffleRandomness, resetPlaylist)
            } else if (MusicAxsApplication.hasUIBeenActive) {
                playSingular(context, song)
            }
        }

        fun playSingularPodcast(episode: PodcastEpisode, feed: PodcastFeed) {
            instance?.playSingularPodcastInternal(episode, feed)
        }

        fun replaceQueuePodcast(episodes: List<PodcastEpisode>, feed: PodcastFeed) {
            if (episodes.isEmpty()) return
            instance?.replaceQueuePodcastInternal(episodes, feed)
        }

        fun playShuffledPodcast(episodes: List<PodcastEpisode>, feed: PodcastFeed) {
            if (episodes.isEmpty()) return
            instance?.playShuffledPodcastInternal(episodes, feed)
        }

        fun addToQueuePodcast(episode: PodcastEpisode, feed: PodcastFeed) {
            instance?.addToQueuePodcastInternal(episode, feed)
        }

        fun moveQueueItem(from: Int, to: Int) {
            instance?.mediaSession?.player?.moveMediaItem(from, to)
        }

        fun removeFromQueue(context: Context, index: Int) {
            val settings = SettingsSave.getInstance(context)
            val player = instance?.mediaSession?.player ?: return
            if (index >= player.mediaItemCount) return
            val uri = player.getMediaItemAt(index).localConfiguration?.uri?.toString()
            if (uri != null) {
                if (ShuffleSave.getInstance(instance!!).isShuffled) ShuffleSave.getInstance(instance!!).removeFromOriginal(uri)
                player.removeMediaItem(index)
                if (index < settings.lastQueueIndex) settings.lastQueueIndex--
            }
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

        fun reorderQueueTo(context: Context, targetUris: List<String>) {
            if (ShuffleSave.getInstance(context).isShuffled) {
                ShuffleSave.getInstance(context).setOriginalQueue(targetUris)
                return
            }
            val player = playerInstance ?: return
            targetUris.forEachIndexed { targetIndex, uri ->
                val currentIndex = (0 until player.mediaItemCount)
                    .firstOrNull { player.getMediaItemAt(it).localConfiguration?.uri?.toString() == uri }
                    ?: return@forEachIndexed
                if (currentIndex != targetIndex) {
                    reorderQueue(currentIndex, targetIndex)
                }
            }
        }

        fun syncMissingFromPlaylist(context: Context, missing: List<Pair<Int, Song>>) {
            val player = playerInstance ?: return
            val save = ShuffleSave.getInstance(context)
            val settings = SettingsSave.getInstance(context)

            if (save.isShuffled) {
                for ((_, song) in missing) {
                    save.addToOriginal(song.uri.toString())
                    val insertIndex = if (player.mediaItemCount > 0)
                        (0 until player.mediaItemCount).random()
                    else 0
                    player.addMediaItem(insertIndex, song.toMediaItem())
                    if (insertIndex <= settings.lastQueueIndex) settings.lastQueueIndex++
                }
            } else {
                for ((insertedSoFar, pair) in missing.withIndex()) {
                    val (playlistIndex, song) = pair
                    val insertAt = (playlistIndex + insertedSoFar).coerceIn(0, player.mediaItemCount)
                    player.addMediaItem(insertAt, song.toMediaItem())
                    if (insertAt <= settings.lastQueueIndex) settings.lastQueueIndex++
                }
            }
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
            val currentItem = player?.currentMediaItem ?: return

            val source = currentItem.mediaMetadata.extras
                ?.getString("source")
                ?.let { runCatching { QueueItemSource.valueOf(it) }.getOrNull() }
                ?: QueueItemSource.LOCAL

            if (source != QueueItemSource.LOCAL) return

            currentItem.localConfiguration?.uri?.let {
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

    private fun setRepeatInternal(repeatMode: Int) {
        settings.repeatMode = repeatMode
    }

    private fun attachVisualizer(sessionId: Int) {
        releaseVisualizer()
        if (!settings.realBars) return
        if (sessionId == AudioEffect.ERROR_BAD_VALUE || sessionId == 0) return

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("AudioViz", "RECORD_AUDIO not granted, staying in fallback mode")
            return
        }

        try {
            visualizer = Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, r: Int) {}
                        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rateMilliHz: Int) {
                            fft ?: return
                            audioBandsState.value = bandsFromFft(fft, rateMilliHz)
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    false,
                    true
                )
                enabled = true
            }
            visualizerActiveState.value = true
        } catch (_: Exception) {
            visualizer = null
            visualizerActiveState.value = false
        }
    }

    private fun releaseVisualizer() {
        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null
        visualizerActiveState.value = false
        audioBandsState.value = Triple(0f, 0f, 0f)
    }

    private var sharedRunningMax = 1f

    //I don't fucking know, these seem to be best from a test
    private val BASS_GAIN = 1.0f
    private val MID_GAIN = 3.94f
    private val TREBLE_GAIN = 9.4f

    private fun bandsFromFft(fft: ByteArray, samplingRateMilliHz: Int): Triple<Float, Float, Float> {
        val sampleRate = samplingRateMilliHz / 1000f
        val n = fft.size / 2

        fun magnitude(bin: Int): Float = when (bin) {
            0 -> abs(fft[0].toInt()).toFloat()
            n -> abs(fft[1].toInt()).toFloat()
            else -> {
                val re = fft[2 * bin].toFloat()
                val im = fft[2 * bin + 1].toFloat()
                sqrt(re * re + im * im)
            }
        }

        fun binForFreq(freq: Float) = ((freq / (sampleRate / 2f)) * n).toInt().coerceIn(0, n)
        fun avg(range: IntRange) = if (range.isEmpty()) 0f else range.map(::magnitude).average().toFloat()

        val rawBass = avg(binForFreq(20f)..binForFreq(250f)) * BASS_GAIN
        val rawMid = avg(binForFreq(250f)..binForFreq(4000f)) * MID_GAIN
        val rawTreble = avg(binForFreq(4000f)..binForFreq(sampleRate / 2f)) * TREBLE_GAIN

        sharedRunningMax = maxOf(rawBass, rawMid, rawTreble, sharedRunningMax * 0.98f)

        val bass = (rawBass / sharedRunningMax).coerceIn(0f, 1f)
        val mid = (rawMid / sharedRunningMax).coerceIn(0f, 1f)
        val treble = (rawTreble / sharedRunningMax).coerceIn(0f, 1f)

        return Triple(bass, mid, treble)
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun playSingularInternal(song: Song, startPositionMs: Long = 0L) {
        val player = mediaSession?.player ?: return

        when (currentSource) {
            QueueSource.PLAYLIST -> {
                settings.lastPlaylistId = -1L
                playlistIdState.value = -1L
                shuffleSave.updateShuffled(false)
                isShuffleOn = false
                currentSource = QueueSource.MANUAL
                settings.queueSource = QueueSource.MANUAL
                queueSourceState.value = QueueSource.MANUAL
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

    private fun playNormalInternal(songs: List<Song>, playlistId: Long = -1L) {
        val player = mediaSession?.player ?: return
        settings.lastPlaylistId = playlistId
        playlistIdState.value = playlistId
        currentSource = QueueSource.PLAYLIST
        settings.queueSource = QueueSource.PLAYLIST
        queueSourceState.value = QueueSource.PLAYLIST

        shuffleSave.updateShuffled(false)
        isShuffleOn = false
        shuffleSave.setOriginalQueue(songs.map { it.uri.toString() })

        player.setMediaItems(songs.map { it.toMediaItem() })
        player.prepare()
        player.play()
    }

    private fun playShuffledInternal(songs: List<Song>, playlistId: Long = -1L) {
        val player = mediaSession?.player ?: return
        settings.lastPlaylistId = playlistId
        playlistIdState.value = playlistId
        currentSource = QueueSource.PLAYLIST
        settings.queueSource = QueueSource.PLAYLIST
        queueSourceState.value = QueueSource.PLAYLIST

        val shuffled = songs.shuffled()

        shuffleSave.setOriginalQueue(songs.map { it.uri.toString() })
        shuffleSave.updateShuffled(true)
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
            playlistIdState.value = -1L
            shuffleSave.updateShuffled(false)
            isShuffleOn = false
            currentSource = QueueSource.MANUAL
            queueSourceState.value = QueueSource.MANUAL
            settings.queueSource = QueueSource.MANUAL
        }
    }

    private fun playSingularPodcastInternal(episode: PodcastEpisode, feed: PodcastFeed) {
        val player = mediaSession?.player ?: return
        when (currentSource) {
            QueueSource.PLAYLIST -> {
                settings.lastPlaylistId = -1L
                playlistIdState.value = -1L
                shuffleSave.updateShuffled(false)
                isShuffleOn = false
                currentSource = QueueSource.MANUAL
                queueSourceState.value = QueueSource.MANUAL
                settings.queueSource = QueueSource.MANUAL
                player.setMediaItem(episode.toMediaItem(feed))
            }
            QueueSource.MANUAL -> {
                player.addMediaItem(0, episode.toMediaItem(feed))
                player.seekTo(0, 0)
            }
        }
        player.prepare()
        player.play()
    }

    private fun replaceQueuePodcastInternal(episodes: List<PodcastEpisode>, feed: PodcastFeed) {
        val player = mediaSession?.player ?: return
        settings.lastPlaylistId = 4L
        playlistIdState.value = 4L
        currentSource = QueueSource.PLAYLIST
        queueSourceState.value = QueueSource.PLAYLIST
        settings.queueSource = QueueSource.PLAYLIST

        shuffleSave.updateShuffled(false)
        isShuffleOn = false
        shuffleSave.setOriginalQueue(episodes.map { it.audioUrl })

        player.setMediaItems(episodes.map { it.toMediaItem(feed) })
        player.prepare()
        player.play()
    }

    private fun playShuffledPodcastInternal(episodes: List<PodcastEpisode>, feed: PodcastFeed) {
        val player = mediaSession?.player ?: return
        settings.lastPlaylistId = 4L
        playlistIdState.value = 4L
        currentSource = QueueSource.PLAYLIST
        queueSourceState.value = QueueSource.PLAYLIST
        settings.queueSource = QueueSource.PLAYLIST

        val shuffled = episodes.shuffled()

        shuffleSave.setOriginalQueue(episodes.map { it.audioUrl })
        shuffleSave.updateShuffled(true)
        isShuffleOn = true

        player.setMediaItems(shuffled.map { it.toMediaItem(feed) })
        player.seekTo(0, 0)
        player.prepare()
        player.play()

        mediaSession?.let { updateNotificationButtons(it) }
    }

    private fun addToQueuePodcastInternal(episode: PodcastEpisode, feed: PodcastFeed) {
        val player = mediaSession?.player ?: return
        player.addMediaItem(player.mediaItemCount, episode.toMediaItem(feed))

        if (currentSource == QueueSource.PLAYLIST) {
            settings.lastPlaylistId = -1L
            playlistIdState.value = -1L
            shuffleSave.updateShuffled(false)
            isShuffleOn = false
            currentSource = QueueSource.MANUAL
            queueSourceState.value = QueueSource.MANUAL
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
            val song by lazy { songRepo.resolveSong(uri.toUri()) ?: songRepo.resolveSong(this, uri.toUri()) }
            MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(titles.getOrElse(i) { "Unknown" })
                        .setArtist(artists.getOrElse(i) { "Unknown" })
                        .setArtworkUri(song?.uri)
                        .setExtras(Bundle().apply {
                            putString("source", QueueItemSource.LOCAL.name)
                        })
                        .build()
                )
                .build()
        }
        currentSource = QueueSource.PLAYLIST
        queueSourceState.value = QueueSource.PLAYLIST
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
                            .setArtworkUri(entry.uri.toUri())
                            .setExtras(Bundle().apply {
                                putString("source", entry.source.name)
                                entry.deviceId?.let { putString("deviceId", it) }
                            })
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

        realBarsEnabledState.value = settings.realBars

        setMediaNotificationProvider(CustomNotificationProvider(this))

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(MusicSessionCallback())
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setBitmapLoader(EmbeddedArtBitmapLoader(this) { uri, artworkData ->
                Handler(Looper.getMainLooper()).post {
                    val player = mediaSession?.player ?: return@post
                    if (player.mediaItemCount == 0) return@post
                    val index = player.currentMediaItemIndex
                    if (index !in 0 until player.mediaItemCount) return@post
                    val item = player.getMediaItemAt(index)
                    if (item.localConfiguration?.uri == uri) {
                        player.replaceMediaItem(
                            index,
                            item.buildUpon()
                                .setMediaMetadata(
                                    item.mediaMetadata.buildUpon()
                                        .setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                        .build()
                                )
                                .build()
                        )
                    }
                }
            })
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

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                    wasPlayingBeforeTransition = playWhenReady
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem == null) {
                    currentUriState.value = null
                    return
                }

                val uri = mediaItem.localConfiguration?.uri ?: return
                val source = mediaItem.mediaMetadata.extras?.getString("source")?.let {
                    runCatching {
                        QueueItemSource.valueOf(it)
                    }.getOrNull()
                } ?: QueueItemSource.LOCAL

                if (source == QueueItemSource.PODCAST) {
                    val isLocalFile = uri.scheme == "file"
                    if (!isLocalFile && !isNetworkAvailable()) {
                        player.seekToNextMediaItem()
                        if (wasPlayingBeforeTransition) {
                            serviceScope.launch(Dispatchers.Main) {
                                delay(100.milliseconds)
                                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                                player.play()
                            }
                        }
                        return
                    }
                }

                val isLocal = source == QueueItemSource.LOCAL

                isLiked = if (isLocal) favourites.isFavourite(uri) else false

                currentIndexState.value =
                    instance?.mediaSession?.player?.currentMediaItemIndex ?: -1
                currentUriState.value = uri
                settings.lastSongUri = uri.toString()
                settings.lastQueueIndex = player.currentMediaItemIndex
                updateNotificationButtons(mediaSession!!)

                if (isLocal) {
                    playCountJob?.cancel()
                    playStartTime = 0L
                    playCountJob = serviceScope.launch {
                        while (true) {
                            delay(500.milliseconds)
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
            }

            override fun onPlayerError(error: PlaybackException) {
                val current = player.currentMediaItem
                val source = current?.mediaMetadata?.extras?.getString("source")?.let {
                    runCatching { QueueItemSource.valueOf(it) }.getOrNull()
                } ?: QueueItemSource.LOCAL

                if (source == QueueItemSource.PODCAST) {
                    // Stream failed (network unavailable, blocked, etc.)
                    player.seekToNextMediaItem()
                    player.play()
                    return
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
                    delay((if (items.isEmpty()) 200L else 100L).milliseconds)
                    settings.lastPositionMs = player.currentPosition.coerceAtLeast(0L)
                    settings.lastQueue = items.mapNotNull { item ->
                        val uri = item.localConfiguration?.uri?.toString() ?: return@mapNotNull null
                        val extras = item.mediaMetadata.extras
                        QueueEntry(
                            uri = uri,
                            title = item.mediaMetadata.title?.toString() ?: "",
                            artist = item.mediaMetadata.artist?.toString() ?: "",
                            albumArtUri = item.mediaMetadata.artworkUri?.toString(),
                            durationMs = 0L,
                            source = extras?.getString("source")
                                ?.let { runCatching { QueueItemSource.valueOf(it) }.getOrNull() }
                                ?: QueueItemSource.LOCAL,
                            deviceId = extras?.getString("deviceId")
                        )
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && player.repeatMode == Player.REPEAT_MODE_OFF) {
                    player.seekTo(0, 0)
                    player.pause()
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                attachVisualizer(audioSessionId)
            }
        })

        queueState.value = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        currentIndexState.value = player.currentMediaItemIndex
        currentSource = settings.queueSource
        queueSourceState.value = settings.queueSource
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
                        MediaItem.Builder()
                            .setUri(entry.uri.toUri())
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(entry.title)
                                    .setArtist(entry.artist)
                                    .setArtworkUri(entry.uri.toUri())
                                    .setExtras(Bundle().apply {
                                        putString("source", entry.source.name)
                                        entry.deviceId?.let { id -> putString("deviceId", id) }
                                    })
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
        releaseVisualizer()
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