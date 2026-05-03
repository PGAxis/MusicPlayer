package com.pg_axis.musicaxs.side_pages

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pg_axis.musicaxs.R
import com.pg_axis.musicaxs.models.Song
import com.pg_axis.musicaxs.repositories.PlaylistRepository
import com.pg_axis.musicaxs.templates.AddToSheet
import com.pg_axis.musicaxs.templates.SongRow
import com.pg_axis.musicaxs.ui.theme.CyanPrimary

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onSeeDetail: (uri: String) -> Unit,
    choosingForPlaylistId: Long? = null,
    vm: SearchViewModel = viewModel()
) {
    val context = LocalContext.current
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.searched.collectAsStateWithLifecycle()
    val repo = remember { PlaylistRepository.getInstance(context) }

    val isChoosing = choosingForPlaylistId != null
    val selectedSongs = remember { mutableStateListOf<Song>() }
    var selectedSong by remember { mutableStateOf<Song?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
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
            OutlinedTextField(
                value = query,
                onValueChange = vm::onQueryChange,
                placeholder = { Text("Search…") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp)
            )
            if (isChoosing) {
                IconButton(onClick = {
                    selectedSongs.forEach { song ->
                        repo.addSong(choosingForPlaylistId, song.id)
                    }
                    onBack()
                }) {
                    Icon(painterResource(R.drawable.check), "Confirm", tint = CyanPrimary)
                }
            }
        }

        HorizontalDivider()

        if (query.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Start typing to search…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {

                // Songs section
                if (results.songs.isNotEmpty()) {
                    item {
                        SectionHeader("Songs")
                    }
                    val songsToShow = if (isChoosing) results.songs else results.songs.take(5)
                    items(songsToShow, key = { it.id }) { song ->
                        if (isChoosing) {
                            val isSelected = selectedSongs.any { it.id == song.id }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) selectedSongs.removeAll { it.id == song.id }
                                        else selectedSongs.add(song)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        if (isSelected) selectedSongs.removeAll { it.id == song.id }
                                        else selectedSongs.add(song)
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
                    }
                }

                // Artists section
                if (!isChoosing && results.artists.isNotEmpty()) {
                    item { SectionHeader("Artists") }
                    items(results.artists.take(5), key = { "artist_${it.name}" }) { artist ->
                        ListItem(
                            headlineContent = { Text(artist.name, fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("${artist.songs.size} songs") }
                        )
                    }
                }

                // Albums section
                if (!isChoosing && results.albums.isNotEmpty()) {
                    item { SectionHeader("Albums") }
                    items(results.albums.take(5), key = { "album_${it.name}" }) { album ->
                        ListItem(
                            headlineContent = { Text(album.name, fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("${album.songs.size} songs") },
                            leadingContent = {
                                AsyncImage(
                                    model = album.artUri,
                                    contentDescription = null,
                                    error = painterResource(R.drawable.default_cover),
                                    placeholder = painterResource(R.drawable.default_cover),
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
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
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}