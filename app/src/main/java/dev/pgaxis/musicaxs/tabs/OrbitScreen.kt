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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pgaxis.musicaxs.LocalPlayerBarTotalHeight
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.models.AlphabetScroller
import dev.pgaxis.musicaxs.models.OrbitSong
import dev.pgaxis.musicaxs.templates.ListDivider
import dev.pgaxis.musicaxs.templates.SectionHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private sealed interface OrbitSongListItem {
    data class Header(val letter: String) : OrbitSongListItem
    data class Item(val song: OrbitSong) : OrbitSongListItem
}

private fun groupSongs(songs: List<OrbitSong>): Pair<List<OrbitSongListItem>, Map<String, Int>> {
    val items = mutableListOf<OrbitSongListItem>()
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
            items.add(OrbitSongListItem.Header(letter))
            group.forEach { items.add(OrbitSongListItem.Item(it)) }
        }

    return items to letterIndex
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbitScreen(vm: OrbitViewModel = viewModel()) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    val discoveryPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.NEARBY_WIFI_DEVICES
    else
        Manifest.permission.ACCESS_FINE_LOCATION

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.onDiscoveryPermissionGranted()
    }

    TabSurface {
        when (val state = uiState) {
            is OrbitSongsUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is OrbitSongsUiState.PermissionRequired -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.orb_perm_ask),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permissionLauncher.launch(discoveryPermission) }) {
                        Text(stringResource(R.string.song_scr_grant))
                    }
                }
            }

            is OrbitSongsUiState.NoPeers -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.orb_no_peer))
                }
            }

            is OrbitSongsUiState.Ready -> {
                Column(Modifier.fillMaxSize()) {
                    OwnerDropdown(
                        owners = state.owners,
                        selected = state.selectedOwner,
                        onSelect = { vm.selectOwner(it.deviceId) }
                    )
                    OrbitSongList(songs = state.songs)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnerDropdown(
    owners: List<OrbitOwner>,
    selected: OrbitOwner,
    onSelect: (OrbitOwner) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.padding(12.dp)
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.set_scr_library)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(
                    ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true
                )
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            owners.forEach { owner ->
                DropdownMenuItem(
                    text = { Text(owner.displayName) },
                    onClick = {
                        onSelect(owner)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun OrbitSongList(songs: List<OrbitSong>) {
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.orb_empty_lib))
        }
        return
    }

    val (listItems, letterIndex) = remember(songs) { groupSongs(songs) }
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
            itemsIndexed(items = listItems, key = { _, item ->
                when (item) {
                    is OrbitSongListItem.Header -> "header_${item.letter}"
                    is OrbitSongListItem.Item -> item.song.orbitId
                }
            }) { index, item ->
                when (item) {
                    is OrbitSongListItem.Header -> SectionHeader(item.letter)
                    is OrbitSongListItem.Item -> {
                        Column {
                            OrbitSongRow(song = item.song)

                            val nextItem = listItems.getOrNull(index + 1)
                            if (nextItem !is OrbitSongListItem.Header && nextItem != null) {
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
                scope.launch { listState.scrollToItem(index) }
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
private fun OrbitSongRow(song: OrbitSong) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
    }
}