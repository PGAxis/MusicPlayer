package dev.pgaxis.musicaxs.templates

import android.annotation.SuppressLint
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
import dev.pgaxis.musicaxs.ui.theme.CyanPrimary
import kotlinx.coroutines.Dispatchers

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
    removeLabel: String = "Remove from queue",
    onRemoveFromQueue: () -> Unit = {},
    dragHandleModifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val isPlaying by MusicService.isPlayingState.collectAsStateWithLifecycle()

    val deleteRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { }

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
        if (isCurrentlyPlaying) {
            EqualizerBars(
                isPlaying = isPlaying,
                modifier = Modifier.width(16.dp)
            )
        } else if (showRemoveFromQueue) {
            Spacer(Modifier.width(16.dp))
        }

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
                        text = { Text(removeLabel) },
                        onClick = { menuExpanded = false; onRemoveFromQueue() }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { menuExpanded = false; showDeleteDialog = true }
                )
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete song") },
                text = { Text("\"${song.title}\" will be permanently deleted from your device. This action is irreversible.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                                val intent = MediaStore.createDeleteRequest(
                                    context.contentResolver, listOf(song.uri)
                                )
                                deleteRequestLauncher.launch(
                                    IntentSenderRequest.Builder(intent.intentSender).build()
                                )
                            }
                            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                                try {
                                    context.contentResolver.delete(song.uri, null, null)
                                } catch (e: RecoverableSecurityException) {
                                    deleteRequestLauncher.launch(
                                        IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                                    )
                                }
                            }
                            else -> context.contentResolver.delete(song.uri, null, null)
                        }
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}