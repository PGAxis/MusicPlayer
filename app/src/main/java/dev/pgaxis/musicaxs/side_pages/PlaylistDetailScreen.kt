package dev.pgaxis.musicaxs.side_pages

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.models.Song
import dev.pgaxis.musicaxs.services.MusicService
import dev.pgaxis.musicaxs.services.PlaylistToQueue
import dev.pgaxis.musicaxs.settings.FavouritedPlaylistsSave
import dev.pgaxis.musicaxs.templates.AddToSheet
import dev.pgaxis.musicaxs.templates.ListDivider
import dev.pgaxis.musicaxs.templates.SongRow
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

data class KeyedSong(val key: Long, val song: Song)

private val ART_FULL_SIZE = 150.dp
private val ART_TITLE_SPACING = 16.dp
private val HEADER_VERTICAL_PADDING = 8.dp

@SuppressLint("FrequentlyChangingValue")
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit,
    onSeeDetail: (uri: String) -> Unit,
    vm: PlaylistsDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedSong by remember { mutableStateOf<Song?>(null) }

    val uiState by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(playlistId) {
        vm.initPlaylist(playlistId)
    }

    when (val state = uiState) {
        is DetailUiState.Loading -> {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is DetailUiState.Ready -> {
            var songs by remember {
                mutableStateOf(state.songs.mapIndexed { i, s -> KeyedSong(i.toLong(), s) })
            }
            LaunchedEffect(state.songs.size) {
                songs = state.songs.mapIndexed { i, s -> KeyedSong(i.toLong(), s) }
            }
            val listState = rememberLazyListState()
            val reorderState = rememberReorderableLazyListState(listState) { from, to ->
                songs = songs.toMutableList().apply { add(to.index, removeAt(from.index)) }
                vm.moveSong(playlistId, songs.map { it.song })
                PlaylistToQueue(context).reorderIfCurrent(playlistId, songs.map { it.song })
            }

            val density = LocalDensity.current
            // Range (in px) the art (and with it, the whole header) collapses over.
            val headerCollapseRangePx = with(density) { ART_FULL_SIZE.toPx() }
            var imageOffsetPx by remember { mutableFloatStateOf(0f) }

            val nestedScrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        val newOffset = (imageOffsetPx + available.y).coerceIn(-headerCollapseRangePx, 0f)
                        val consumed = newOffset - imageOffsetPx
                        imageOffsetPx = newOffset
                        return Offset(0f, consumed)
                    }
                }
            }

            // 0f = fully expanded header, 1f = fully collapsed header.
            val scrollFraction by remember {
                derivedStateOf { (-imageOffsetPx / headerCollapseRangePx).coerceIn(0f, 1f) }
            }

            val favPlaylists = remember { FavouritedPlaylistsSave.getInstance(context) }
            var isFavourited by remember { mutableStateOf(favPlaylists.isFavourited(playlistId)) }

            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // -- Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, shape = RoundedCornerShape(0.dp), modifier = Modifier.size(45.dp).padding(horizontal = 5.dp)) {
                        Icon(painterResource(R.drawable.back), "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.weight(1f))
                    when (playlistId) {
                        0L, 1L, 2L, 3L, 4L -> { }
                        else -> {
                            IconButton(onClick = {
                                favPlaylists.toggle(playlistId)
                                isFavourited = !isFavourited
                            }, shape = RoundedCornerShape(0.dp)) {
                                Icon(
                                    painterResource(if (isFavourited) R.drawable.heart_filled else R.drawable.heart_outline),
                                    "Favourite playlist",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        if (state.songs.isNotEmpty()) MusicService.playShuffled(context, state.songs, playlistId)
                    }, shape = RoundedCornerShape(0.dp)) {
                        Icon(painterResource(R.drawable.shuffle), "Play shuffled",
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = {
                        if (state.songs.isNotEmpty()) MusicService.playNormal(context, state.songs, playlistId)
                    }, shape = RoundedCornerShape(0.dp)) {
                        Icon(painterResource(R.drawable.play), "Play all",
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    }
                }

                if (state.songs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.pls_det_no_found))
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(nestedScrollConnection)
                    ) {
                        PlaylistHeader(
                            song = state.songs.first(),
                            name = state.name,
                            trackCount = state.songs.size,
                            isLandscape = isLandscape,
                            scrollFraction = scrollFraction
                        )

                        Surface(
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                state = listState
                            ) {
                                itemsIndexed(songs, key = { _, keyed -> keyed.key }) { index, keyed ->
                                    ReorderableItem(reorderState, key = keyed.key) { _ ->
                                        Column {
                                            SongRow(
                                                song = keyed.song,
                                                onSeeDetails = onSeeDetail,
                                                onAddTo = { selectedSong = keyed.song },
                                                showRemoveFrom = playlistId !in longArrayOf(0L, 1L, 2L, 3L, 4L),
                                                onRemoveFrom = {
                                                    vm.removeSong(playlistId, index)
                                                    PlaylistToQueue(context).removeIfCurrent(playlistId, keyed.song, index)
                                                },
                                                dragHandleModifier = Modifier.draggableHandle()
                                            )

                                            if (index < songs.lastIndex) {
                                                ListDivider()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            selectedSong?.let { song ->
                AddToSheet(song = song, onDismiss = { selectedSong = null })
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    song: Song,
    name: String,
    trackCount: Int,
    isLandscape: Boolean,
    scrollFraction: Float
) {
    val artSize = ART_FULL_SIZE * (1f - scrollFraction)
    val spacing = ART_TITLE_SPACING * (1f - scrollFraction)

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = HEADER_VERTICAL_PADDING),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(song = song, size = artSize)
            if (artSize > 0.dp) Spacer(Modifier.width(spacing))
            PlaylistTitle(name = name, trackCount = trackCount)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = HEADER_VERTICAL_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AlbumArt(song = song, size = artSize)
            if (artSize > 0.dp) Spacer(Modifier.height(spacing))
            PlaylistTitle(name = name, trackCount = trackCount)
        }
    }
}

@Composable
private fun AlbumArt(song: Song, size: Dp) {
    val context = LocalContext.current
    val validAlbumArtUri = song.albumArtUri.takeIf {
        it != Uri.EMPTY && (it?.lastPathSegment?.toLongOrNull() ?: 0L) > 0L
    }
    var useFallbackUri by remember(song.uri) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(size.coerceAtLeast(0.dp))
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(if (useFallbackUri) song.uri else validAlbumArtUri)
                .size(150)
                .crossfade(true)
                .build(),
            contentDescription = "Album art",
            error = painterResource(R.drawable.default_cover),
            placeholder = painterResource(R.drawable.default_cover),
            fallback = painterResource(R.drawable.default_cover),
            modifier = Modifier
                .size(ART_FULL_SIZE)
                .clip(RoundedCornerShape(15.dp)),
            contentScale = ContentScale.Crop,
            onError = { if (!useFallbackUri) useFallbackUri = true }
        )
    }
}

@Composable
private fun PlaylistTitle(name: String, trackCount: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            name,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.basicMarquee()
        )
        Text(
            pluralStringResource(R.plurals.track_count, trackCount, trackCount),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}