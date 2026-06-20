package dev.pgaxis.musicaxs.tabs

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pgaxis.musicaxs.LocalPlayerBarTotalHeight
import dev.pgaxis.musicaxs.models.Song
import dev.pgaxis.musicaxs.models.AlphabetScroller
import dev.pgaxis.musicaxs.templates.AddToSheet
import dev.pgaxis.musicaxs.templates.SongRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.templates.ListDivider
import dev.pgaxis.musicaxs.templates.SectionHeader
import kotlin.time.Duration.Companion.milliseconds

private sealed interface SongListItem {
    data class Header(val letter: String) : SongListItem
    data class Item(val song: Song) : SongListItem
}

private fun groupSongs(songs: List<Song>): Pair<List<SongListItem>, Map<String, Int>> {
    val items = mutableListOf<SongListItem>()
    val letterIndex = mutableMapOf<String, Int>()

    songs
        .groupBy { song ->
            val first = song.title.firstOrNull()?.uppercaseChar()
            when {
                first == null -> "#"
                first.isLetter() -> first.toString()
                else -> "#"
            }
        }
        .toSortedMap(compareBy { if (it == "#") "\u0000" else it })
        .forEach { (letter, group) ->
            letterIndex[letter] = items.size
            items.add(SongListItem.Header(letter))
            group.forEach { items.add(SongListItem.Item(it)) }
        }

    return items to letterIndex
}

@Composable
fun SongsScreen(
    goToDetail: (uri: String) -> Unit,
    scanSongs: () -> Unit,
    vm: SongsViewModel = viewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_AUDIO
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.scanSongs(scanSongs)
    }

    var selectedSong by remember { mutableStateOf<Song?>(null) }

    TabSurface {
        when (val state = uiState) {
            is SongsUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is SongsUiState.PermissionRequired -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.song_scr_storage_perm), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permissionLauncher.launch(readPermission) }) {
                        Text(stringResource(R.string.song_scr_grant))
                    }
                }
            }

            is SongsUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${state.message}")
                }
            }

            is SongsUiState.Ready -> {
                if (state.songs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.song_scr_no_found))
                    }
                } else {
                    val (listItems, letterIndex) = remember(state.songs) {
                        groupSongs(state.songs)
                    }
                    val letters = remember(letterIndex) { letterIndex.keys.toList() }

                    val listState = rememberLazyListState()
                    val scope = rememberCoroutineScope()

                    var activeLetter by remember { mutableStateOf<String?>(null) }

                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = 20.dp),
                            contentPadding = PaddingValues(bottom = LocalPlayerBarTotalHeight.current)
                        ) {
                            itemsIndexed(listItems, key = { _, item ->
                                when (item) {
                                    is SongListItem.Header -> "header_${item.letter}"
                                    is SongListItem.Item -> item.song.id
                                }
                            }) { index, item ->
                                when (item) {
                                    is SongListItem.Header -> SectionHeader(item.letter)
                                    is SongListItem.Item -> {
                                        Column {
                                            SongRow(
                                                song = item.song,
                                                onSeeDetails = goToDetail,
                                                onAddTo = { selectedSong = item.song }
                                            )

                                            val nextItem = listItems.getOrNull(index + 1)

                                            if (nextItem !is SongListItem.Header && nextItem != null) {
                                                ListDivider()
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        AlphabetScroller(
                            letters = letters,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(bottom = LocalPlayerBarTotalHeight.current),
                            onLetterSelected = { letter ->
                                activeLetter = letter
                                val index = letterIndex[letter] ?: return@AlphabetScroller
                                scope.launch {
                                    listState.scrollToItem(index)
                                }
                            },
                            onScrubEnd = {
                                scope.launch {
                                    delay(600.milliseconds)
                                    activeLetter = null
                                }
                            }
                        )

                        activeLetter?.let { letter ->
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(90.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = letter,
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        selectedSong?.let { song ->
                            AddToSheet(
                                song = song,
                                onDismiss = { selectedSong = null }
                            )
                        }
                    }
                }
            }
        }
    }
}