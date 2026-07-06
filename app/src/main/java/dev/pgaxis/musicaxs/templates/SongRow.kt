package dev.pgaxis.musicaxs.templates

import android.annotation.SuppressLint
import android.app.Activity
import android.app.RecoverableSecurityException
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.models.Song
import dev.pgaxis.musicaxs.services.MusicService
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("ModifierParameter")
@Composable
fun SongRow(
    song: Song,
    onSeeDetails: (uri: String) -> Unit,
    onAddTo: () -> Unit,
    showsImage: Boolean = true,
    showRemoveFrom: Boolean = false,
    onRemoveFrom: () -> Unit = {},
    dragHandleModifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val isSongPlaying by MusicService.isPlayingState.collectAsStateWithLifecycle()
    val currentUri by MusicService.currentUriState.collectAsStateWithLifecycle()

    var pendingDelete by remember { mutableStateOf(false) }
    val isCurrentlyPlaying = currentUri == song.uri

    val deleteRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (pendingDelete && result.resultCode == Activity.RESULT_OK) {
            MusicService.removeAllFromQueue(context, song.uri.toString())
        }
        pendingDelete = false
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrentlyPlaying)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else Color.Transparent
            )
            .clickable { MusicService.playSingular(context, song) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        if (showsImage) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(song.uri)
                    .size(64, 64)
                    .crossfade(false)
                    .dispatcher(Dispatchers.IO.limitedParallelism(8))
                    .build(),
                contentDescription = "Album art",
                error = painterResource(R.drawable.default_cover),
                placeholder = painterResource(R.drawable.default_cover),
                fallback = painterResource(R.drawable.default_cover),
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (showRemoveFrom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = song.artist,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (showRemoveFrom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        if (isCurrentlyPlaying) {
            EqualizerBars(
                isPlaying = isSongPlaying,
                modifier = Modifier.width(16.dp)
            )
        }

        // Drag handle — only shown in queue/playlist
        if (showRemoveFrom) {
            Icon(
                painter = painterResource(R.drawable.drag_handle),
                contentDescription = "Reorder",
                modifier = dragHandleModifier
                    .size(24.dp)
                    .padding(4.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = "Song options",
                    tint = if (showRemoveFrom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.see_details), color = MaterialTheme.colorScheme.onSecondaryContainer) },
                    onClick = {
                        menuExpanded = false
                        onSeeDetails(Uri.encode(song.uri.toString()))
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.add_to), color = MaterialTheme.colorScheme.onSecondaryContainer) },
                    onClick = { menuExpanded = false; onAddTo() }
                )
                if (showRemoveFrom) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rm_from_playlist), color = MaterialTheme.colorScheme.onSecondaryContainer) },
                        onClick = { menuExpanded = false; onRemoveFrom() }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                    onClick = { menuExpanded = false; showDeleteDialog = true }
                )
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(stringResource(R.string.song_row_delete), color = MaterialTheme.colorScheme.onSecondaryContainer) },
                text = { Text(stringResource(R.string.song_row_delete_desc, song.title)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                                val intent = MediaStore.createDeleteRequest(
                                    context.contentResolver, listOf(song.uri)
                                )
                                pendingDelete = true
                                deleteRequestLauncher.launch(
                                    IntentSenderRequest.Builder(intent.intentSender).build()
                                )
                            }
                            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                                try {
                                    val deleted = context.contentResolver.delete(song.uri, null, null)
                                    if (deleted > 0) MusicService.removeAllFromQueue(context, song.uri.toString())
                                } catch (e: RecoverableSecurityException) {
                                    pendingDelete = true
                                    deleteRequestLauncher.launch(
                                        IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                                    )
                                }
                            }
                            else -> {
                                val deleted = context.contentResolver.delete(song.uri, null, null)
                                if (deleted > 0) MusicService.removeAllFromQueue(context, song.uri.toString())
                            }
                        }
                    }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    }
}