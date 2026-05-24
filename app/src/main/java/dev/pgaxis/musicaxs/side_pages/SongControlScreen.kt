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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pgaxis.musicaxs.CurrentSong
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.repositories.SongRepository
import dev.pgaxis.musicaxs.services.MusicService
import dev.pgaxis.musicaxs.settings.FavouritesSave
import dev.pgaxis.musicaxs.templates.AddToSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongControlScreen(
    currentSong: CurrentSong,
    artSizeDp: Dp,
    onSeeDetail: (uri: String) -> Unit,
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    vm: SongControlViewModel = viewModel()
) {
    val context = LocalContext.current
    val favourites = remember { FavouritesSave.getInstance(context) }
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val positionMs by vm.positionMs.collectAsStateWithLifecycle()
    val durationMs by vm.durationMs.collectAsStateWithLifecycle()
    val isPlaying by MusicService.isPlayingState.collectAsStateWithLifecycle()

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

    val controls = @Composable fun (modifier: Modifier) {
        // -- Secondary controls
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenQueue, shape = RoundedCornerShape(0.dp)) {
                Icon(painterResource(R.drawable.queue), "Queue",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
            }

            Spacer(Modifier.weight(1f))

            val isFav = favourites.isFavourite(currentSong.songUri!!.toUri())
            IconButton(onClick = { vm.onLike() }, shape = RoundedCornerShape(0.dp)) {
                Icon(
                    painterResource(if (isFav) R.drawable.heart_filled else R.drawable.heart_outline),
                    "Like", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            val isShuffled by remember { derivedStateOf { MusicService.isShuffled } }
            IconButton(onClick = { MusicService.toggleShuffle(context) }, shape = RoundedCornerShape(0.dp)) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = "Shuffle",
                    tint = if (isShuffled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(25.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            IconButton(onClick = { vm.changePlayType() }, shape = RoundedCornerShape(0.dp)) {
                Icon(
                    painterResource(when (uiState) {
                        PlayType.Repeat -> R.drawable.repeat
                        PlayType.RepeatOnce -> R.drawable.repeat_once
                        PlayType.Continue -> R.drawable.continue_play
                    }),
                    "RepeatType", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp)
                )
            }
        }

        Spacer(modifier = modifier)

        // -- Progress bar
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (durationMs > 0) positionMs.toTimestamp() else "-:--",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.primary
            )
            Slider(
                value = if (durationMs > 0) positionMs / durationMs.toFloat() else 0f,
                onValueChange = { vm.onScrub((it * durationMs).toLong()) },
                onValueChangeFinished = { vm.onScrubStop() },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                thumb = {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(3.dp),
                        colors = SliderDefaults.colors(
                            activeTrackColor   = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    )
                }
            )
            Text(
                text = if (durationMs > 0) durationMs.toTimestamp() else "-:--",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = modifier)

        // -- Playback controls
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    onPrevious()
                    vm.setTime()
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.prev),
                    contentDescription = "Previous",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.seekBack10() }) {
                Icon(
                    painter = painterResource(R.drawable.rewind),
                    contentDescription = "-10s",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onPlayPause, modifier = Modifier.size(56.dp)) {
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.seekForward10() }) {
                Icon(
                    painter = painterResource(R.drawable.forward),
                    contentDescription = "+10s",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = {
                    onNext()
                    vm.setTime()
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.next),
                    contentDescription = "Next",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
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
                    controls(Modifier.weight(1f))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                artAndMeta()
                Spacer(Modifier.weight(1f))
                controls(Modifier.height(15.dp))
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