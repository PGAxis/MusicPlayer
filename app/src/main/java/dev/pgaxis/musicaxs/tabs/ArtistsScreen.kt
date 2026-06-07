package dev.pgaxis.musicaxs.tabs

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pgaxis.musicaxs.LocalPlayerBarTotalHeight
import dev.pgaxis.musicaxs.models.AlphabetScroller
import dev.pgaxis.musicaxs.models.Artist
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.templates.ListDivider
import dev.pgaxis.musicaxs.templates.SectionHeader

private sealed interface ArtistListItem {
    data class Header(val letter: String) : ArtistListItem
    data class Item(val artist: Artist) : ArtistListItem
}

private fun groupArtists(artists: List<Artist>): Pair<List<ArtistListItem>, Map<String, Int>> {
    val items = mutableListOf<ArtistListItem>()
    val letterIndex = mutableMapOf<String, Int>()

    artists
        .groupBy { artist ->
            val first = artist.name.firstOrNull()?.uppercaseChar()
            when {
                first == null -> "#"
                first.isLetter() -> first.toString()
                else -> "#"
            }
        }
        .toSortedMap(compareBy { if (it == "#") "\u0000" else it })
        .forEach { (letter, group) ->
            letterIndex[letter] = items.size
            items.add(ArtistListItem.Header(letter))
            group.forEach { items.add(ArtistListItem.Item(it)) }
        }

    return items to letterIndex
}

@Composable
fun ArtistsScreen(
    onOpenArtist: (name: String) -> Unit,
    vm: ArtistsViewModel = viewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    TabSurface {
        when (val state = uiState) {
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
                        Text(stringResource(R.string.art_scr_no_found))
                    }
                } else {
                    InterpretsListScreen(interprets = state.interprets, onOpenArtist = onOpenArtist)
                }
            }
        }
    }
}

@Composable
private fun InterpretsListScreen(interprets: List<Artist>, onOpenArtist: (name: String) -> Unit) {
    val (listItems, letterIndex) = remember(interprets) { groupArtists(interprets) }
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
                    is ArtistListItem.Header -> "header_${item.letter}"
                    is ArtistListItem.Item -> item.artist.name
                }
            }) { index, item ->
                when (item) {
                    is ArtistListItem.Header -> SectionHeader(item.letter)
                    is ArtistListItem.Item -> {
                        Column {
                            InterpretRow(
                                interpret = item.artist,
                                onClick = { onOpenArtist(Uri.encode(item.artist.name)) }
                            )
                            val nextItem = listItems.getOrNull(index + 1)
                            if (nextItem !is ArtistListItem.Header && nextItem != null) {
                                ListDivider(hasArt = false)
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)),
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
            .padding(horizontal = 16.dp, vertical = 3.dp),
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
                text = "${pluralStringResource(R.plurals.album_count, interpret.albumCount, interpret.albumCount)} · ${pluralStringResource(R.plurals.song_count, interpret.songCount, interpret.songCount)}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1
            )
        }
    }
}