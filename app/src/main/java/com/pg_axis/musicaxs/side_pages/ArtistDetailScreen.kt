package com.pg_axis.musicaxs.side_pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pg_axis.musicaxs.PlayerBarDefaults
import com.pg_axis.musicaxs.R
import com.pg_axis.musicaxs.models.Song
import com.pg_axis.musicaxs.tabs.ArtistDetailItem
import com.pg_axis.musicaxs.templates.AddToSheet
import com.pg_axis.musicaxs.templates.SongRow
import kotlinx.coroutines.delay

@Composable
fun ArtistDetailScreen(
    artistName: String,
    onBack: () -> Unit,
    onSeeDetail: (uri: String) -> Unit,
    vm: ArtistDetailViewModel = viewModel()
) {
    val artist by vm.artist.collectAsStateWithLifecycle()
    val items by vm.detailItems.collectAsStateWithLifecycle()
    var selectedSong by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(artistName) { vm.init(artistName) }

    LaunchedEffect(items.isEmpty()) {
        if (items.isEmpty()) {
            delay(3000)
            if (items.isEmpty()) onBack()
        }
    }

    val currentArtist = artist ?: return

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .windowInsetsPadding(WindowInsets.systemBars),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, shape = RoundedCornerShape(0.dp)) {
                Icon(
                    painter = painterResource(R.drawable.back),
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Spacer(Modifier.width(4.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentArtist.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
                Text(
                    text = "${currentArtist.albumCount} ${if (currentArtist.albumCount == 1) "album" else "albums"} · ${currentArtist.songCount} ${if (currentArtist.songCount == 1) "song" else "songs"}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        HorizontalDivider()

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    contentPadding = PaddingValues(top = 8.dp, bottom = PlayerBarDefaults.TotalHeight)
                ) {
                    items(items, key = { item ->
                        when (item) {
                            is ArtistDetailItem.AlbumHeader -> "header_${item.albumName}"
                            is ArtistDetailItem.SongItem -> item.song.id
                        }
                    }) { item ->
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
                            is ArtistDetailItem.SongItem -> SongRow(
                                song = item.song,
                                onSeeDetails = onSeeDetail,
                                onAddTo = { selectedSong = item.song }
                            )
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