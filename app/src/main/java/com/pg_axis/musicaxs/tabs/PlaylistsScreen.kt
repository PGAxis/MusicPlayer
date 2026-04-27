package com.pg_axis.musicaxs.tabs

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pg_axis.musicaxs.PlayerBarDefaults
import com.pg_axis.musicaxs.R
import com.pg_axis.musicaxs.models.Playlist

data class SmartInfo(
    val first: String,
    val second: Int,
    val third: Uri?,
    val fourth: Long
)

@Composable
fun PlaylistsScreen(
    onOpenPlaylist: (id: String) -> Unit,
    vm: PlaylistsViewModel = viewModel()
) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val recentlyAdded by vm.recentlyAdded.collectAsStateWithLifecycle()
    val recentlyPlayed by vm.recentlyPlayed.collectAsStateWithLifecycle()
    val mostPlayed by vm.mostPlayed.collectAsStateWithLifecycle()

    val smartPlaylists = listOf(
        SmartInfo("Recently Added", recentlyAdded.size, recentlyAdded.firstOrNull()?.uri, 1),
        SmartInfo("Recently Played", recentlyPlayed.size, recentlyPlayed.firstOrNull()?.uri, 2),
        SmartInfo("Most Played", mostPlayed.size, mostPlayed.firstOrNull()?.uri, 3)
    )

    TabSurface {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = PlayerBarDefaults.TotalHeight)
        ) {
            // ── Smart playlist row ────────────────────────────────────────────
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(smartPlaylists) { (name, size, iconUri, id) ->
                        SmartPlaylistCard(
                            name = name,
                            iconUri = iconUri,
                            songCount = size,
                            onClick = { onOpenPlaylist(id.toString()) }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Playlists",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider()
            }

            if (playlists.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No playlists yet.")
                    }
                }
            } else {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        onClick = { onOpenPlaylist(playlist.id.toString()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartPlaylistCard(
    name: String,
    iconUri: Uri?,
    songCount: Int,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(iconUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Album art",
                    error = painterResource(R.drawable.default_cover),
                    placeholder = painterResource(R.drawable.default_cover),
                    fallback = painterResource(R.drawable.default_cover),
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        Text("$songCount songs", fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(playlist.getImageUri())
                .size(48)
                .crossfade(true)
                .build(),
            contentDescription = playlist.name,
            error = painterResource(R.drawable.default_cover),
            placeholder = painterResource(R.drawable.default_cover),
            fallback = painterResource(R.drawable.default_cover),
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        Text(
            text = playlist.name,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "${playlist.getSongCount()} songs",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}