package dev.pgaxis.musicaxs.side_pages

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.models.Song
import dev.pgaxis.musicaxs.repositories.SongRepository
import dev.pgaxis.musicaxs.services.MusicService
import dev.pgaxis.musicaxs.templates.AddToSheet
import dev.pgaxis.musicaxs.templates.SongRow
import dev.pgaxis.musicaxs.ui.theme.CyanPrimary
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun QueueScreen(
    onBack: () -> Unit,
    onSeeDetail: (uri: String) -> Unit,
    vm: QueueViewModel = viewModel()
) {
    val listState = rememberLazyListState()
    val currentIndex by vm.currentIndex.collectAsStateWithLifecycle()
    var selectedSong by remember { mutableStateOf<Song?>(null) }

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        vm.onMove(from.index, to.index)
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, shape = RoundedCornerShape(0.dp)) {
                Icon(
                    painter = painterResource(R.drawable.back),
                    contentDescription = "Back",
                    tint = CyanPrimary,
                    modifier = Modifier.size(25.dp)
                )
            }
            Text(
                text = "Queue",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f),
                color = CyanPrimary
            )
        }

        HorizontalDivider()

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(vm.queue, key = { _, item ->
                System.identityHashCode(item)
            }) { index, mediaItem ->
                val uri = mediaItem.localConfiguration?.uri ?: return@itemsIndexed
                val key = System.identityHashCode(mediaItem)

                // Resolve a Song from the MediaItem for SongRow
                val song = remember(uri) {
                    SongRepository.getInstance().songs.value.find { it.uri == uri }
                        ?: Song(
                            id = uri.toString().hashCode().toLong(),
                            title = mediaItem.mediaMetadata.title?.toString() ?: "Unknown",
                            artist = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown",
                            album = "",
                            albumId = 0,
                            uri = uri,
                            durationMs = 0L,
                            albumArtUri = Uri.EMPTY,
                            track = 0
                        )
                }

                ReorderableItem(reorderableState, key = key) { _ ->
                    SongRow(
                        song = song,
                        onSeeDetails = onSeeDetail,
                        onAddTo = { selectedSong = song },
                        isCurrentlyPlaying = index == currentIndex,
                        showRemoveFrom = true,
                        onRemoveFrom = { vm.removeAt(index) },
                        dragHandleModifier = Modifier.draggableHandle(),
                        onClick = {
                            MusicService.playerInstance?.let {
                                it.seekTo(index, 0)
                                it.play()
                            }
                        }
                    )
                }
            }
        }
    }

    selectedSong?.let { song ->
        AddToSheet(song = song, onDismiss = { selectedSong = null })
    }
}