package com.pg_axis.musicaxs.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import com.pg_axis.musicaxs.R
import com.pg_axis.musicaxs.models.Song
import com.pg_axis.musicaxs.services.MusicService
import com.pg_axis.musicaxs.settings.FavouritesSave
import com.pg_axis.musicaxs.repositories.PlaylistRepository

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
        ) {
            Text(
                text = "Add to",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
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
                    Text(if (isFav) "Remove from Favourites" else "Add to Favourites")
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
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                playlists.forEach { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.name) },
                        leadingContent = {
                            Icon(painterResource(R.drawable.default_playlist), null, Modifier.size(15.dp))
                        },
                        modifier = Modifier.clickable {
                            PlaylistRepository.getInstance(context).addSong(playlist.id, song.id)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}