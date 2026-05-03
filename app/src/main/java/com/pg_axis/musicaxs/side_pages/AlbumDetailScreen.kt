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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pg_axis.musicaxs.PlayerBarDefaults
import com.pg_axis.musicaxs.R
import com.pg_axis.musicaxs.models.Song
import com.pg_axis.musicaxs.templates.AddToSheet
import com.pg_axis.musicaxs.templates.SongRow
import kotlinx.coroutines.delay

@Composable
fun AlbumDetailScreen(
    albumId: Long,
    onBack: () -> Unit,
    onSeeDetail: (uri: String) -> Unit,
    vm: AlbumDetailViewModel = viewModel()
) {
    val album by vm.album.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    var selectedSong by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(albumId) { vm.init(albumId) }

    LaunchedEffect(songs.isEmpty()) {
        if (songs.isEmpty()) {
            delay(3000)
            if (songs.isEmpty()) onBack()
        }
    }

    val currentAlbum = album ?: return

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

            AsyncImage(
                model = currentAlbum.albumArtUri ?: R.drawable.default_cover,
                error = painterResource(R.drawable.default_cover),
                placeholder = painterResource(R.drawable.default_cover),
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentAlbum.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
                Text(
                    text = "${currentAlbum.artist} · ${currentAlbum.songCount} songs",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        HorizontalDivider()

        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    contentPadding = PaddingValues(top = 8.dp, bottom = PlayerBarDefaults.TotalHeight)
                ) {
                    items(songs, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            onSeeDetails = onSeeDetail,
                            onAddTo = { selectedSong = song },
                            showsImage = false
                        )
                    }
                }
                selectedSong?.let { song ->
                    AddToSheet(song = song, onDismiss = { selectedSong = null })
                }
            }
        }
    }
}