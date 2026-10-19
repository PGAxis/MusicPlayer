package dev.pgaxis.musicaxs.side_pages

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.models.Song
import dev.pgaxis.musicaxs.repositories.PlaylistRepository
import dev.pgaxis.musicaxs.templates.AddToSheet
import dev.pgaxis.musicaxs.templates.ListDivider
import dev.pgaxis.musicaxs.templates.SideScreenHeader
import dev.pgaxis.musicaxs.templates.SongRow

enum class Expanded {
    SONGS, ARTISTS, ALBUMS, NONE
}

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onScan: (() -> Unit) = { },
    onSeeDetail: (uri: String) -> Unit,
    onOpenArtist: (name: String) -> Unit,
    onOpenAlbum: (albumId: String) -> Unit,
    choosingForPlaylistId: Long? = null,
    isChoosingForFilter: Boolean = false,
    initialSongs: List<Song> = emptyList(),
    vm: SearchViewModel = viewModel()
) {
    LaunchedEffect(isChoosingForFilter) {
        vm.setIncludeFiltered(isChoosingForFilter)
    }

    val context = LocalContext.current
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.searched.collectAsStateWithLifecycle()
    val repo = remember { PlaylistRepository.getInstance(context) }

    val isChoosing = choosingForPlaylistId != null || isChoosingForFilter
    val selectedSongs = rememberSaveable {
        mutableStateListOf<Song>().apply { addAll(initialSongs) }
    }
    var selectedSong by rememberSaveable { mutableStateOf<Song?>(null) }
    var expandedPart by rememberSaveable { mutableStateOf(Expanded.NONE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        SideScreenHeader(
            onBack = onBack
        ) {
            Spacer(Modifier.width(4.dp))

            OutlinedTextField(
                value = query,
                onValueChange = vm::onQueryChange,
                placeholder = { Text(stringResource(R.string.search_scr_search)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp)
            )
            if (isChoosing) {
                IconButton(onClick = {
                    if (choosingForPlaylistId != null) {
                        selectedSongs.forEach { song ->
                            repo.addSong(choosingForPlaylistId, song.id)
                        }
                    }
                    else {
                        vm.settings.filteredSongs = selectedSongs.map { it.id }
                        onScan()
                    }
                    onBack()
                }) {
                    Icon(painterResource(R.drawable.check), "Confirm", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (query.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.search_scr_search_long), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Songs section
                if (results.songs.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.search_scr_songs),
                            expanded = expandedPart == Expanded.SONGS,
                            showIcon = results.songs.size > 5
                        ) {
                            expandedPart = if (expandedPart != Expanded.SONGS) Expanded.SONGS
                            else Expanded.NONE
                        }
                    }
                    val songsToShow = if (isChoosing || expandedPart == Expanded.SONGS) results.songs else results.songs.take(5)
                    itemsIndexed(songsToShow, key = { _, it -> it.id }) { index, song ->
                        Column {
                            if (isChoosing) {
                                val isSelected = selectedSongs.any { it.id == song.id }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isSelected) {
                                                selectedSongs.removeAll { it.id == song.id }
                                            } else {
                                                selectedSongs.add(song)
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            if (isSelected) {
                                                selectedSongs.removeAll { it.id == song.id }
                                            } else {
                                                selectedSongs.add(song)
                                            }
                                        }
                                    )

                                    Spacer(Modifier.width(8.dp))

                                    SongRow(
                                        song = song,
                                        onSeeDetails = onSeeDetail,
                                        onAddTo = { selectedSong = song }
                                    )
                                }
                            } else {
                                SongRow(
                                    song = song,
                                    onSeeDetails = onSeeDetail,
                                    onAddTo = { selectedSong = song }
                                )
                            }

                            if (index < songsToShow.lastIndex) {
                                ListDivider()
                            }
                        }
                    }
                }

                // Artists section
                if (!isChoosing && results.artists.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))

                        SectionHeader(
                            title = stringResource(R.string.search_scr_artists),
                            expanded = expandedPart == Expanded.ARTISTS,
                            showIcon = results.artists.size > 5
                        ) {
                            expandedPart = if (expandedPart != Expanded.ARTISTS) Expanded.ARTISTS
                            else Expanded.NONE
                        }
                    }
                    items(items = if (expandedPart == Expanded.ARTISTS) results.artists else results.artists.take(5), key = { "artist_${it.name}" }) { artist ->
                        ListItem(
                            headlineContent = { Text(artist.name, fontWeight = FontWeight.Medium) },
                            supportingContent = { Text(pluralStringResource(R.plurals.song_count, artist.songCount, artist.songCount)) },
                            modifier = Modifier.clickable { onOpenArtist(Uri.encode(artist.name)) }
                        )
                    }
                }

                // Albums section
                if (!isChoosing && results.albums.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))

                        SectionHeader(
                            title = stringResource(R.string.search_scr_albums),
                            expanded = expandedPart == Expanded.ALBUMS,
                            showIcon = results.albums.size > 5
                        ) {
                            expandedPart = if (expandedPart != Expanded.ALBUMS) Expanded.ALBUMS
                            else Expanded.NONE
                        }
                    }
                    items(items = if (expandedPart == Expanded.ALBUMS) results.albums else results.albums.take(5), key = { "album_${it.name}" }) { album ->
                        ListItem(
                            headlineContent = { Text(album.name, fontWeight = FontWeight.Medium) },
                            supportingContent = { Text(pluralStringResource(R.plurals.song_count, album.size, album.size)) },
                            leadingContent = {
                                AsyncImage(
                                    model = album.artUri,
                                    contentDescription = null,
                                    error = painterResource(R.drawable.default_cover),
                                    placeholder = painterResource(R.drawable.default_cover),
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            },
                            modifier = Modifier.clickable { onOpenAlbum(album.id.toString()) }
                        )
                    }
                }
            }
        }
    }

    selectedSong?.let { song ->
        AddToSheet(song = song, onDismiss = { selectedSong = null })
    }
}

@Composable
private fun SectionHeader(title: String, expanded: Boolean, showIcon: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painterResource(if (expanded) R.drawable.expand_less else R.drawable.expand_more),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp).alpha(if (showIcon) 1f else 0f)
        )
    }
}