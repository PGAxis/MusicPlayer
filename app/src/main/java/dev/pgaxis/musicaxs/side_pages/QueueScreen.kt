package dev.pgaxis.musicaxs.side_pages

import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.models.QueueItemSource
import dev.pgaxis.musicaxs.models.Song
import dev.pgaxis.musicaxs.repositories.SongRepository
import dev.pgaxis.musicaxs.services.MusicService
import dev.pgaxis.musicaxs.settings.ShuffleSave
import dev.pgaxis.musicaxs.templates.AddToSheet
import dev.pgaxis.musicaxs.templates.BounceMarqueeText
import dev.pgaxis.musicaxs.templates.ListDivider
import dev.pgaxis.musicaxs.templates.QueueItemRow
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs

@Composable
fun QueueScreen(
    onBack: () -> Unit,
    onSeeDetail: (uri: String) -> Unit,
    vm: QueueViewModel = viewModel()
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val currentIndex by vm.currentIndex.collectAsStateWithLifecycle()
    val lastIndex by vm.lastIndex.collectAsStateWithLifecycle()
    val currentTitle by vm.currentTitle.collectAsStateWithLifecycle()
    val availableOwners by vm.availableOwners.collectAsStateWithLifecycle()
    val shuffleSave = remember { ShuffleSave.getInstance(context) }
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var keyedQueueItems by remember {
        mutableStateOf(vm.queueItems.mapIndexed { i, item -> i.toLong() to item })
    }

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        keyedQueueItems = keyedQueueItems.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        vm.onMove(from.index, to.index)
    }

    var userHasScrolled by remember { mutableStateOf(false) }

    LaunchedEffect(vm.queueItems.size, shuffleSave.isShuffled) {
        keyedQueueItems = vm.queueItems.mapIndexed { i, item -> i.toLong() to item }
    }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                userHasScrolled = true
            }
        }
    }

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && !userHasScrolled) {
            if (abs(currentIndex - lastIndex) > 10) listState.scrollToItem(currentIndex)
            else listState.animateScrollToItem(currentIndex)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (currentIndex >= 0) {
                        userHasScrolled = false
                        coroutineScope.launch {
                            listState.scrollToItem(currentIndex)
                        }
                    }
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp)
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            IconButton(
                onClick = onBack,
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.align(Alignment.CenterStart).size(45.dp).padding(horizontal = 5.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.back),
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 45.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.que_scr_playing),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
                BounceMarqueeText(
                    text = currentTitle,
                    style = TextStyle(
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        HorizontalDivider()

        key(shuffleSave.isShuffled) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(keyedQueueItems, key = { _, keyed -> keyed.first }) { index, keyed ->
                    val item = keyed.second
                    val unavailable = item.source == QueueItemSource.GUEST && item.deviceId !in availableOwners
                    ReorderableItem(reorderableState, key = keyed.first) { _ ->
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
                                unavailable = unavailable,
                                onClick = {
                                    if (!unavailable) MusicService.playerInstance?.let {
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