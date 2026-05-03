package com.pg_axis.musicaxs.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.pg_axis.musicaxs.models.Album

@Composable
fun AlbumsScreen(
    onOpenAlbum: (albumId: String) -> Unit,
    vm: AlbumsViewModel = viewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    TabSurface {
        when (val state = uiState) {
            is AlbumsUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is AlbumsUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${state.message}")
                }
            }
            is AlbumsUiState.Ready -> {
                if (state.albums.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No albums found.")
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start  = 12.dp,
                            end = 12.dp,
                            top = 12.dp,
                            bottom = PlayerBarDefaults.TotalHeight
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.albums, key = { it.id }) { album ->
                            AlbumTile(
                                album = album,
                                onClick = { onOpenAlbum(album.id.toString()) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumTile(
    album: Album,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = album.albumArtUri ?: R.drawable.default_cover,
            contentDescription = "Album art for ${album.name}",
            error = painterResource(R.drawable.default_cover),
            placeholder = painterResource(R.drawable.default_cover),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(25.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = album.name,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artist,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}