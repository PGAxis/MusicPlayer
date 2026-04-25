package com.pg_axis.musicaxs.tabs

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pg_axis.musicaxs.PlayerBarDefaults
import com.pg_axis.musicaxs.models.Song
import com.pg_axis.musicaxs.R
import com.pg_axis.musicaxs.models.AlphabetScroller
import com.pg_axis.musicaxs.services.MusicService
import com.pg_axis.musicaxs.ui.theme.CyanPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface SongListItem {
    data class Header(val letter: String) : SongListItem
    data class Item(val song: Song) : SongListItem
}

// Returns a flat list of headers + items, and a map of letter → first index in that flat list
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
    vm: SongsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_AUDIO
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.scanSongs()
    }

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
                    Text("Storage permission is required to show your songs.", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permissionLauncher.launch(readPermission) }) {
                        Text("Grant Permission")
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
                        Text("No songs found on this device.")
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
                            contentPadding = PaddingValues(bottom = PlayerBarDefaults.TotalHeight)
                        ) {
                            items(listItems, key = { item ->
                                when (item) {
                                    is SongListItem.Header -> "header_${item.letter}"
                                    is SongListItem.Item -> item.song.id
                                }
                            }) { item ->
                                when (item) {
                                    is SongListItem.Header -> SectionHeader(item.letter)
                                    is SongListItem.Item -> SongRow(
                                        song = item.song,
                                        onClick = {
                                            MusicService.play(context, item.song)
                                        },
                                        onSeeDetails = {
                                            val encoded = Uri.encode(item.song.uri.toString())
                                            goToDetail(encoded)
                                        }
                                    )
                                }
                            }
                        }

                        AlphabetScroller(
                            letters = letters,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(bottom = PlayerBarDefaults.TotalHeight),
                            onLetterSelected = { letter ->
                                activeLetter = letter
                                val index = letterIndex[letter] ?: return@AlphabetScroller
                                scope.launch {
                                    listState.scrollToItem(index)
                                }
                            },
                            onScrubEnd = {
                                scope.launch {
                                    delay(600)
                                    activeLetter = null
                                }
                            }
                        )

                        // Scrub popup — large letter shown in center while dragging
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
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(letter: String) {
    Text(
        text = letter,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = CyanPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun SongRow(
    song: Song,
    onClick: () -> Unit,
    onSeeDetails: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(song.uri)
                .size(44)
                .crossfade(true)
                .build(),
            contentDescription = "Album art",
            error = painterResource(R.drawable.default_cover),
            placeholder = painterResource(R.drawable.default_cover),
            fallback = painterResource(R.drawable.default_cover),
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box {
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = "Song options"
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("See details") },
                    onClick = {
                        expanded = false
                        onSeeDetails()
                    }
                )

                DropdownMenuItem(
                    text = { Text("Add to") },
                    onClick = { expanded = false }
                )

                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { expanded = false }
                )
            }
        }
    }
}