package dev.pgaxis.musicaxs.side_pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.models.Song
import dev.pgaxis.musicaxs.tabs.ArtistDetailItem
import dev.pgaxis.musicaxs.templates.AddToSheet
import dev.pgaxis.musicaxs.templates.ListDivider
import dev.pgaxis.musicaxs.templates.SideScreenHeader
import dev.pgaxis.musicaxs.templates.SongRow

@Composable
fun ArtistDetailScreen(
    artistName: String,
    onBack: () -> Unit,
    onSeeDetail: (uri: String) -> Unit,
    vm: ArtistDetailViewModel = viewModel()
) {
    val artist by vm.artist.collectAsStateWithLifecycle()
    val items by vm.detailItems.collectAsStateWithLifecycle()
    val isInitialized by vm.isInitialized.collectAsStateWithLifecycle()
    var selectedSong by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(artistName) { vm.init(artistName) }

    LaunchedEffect(artist) {
        if (artist == null && isInitialized) onBack()
    }

    val currentArtist = artist ?: return

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

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentArtist.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${pluralStringResource(R.plurals.album_count, currentArtist.albumCount, currentArtist.albumCount)} · ${pluralStringResource(R.plurals.song_count, currentArtist.songCount, currentArtist.songCount)}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(items, key = { _, item ->
                        when (item) {
                            is ArtistDetailItem.AlbumHeader -> "header_${item.albumName}"
                            is ArtistDetailItem.SongItem -> item.song.id
                        }
                    }) { index, item ->
                        when (item) {
                            is ArtistDetailItem.AlbumHeader -> Text(
                                text = item.albumName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                            is ArtistDetailItem.SongItem -> {
                                Column {
                                    SongRow(
                                        song = item.song,
                                        onSeeDetails = onSeeDetail,
                                        onAddTo = { selectedSong = item.song }
                                    )

                                    val nextItem = items.getOrNull(index + 1)

                                    if (nextItem !is ArtistDetailItem.AlbumHeader && nextItem != null) {
                                        ListDivider()
                                    }
                                }
                            }
                        }
                    }
                }
                selectedSong?.let { song ->
                    AddToSheet(song = song, onDismiss = { selectedSong = null })
                }
            }
        }
    }
}