package com.pg_axis.musicaxs.templates

import android.annotation.SuppressLint
import android.net.Uri
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pg_axis.musicaxs.R
import com.pg_axis.musicaxs.models.Song
import com.pg_axis.musicaxs.services.MusicService
import com.pg_axis.musicaxs.ui.theme.CyanPrimary

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("ModifierParameter")
@Composable
fun SongRow(
    song: Song,
    onSeeDetails: (uri: String) -> Unit,
    onAddTo: () -> Unit,
    showsImage: Boolean = true,
    isCurrentlyPlaying: Boolean = false,
    showRemoveFromQueue: Boolean = false,
    onRemoveFromQueue: () -> Unit = {},
    dragHandleModifier: Modifier = Modifier,   // caller applies .draggableHandle() here
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrentlyPlaying)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .clickable { onClick?.invoke() ?: MusicService.playSingular(context, song) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Currently playing indicator
        if (isCurrentlyPlaying) {
            EqualizerBars(modifier = Modifier.width(16.dp))
        } else if (showRemoveFromQueue) {
            // Spacer to keep alignment consistent in queue
            Spacer(Modifier.width(16.dp))
        }

        if (showsImage) {
            val validAlbumArtUri = song.albumArtUri.takeIf { it != Uri.EMPTY && (it?.lastPathSegment?.toLongOrNull() ?: 0L) > 0L }
            var useFallbackUri by remember(song.id) { mutableStateOf(false) }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(if (useFallbackUri) song.uri else validAlbumArtUri)
                    .size(44)
                    .memoryCacheKey("art_${song.id}_$useFallbackUri")
                    .diskCacheKey("art_${song.id}_$useFallbackUri")
                    .crossfade(true)
                    .build(),
                contentDescription = "Album art",
                error = painterResource(R.drawable.default_cover),
                placeholder = painterResource(R.drawable.default_cover),
                fallback = painterResource(R.drawable.default_cover),
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
                onError = { if (!useFallbackUri) useFallbackUri = true }
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (showRemoveFromQueue) CyanPrimary else Color.White
            )
            Text(
                text = song.artist,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (showRemoveFromQueue) CyanPrimary else Color.White
            )
        }

        // Drag handle — only shown in queue
        if (showRemoveFromQueue) {
            Icon(
                painter = painterResource(R.drawable.drag_handle),
                contentDescription = "Reorder",
                modifier = dragHandleModifier
                    .size(24.dp)
                    .padding(4.dp),
                tint = CyanPrimary
            )
        }

        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = "Song options",
                    tint = if (showRemoveFromQueue) CyanPrimary else Color.White
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("See details") },
                    onClick = {
                        menuExpanded = false
                        onSeeDetails(Uri.encode(song.uri.toString()))
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to") },
                    onClick = { menuExpanded = false; onAddTo() }
                )
                if (showRemoveFromQueue) {
                    DropdownMenuItem(
                        text = { Text("Remove from queue") },
                        onClick = { menuExpanded = false; onRemoveFromQueue() }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { menuExpanded = false }
                )
            }
        }
    }
}