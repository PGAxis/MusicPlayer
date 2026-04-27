package com.pg_axis.musicaxs.templates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pg_axis.musicaxs.PlayerBarDefaults
import com.pg_axis.musicaxs.R
import com.pg_axis.musicaxs.models.Playlist
import com.pg_axis.musicaxs.models.Song


@Composable
fun FavouritesDetailScreen(
    playlist: Playlist,
    songs: List<Song>,
    onBack: () -> Unit,
    onShowDetails: (uri: String) -> Unit
) {
    var selectedSong by remember { mutableStateOf<Song?>(null) }

    Column(Modifier.fillMaxSize()) {
        // ── Header ────────────────────────────────────────────────────────────
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

            AsyncImage(
                model = playlist.getImageUri(),
                error = painterResource(R.drawable.default_cover),
                placeholder = painterResource(R.drawable.default_cover),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (playlist.getSongCount() != 1) "${playlist.getSongCount()} songs" else "${playlist.getSongCount()} song",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        HorizontalDivider()

        // ── Song list ─────────────────────────────────────────────────────────
        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = PlayerBarDefaults.TotalHeight
                    )
                ) {
                    items(songs, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            onSeeDetails = onShowDetails,
                            onAddTo = { selectedSong = song }
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