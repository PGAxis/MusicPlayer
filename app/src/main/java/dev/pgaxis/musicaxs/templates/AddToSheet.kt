package dev.pgaxis.musicaxs.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.models.Song
import dev.pgaxis.musicaxs.services.MusicService
import dev.pgaxis.musicaxs.settings.FavouritesSave
import dev.pgaxis.musicaxs.repositories.PlaylistRepository
import dev.pgaxis.musicaxs.services.PlaylistToQueue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToSheet(
    song: Song,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val favourites = remember { FavouritesSave.getInstance(context) }
    val playlists = remember { PlaylistRepository.getInstance(context).playlists.value }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Text(
                text = "Add to",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Queue") },
                leadingContent = {
                    Icon(painterResource(R.drawable.queue), null, Modifier.size(15.dp))
                },
                modifier = Modifier.clickable {
                    MusicService.addToQueue(context, song)
                    onDismiss()
                }
            )

            val isFav = favourites.isFavourite(song.uri)
            ListItem(
                headlineContent = {
                    Text(if (isFav) "Remove from Favourites" else "Add to Favourites", color = MaterialTheme.colorScheme.onSecondaryContainer)
                },
                leadingContent = {
                    Icon(
                        painterResource(if (isFav) R.drawable.heart_filled else R.drawable.heart_outline),
                        null, Modifier.size(15.dp)
                    )
                },
                modifier = Modifier.clickable {
                    if (MusicService.currentUri == song.uri) MusicService.like(favourites)
                    else favourites.toggle(song.uri, !isFav)
                    onDismiss()
                }
            )

            if (playlists.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Add to playlist",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                playlists.forEach { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.name, color = MaterialTheme.colorScheme.onSecondaryContainer) },
                        leadingContent = {
                            Icon(painterResource(R.drawable.default_playlist), null, Modifier.size(15.dp))
                        },
                        modifier = Modifier.clickable {
                            PlaylistRepository.getInstance(context).addSong(playlist.id, song.id)
                            PlaylistToQueue(context).addSongIfCurrent(playlist.id, song)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}