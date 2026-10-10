package dev.pgaxis.musicaxs.templates

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.funcs.placeholderUri
import dev.pgaxis.musicaxs.models.OrbitSong
import dev.pgaxis.musicaxs.services.MusicService

@Composable
fun OrbitSongRow(song: OrbitSong) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val isSongPlaying by MusicService.isPlayingState.collectAsStateWithLifecycle()
    val currentUri by MusicService.currentUriState.collectAsStateWithLifecycle()
    val isCurrentlyPlaying = currentUri == remember(song.orbitId) { song.placeholderUri() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrentlyPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else Color.Transparent
            )
            .clickable { MusicService.playSingular(song) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        // Album art for shared songs is feature #1; default cover until then.
        Image(
            painter = painterResource(R.drawable.default_cover),
            contentDescription = "Album art",
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = song.artist,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        if (isCurrentlyPlaying) {
            EqualizerBars(isPlaying = isSongPlaying, modifier = Modifier.width(16.dp))
        }

        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = "Song options",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.add_to_queue),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        MusicService.addToQueue(context, song)
                    }
                )
            }
        }
    }
}