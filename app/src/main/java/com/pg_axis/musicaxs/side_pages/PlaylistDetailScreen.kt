package com.pg_axis.musicaxs.side_pages

import android.annotation.SuppressLint
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pg_axis.musicaxs.R
import com.pg_axis.musicaxs.models.Song
import com.pg_axis.musicaxs.services.MusicService
import com.pg_axis.musicaxs.settings.FavouritedPlaylistsSave
import com.pg_axis.musicaxs.templates.AddToSheet
import com.pg_axis.musicaxs.templates.SongRow
import com.pg_axis.musicaxs.ui.theme.CyanPrimary
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

data class KeyedSong(val key: Long, val song: Song)

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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
            }

            val density = LocalDensity.current
            val imageHeightPx = with(density) { 180.dp.toPx() }
            var imageOffsetPx by remember { mutableFloatStateOf(0f) }

            val nestedScrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        val newOffset = (imageOffsetPx + available.y).coerceIn(-imageHeightPx, 0f)
                        val consumed = newOffset - imageOffsetPx
                        imageOffsetPx = newOffset
                        return Offset(0f, consumed)
                    }
                }
            }

            val favPlaylists = remember { FavouritedPlaylistsSave.getInstance(context) }
            var isFavourited by remember { mutableStateOf(favPlaylists.isFavourited(playlistId)) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, shape = RoundedCornerShape(0.dp)) {
                        Icon(painterResource(R.drawable.back), "Back", tint = CyanPrimary)
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.name, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                        Text("${state.songs.size} songs",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        favPlaylists.toggle(playlistId)
                        isFavourited = !isFavourited
                    }, shape = RoundedCornerShape(0.dp)) {
                        Icon(
                            painterResource(if (isFavourited) R.drawable.heart_filled else R.drawable.heart_outline),
                            "Favourite playlist",
                            tint = CyanPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    // Play all button
                    IconButton(onClick = {
                        if (state.songs.isNotEmpty()) MusicService.replaceQueue(context, state.songs)
                    }, shape = RoundedCornerShape(0.dp)) {
                        Icon(painterResource(R.drawable.play), "Play all",
                            tint = CyanPrimary, modifier = Modifier.size(28.dp))
                    }
                }

                HorizontalDivider()

                if (state.songs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("This playlist is empty.")
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(nestedScrollConnection)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(with(density) { (imageHeightPx + imageOffsetPx).toDp().coerceAtLeast(0.dp) })
                                .clipToBounds(),
                            contentAlignment = Alignment.Center
                        ) {
                            val validAlbumArtUri = state.songs.first().albumArtUri.takeIf { it != Uri.EMPTY && (it?.lastPathSegment?.toLongOrNull() ?: 0L) > 0L }
                            var useFallbackUri by remember { mutableStateOf(false) }

                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(if (useFallbackUri) state.songs.first().uri else validAlbumArtUri)
                                    .size(150)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Album art",
                                error = painterResource(R.drawable.default_cover),
                                placeholder = painterResource(R.drawable.default_cover),
                                fallback = painterResource(R.drawable.default_cover),
                                modifier = Modifier
                                    .size(150.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop,
                                onError = { if (!useFallbackUri) useFallbackUri = true }
                            )
                        }

                        HorizontalDivider()

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            state = listState
                        ) {
                            itemsIndexed(songs, key = { _, keyed -> keyed.key }) { index, keyed ->
                                ReorderableItem(reorderState, key = keyed.key) { _ ->
                                    SongRow(
                                        song = keyed.song,
                                        onSeeDetails = onSeeDetail,
                                        onAddTo = { selectedSong = keyed.song },
                                        showRemoveFromQueue = true,
                                        removeLabel = "Remove from playlist",
                                        onRemoveFromQueue = {
                                            vm.removeSong(playlistId, index)
                                        },
                                        dragHandleModifier = Modifier.draggableHandle()
                                    )
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