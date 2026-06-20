package dev.pgaxis.musicaxs.templates

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.models.QueueItem
import dev.pgaxis.musicaxs.models.QueueItemSource
import dev.pgaxis.musicaxs.services.MusicService
import kotlinx.coroutines.Dispatchers
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("ModifierParameter")
@Composable
fun QueueItemRow(
    item: QueueItem,
    isPlaying: Boolean,
    onSeeDetails: (uri: String) -> Unit,
    onAddTo: () -> Unit,
    onRemoveFrom: () -> Unit,
    dragHandleModifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    val isSongPlaying by MusicService.isPlayingState.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else Color.Transparent
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Log.d("QueueItemRow", "albumArtUri for '${item.title}': ${item.albumArtUri}")
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(
                    if (item.source == QueueItemSource.LOCAL) item.uri.toUri()
                    else item.albumArtUri?.toUri()
                )
                .crossfade(false)
                .dispatcher(Dispatchers.IO.limitedParallelism(8))
                .build(),
            contentDescription = "Art",
            error = painterResource(R.drawable.default_cover),
            placeholder = painterResource(R.drawable.default_cover),
            fallback = painterResource(R.drawable.default_cover),
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = item.artist,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (isPlaying) {
            EqualizerBars(
                isPlaying = isSongPlaying,
                modifier = Modifier.width(16.dp)
            )
        }

        Icon(
            painter = painterResource(R.drawable.drag_handle),
            contentDescription = "Reorder",
            modifier = dragHandleModifier
                .size(24.dp)
                .padding(4.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = "Item options",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                if (item.source == QueueItemSource.LOCAL) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.see_details), color = MaterialTheme.colorScheme.onSecondaryContainer) },
                        onClick = {
                            menuExpanded = false
                            onSeeDetails(Uri.encode(item.uri))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_to), color = MaterialTheme.colorScheme.onSecondaryContainer) },
                        onClick = { menuExpanded = false; onAddTo() }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rm_from_queue), color = MaterialTheme.colorScheme.onSecondaryContainer) },
                    onClick = { menuExpanded = false; onRemoveFrom() }
                )
            }
        }
    }
}