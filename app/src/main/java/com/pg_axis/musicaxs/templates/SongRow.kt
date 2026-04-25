package com.pg_axis.musicaxs.templates

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pg_axis.musicaxs.R
import com.pg_axis.musicaxs.models.Song
import com.pg_axis.musicaxs.services.MusicService
import com.pg_axis.musicaxs.settings.FavouritesSave

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongRow(
    song: Song,
    onSeeDetails: (uri: String) -> Unit,
    showsImage: Boolean = true
) {
    val context = LocalContext.current
    val favourites = remember { FavouritesSave.getInstance(context) }

    var menuExpanded by remember { mutableStateOf(false) }
    var showAddToSheet by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { MusicService.playSingular(context, song) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showsImage) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(song.uri)
                    .size(44)
                    .crossfade(true)
                    .build(),
                contentDescription = "Album art",
                error = painterResource(R.drawable.default_cover),
                placeholder = painterResource(R.drawable.default_cover),
                fallback = painterResource(R.drawable.default_cover),
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = "Song options"
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("See details") },
                    onClick = {
                        menuExpanded = false
                        onSeeDetails(Uri.encode(song.uri.toString()))
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to") },
                    onClick = {
                        menuExpanded = false
                        showAddToSheet = true
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { menuExpanded = false }
                )
            }
        }
    }

    // ── "Add to" bottom sheet ─────────────────────────────────────────────────
    if (showAddToSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddToSheet = false }
        ) {
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

                // Queue
                ListItem(
                    headlineContent = { Text("Queue") },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.queue), // swap for your icon
                            contentDescription = null,
                            Modifier.size(15.dp)
                        )
                    },
                    modifier = Modifier.clickable {
                        MusicService.addToQueue(context, song)
                        showAddToSheet = false
                    }
                )

                // Favourites
                val isFav = favourites.isFavourite(song.uri)
                ListItem(
                    headlineContent = {
                        Text(if (isFav) "Remove from Favourites" else "Add to Favourites")
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(
                                if (isFav) R.drawable.heart_filled
                                else R.drawable.heart_outline
                            ),
                            contentDescription = null,
                            Modifier.size(15.dp)
                        )
                    },
                    modifier = Modifier.clickable {
                        if (MusicService.currentUri == song.uri) {
                            MusicService.like(favourites)
                        } else {
                            favourites.toggle(song.uri, !isFav)
                        }
                        showAddToSheet = false
                    }
                )
            }
        }
    }
}