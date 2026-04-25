package com.pg_axis.musicaxs.tabs

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pg_axis.musicaxs.PlayerBarDefaults
import com.pg_axis.musicaxs.R
import com.pg_axis.musicaxs.models.AlphabetScroller
import com.pg_axis.musicaxs.models.Artist
import com.pg_axis.musicaxs.templates.SongRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ArtistsScreen(
    goToDetail: (uri: String) -> Unit,
    vm: ArtistsViewModel = viewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val selected by vm.selectedInterpret.collectAsStateWithLifecycle()
    val detail by vm.detailItems.collectAsStateWithLifecycle()

    BackHandler(enabled = selected != null) { vm.onBackFromDetail() }

    TabSurface {
        when {
            selected != null -> ArtistDetailScreen(
                interpret = selected!!,
                items = detail,
                onBack = vm::onBackFromDetail,
                goToDetail = goToDetail
            )

            else -> when (val state = uiState) {
                is ArtistsUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is ArtistsUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error: ${state.message}")
                    }
                }
                is ArtistsUiState.Ready -> {
                    if (state.interprets.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No artists found.")
                        }
                    } else {
                        InterpretsListScreen(interprets = state.interprets, vm = vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun InterpretsListScreen(interprets: List<Artist>, vm: ArtistsViewModel) {
    // Reuse SongsScreen's flat-list + letter grouping approach
    data class ListItem(val letter: String?, val interpret: Artist)

    val flatItems = remember(interprets) {
        val result = mutableListOf<ListItem>()
        interprets
            .groupBy { i ->
                val f = i.name.firstOrNull()?.uppercaseChar()
                when {
                    f == null -> "#"
                    f.isLetter() -> f.toString()
                    else -> "#"
                }
            }
            .toSortedMap(compareBy { if (it == "#") "\u0000" else it })
            .forEach { (letter, group) ->
                result.add(ListItem(letter, group.first()))
                group.drop(1).forEach { result.add(ListItem(null, it)) }
            }
        result
    }

    val letterIndex = remember(flatItems) {
        val map = mutableMapOf<String, Int>()
        flatItems.forEachIndexed { index, item ->
            if (item.letter != null) map[item.letter] = index
        }
        map
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
            items(flatItems, key = { "${it.letter}_${it.interpret.name}" }) { item ->
                if (item.letter != null) {
                    Text(
                        text = item.letter,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                InterpretRow(
                    interpret = item.interpret,
                    onClick = { vm.onInterpretSelected(item.interpret) }
                )
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
                scope.launch { listState.scrollToItem(index) }
            },
            onScrubEnd = {
                scope.launch {
                    delay(600)
                    activeLetter = null
                }
            }
        )

        activeLetter?.let { letter ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(90.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
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

@Composable
private fun InterpretRow(interpret: Artist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = interpret.name,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${interpret.albumCount} ${if (interpret.albumCount == 1) "album" else "albums"} · ${interpret.songCount} ${if (interpret.songCount == 1) "song" else "songs"}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ArtistDetailScreen(
    interpret: Artist,
    items: List<ArtistDetailItem>,
    onBack: () -> Unit,
    goToDetail: (uri: String) -> Unit
) {

    Column(Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, shape = RoundedCornerShape(0.dp)) {
                Icon(
                    painter = painterResource(R.drawable.back),
                    contentDescription = "Back"
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = interpret.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${interpret.albumCount} ${if (interpret.albumCount == 1) "album" else "albums"} · ${interpret.songCount} ${if (interpret.songCount == 1) "song" else "songs"}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        HorizontalDivider()

        // ── Song list grouped by album ─────────────────────────────────────────
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = PlayerBarDefaults.TotalHeight
                )
            ) {
                items(items, key = { item ->
                    when (item) {
                        is ArtistDetailItem.AlbumHeader -> "header_${item.albumName}"
                        is ArtistDetailItem.SongItem -> item.song.id
                    }
                }) { item ->
                    when (item) {
                        is ArtistDetailItem.AlbumHeader -> {
                            Text(
                                text = item.albumName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        is ArtistDetailItem.SongItem -> {
                            SongRow(
                                song = item.song,
                                onSeeDetails = goToDetail
                            )
                        }
                    }
                }
            }
        }
    }
}