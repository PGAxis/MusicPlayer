package com.pg_axis.musicaxs.side_pages

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pg_axis.musicaxs.R
import com.pg_axis.musicaxs.models.Song
import com.pg_axis.musicaxs.services.MusicService
import com.pg_axis.musicaxs.templates.AddToSheet
import com.pg_axis.musicaxs.templates.SongRow
import com.pg_axis.musicaxs.ui.theme.CyanPrimary

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit,
    onSeeDetail: (uri: String) -> Unit,
    vm: PlaylistsDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedSong by remember { mutableStateOf<Song?>(null) }

    val uiState by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(playlistId) {
        vm.initPlaylist(playlistId)
    }

    when (val state = uiState) {
        is DetailUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is DetailUiState.Ready -> {
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
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.name, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                        Text("${state.songs.size} songs",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Play all button
                    IconButton(onClick = {
                        if (state.songs.isNotEmpty()) MusicService.replaceQueue(context, state.songs)
                    }) {
                        Icon(painterResource(R.drawable.play), "Play all",
                            tint = CyanPrimary, modifier = Modifier.size(28.dp))
                    }
                }

                HorizontalDivider()

                if (state.songs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("This playlist is empty.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item{
                            val validAlbumArtUri = state.songs.first().albumArtUri.takeIf { it != Uri.EMPTY && (it?.lastPathSegment?.toLongOrNull() ?: 0L) > 0L }
                            var useFallbackUri by remember { mutableStateOf(false) }

                            Spacer(Modifier.height(15.dp))

                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(if (useFallbackUri) state.songs.first().uri else validAlbumArtUri)
                                    .size(150)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Album art",
                                error = painterResource(R.drawable.default_cover),
                                placeholder = painterResource(R.drawable.default_cover),
                                fallback = painterResource(R.drawable.default_cover),
                                modifier = Modifier
                                    .size(150.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                ,
                                contentScale = ContentScale.Crop,
                                onError = { if (!useFallbackUri) useFallbackUri = true }
                            )

                            Spacer(Modifier.height(15.dp))
                            HorizontalDivider()
                        }

                        items(state.songs, key = { item ->
                            System.identityHashCode(item)
                        }) { song ->
                            SongRow(
                                song = song,
                                onSeeDetails = onSeeDetail,
                                onAddTo = { selectedSong = song }
                            )
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