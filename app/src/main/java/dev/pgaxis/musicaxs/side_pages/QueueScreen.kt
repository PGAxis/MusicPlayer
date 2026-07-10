package dev.pgaxis.musicaxs.side_pages

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.models.Song
import dev.pgaxis.musicaxs.repositories.SongRepository
import dev.pgaxis.musicaxs.services.MusicService
import dev.pgaxis.musicaxs.settings.ShuffleSave
import dev.pgaxis.musicaxs.templates.AddToSheet
import dev.pgaxis.musicaxs.templates.ListDivider
import dev.pgaxis.musicaxs.templates.QueueItemRow
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun QueueScreen(
    onBack: () -> Unit,
    onSeeDetail: (uri: String) -> Unit,
    vm: QueueViewModel = viewModel()
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val currentIndex by vm.currentIndex.collectAsStateWithLifecycle()
    val currentTitle by vm.currentTitle.collectAsStateWithLifecycle()
    val shuffleSave = remember { ShuffleSave.getInstance(context) }
    var selectedSong by remember { mutableStateOf<Song?>(null) }

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        vm.onMove(from.index, to.index)
    }

    LaunchedEffect(Unit) {
        if (currentIndex >= 0) {
            listState.scrollToItem(currentIndex)
        }
    }

    LaunchedEffect(ShuffleSave.getInstance(context).isShuffled) {
        if (currentIndex >= 0) {
            listState.scrollToItem(currentIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            IconButton(
                onClick = onBack,
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    painter = painterResource(R.drawable.back),
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(25.dp)
                )
            }
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.que_scr_playing),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
                Text(
                    text = currentTitle,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.basicMarquee()
                )
            }
        }

        HorizontalDivider()

        key(shuffleSave.isShuffled) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(vm.queueItems, key = { _, item ->
                    "${item.uri}_${item.source}"
                }) { index, item ->
                    ReorderableItem(reorderableState, key = "${item.uri}_${item.source}") { _ ->
                        Column {
                            QueueItemRow(
                                item = item,
                                onSeeDetails = onSeeDetail,
                                onAddTo = {
                                    val song = SongRepository.getInstance().songs.value
                                        .find { it.uri.toString() == item.uri }
                                    song?.let { selectedSong = it }
                                },
                                isPlaying = index == currentIndex,
                                onRemoveFrom = { vm.removeAt(index) },
                                dragHandleModifier = Modifier.draggableHandle(),
                                onClick = {
                                    MusicService.playerInstance?.let {
                                        it.seekTo(index, 0)
                                        it.play()
                                    }
                                }
                            )

                            if (index < vm.queue.lastIndex) {
                                ListDivider()
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    selectedSong?.let { song ->
        AddToSheet(song = song, onDismiss = { selectedSong = null })
    }
}