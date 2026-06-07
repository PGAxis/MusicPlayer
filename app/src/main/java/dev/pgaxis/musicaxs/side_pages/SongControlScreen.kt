package dev.pgaxis.musicaxs.side_pages

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.pgaxis.musicaxs.CurrentSong
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.repositories.SongRepository
import dev.pgaxis.musicaxs.services.MusicService
import dev.pgaxis.musicaxs.templates.AddToSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongControlScreen(
    currentSong: CurrentSong,
    artSizeDp: Dp,
    onSeeDetail: (uri: String) -> Unit,
    onCollapse: () -> Unit,
) {
    val context = LocalContext.current

    var menuExpanded  by remember { mutableStateOf(false) }
    var showAddToSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var pendingDelete by remember { mutableStateOf(false) }
    val deleteRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (pendingDelete && result.resultCode == Activity.RESULT_OK) {
            MusicService.removeAllFromQueue(context, currentSong.songUri.toString())
        }
        pendingDelete = false
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val artAndMeta = @Composable {
        // -- Album art placeholder
        Spacer(
            modifier = Modifier.size(artSizeDp)
        )

        Spacer(Modifier.height(if (isLandscape) 1.dp else 15.dp))

        // -- Song info
        Text(currentSong.title, textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(currentSong.artist, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(15.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // -- Header
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onCollapse, shape = RoundedCornerShape(0.dp)) {
                Icon(
                    painter = painterResource(R.drawable.back),
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                    Icon(painterResource(R.drawable.settings), "Song options",
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    DropdownMenuItem(
                        text = { Text("See details", color = MaterialTheme.colorScheme.onSecondaryContainer) },
                        onClick = {
                            menuExpanded = false
                            onSeeDetail(Uri.encode(currentSong.songUri.toString()))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to", color = MaterialTheme.colorScheme.onSecondaryContainer) },
                        onClick = { menuExpanded = false; showAddToSheet = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; showDeleteDialog = true }
                    )
                }
            }
        }

        Spacer(Modifier.height(15.dp))

        if (isLandscape) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    artAndMeta()
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(Modifier.weight(1f))
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                artAndMeta()
            }
        }
    }

    if (showAddToSheet) {
        AddToSheet(
            song = SongRepository.getInstance().resolveSong(currentSong.songUri!!.toUri())!!,
            onDismiss = { showAddToSheet = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete song") },
            text = { Text("\"${currentSong.title}\" will be permanently deleted from your device. This action is irreversible.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                            val intent = MediaStore.createDeleteRequest(
                                context.contentResolver, listOf(currentSong.songUri!!.toUri())
                            )
                            pendingDelete = true
                            deleteRequestLauncher.launch(
                                IntentSenderRequest.Builder(intent.intentSender).build()
                            )
                        }
                        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                            try {
                                val deleted = context.contentResolver.delete(currentSong.songUri!!.toUri(), null, null)
                                if (deleted > 0) MusicService.removeAllFromQueue(context, currentSong.songUri)
                            } catch (e: RecoverableSecurityException) {
                                pendingDelete = true
                                deleteRequestLauncher.launch(
                                    IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                                )
                            }
                        }
                        else -> {
                            val deleted = context.contentResolver.delete(currentSong.songUri!!.toUri(), null, null)
                            if (deleted > 0) MusicService.removeAllFromQueue(context, currentSong.songUri)
                        }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

fun Long.toTimestamp(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}