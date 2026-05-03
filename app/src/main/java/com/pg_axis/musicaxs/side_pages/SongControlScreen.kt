package com.pg_axis.musicaxs.side_pages

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pg_axis.musicaxs.CurrentSong
import com.pg_axis.musicaxs.R
import com.pg_axis.musicaxs.services.MusicService
import com.pg_axis.musicaxs.settings.FavouritesSave
import com.pg_axis.musicaxs.ui.theme.CyanPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongControlScreen(
    currentSong: CurrentSong,
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
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()

    var menuExpanded  by remember { mutableStateOf(false) }
    var showAddToSheet by remember { mutableStateOf(false) }

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
                Icon(painterResource(R.drawable.back), "Back",
                    tint = CyanPrimary, modifier = Modifier.size(25.dp))
            }

            Spacer(Modifier.weight(1f))

            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                    Icon(painterResource(R.drawable.settings), "Song options",
                        tint = CyanPrimary, modifier = Modifier.size(25.dp))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("See details") },
                        onClick = {
                            menuExpanded = false
                            onSeeDetail(Uri.encode(currentSong.songUri.toString()))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to") },
                        onClick = { menuExpanded = false; showAddToSheet = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { menuExpanded = false }
                    )
                }
            }
        }

        Spacer(Modifier.height(15.dp))

        // -- Album art placeholder (morphing image lives in ExpandablePlayer)
        Spacer(
            modifier = Modifier
                .padding(horizontal = 50.dp, vertical = 10.dp)
                .aspectRatio(1f)
        )

        Spacer(Modifier.height(15.dp))

        // -- Song info
        Text(currentSong.title, textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold, color = CyanPrimary)
        Text(currentSong.artist, textAlign = TextAlign.Center, color = CyanPrimary)

        Spacer(Modifier.weight(1f))

        // -- Secondary controls
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenQueue, shape = RoundedCornerShape(0.dp)) {
                Icon(painterResource(R.drawable.queue), "Queue",
                    tint = CyanPrimary, modifier = Modifier.size(25.dp))
            }

            Spacer(Modifier.weight(1f))

            val isFav = favourites.isFavourite(currentSong.songUri!!.toUri())
            IconButton(onClick = { vm.onLike() }, shape = RoundedCornerShape(0.dp)) {
                Icon(
                    painterResource(if (isFav) R.drawable.heart_filled else R.drawable.heart_outline),
                    "Like", tint = CyanPrimary, modifier = Modifier.size(25.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            val isShuffled by remember { derivedStateOf { MusicService.isShuffled } }
            IconButton(onClick = { MusicService.toggleShuffle(context) }, shape = RoundedCornerShape(0.dp)) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = "Shuffle",
                    tint = if (isShuffled) CyanPrimary else CyanPrimary.copy(alpha = 0.5f),
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
                    "RepeatType", tint = CyanPrimary, modifier = Modifier.size(25.dp)
                )
            }
        }

        Spacer(Modifier.height(15.dp))

        // -- Progress bar
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (durationMs > 0) positionMs.toTimestamp() else "-:--",
                fontSize = 12.sp, color = CyanPrimary
            )
            Slider(
                value = if (durationMs > 0) positionMs / durationMs.toFloat() else 0f,
                onValueChange = { vm.onScrub((it * durationMs).toLong()) },
                onValueChangeFinished = { vm.onScrubStop() },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                thumb = {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(CyanPrimary))
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(3.dp),
                        colors = SliderDefaults.colors(
                            activeTrackColor   = CyanPrimary,
                            inactiveTrackColor = CyanPrimary.copy(alpha = 0.3f)
                        )
                    )
                }
            )
            Text(
                text = if (durationMs > 0) durationMs.toTimestamp() else "-:--",
                fontSize = 12.sp, color = CyanPrimary
            )
        }

        Spacer(Modifier.height(15.dp))

        // -- Playback controls
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) {
                Icon(painterResource(R.drawable.prev), "Previous",
                    tint = CyanPrimary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.seekBack10() }) {
                Icon(painterResource(R.drawable.rewind), "-10s",
                    tint = CyanPrimary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onPlayPause, modifier = Modifier.size(56.dp)) {
                Icon(
                    painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                    if (isPlaying) "Pause" else "Play",
                    tint = CyanPrimary, modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.seekForward10() }) {
                Icon(painterResource(R.drawable.forward), "+10s",
                    tint = CyanPrimary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onNext) {
                Icon(painterResource(R.drawable.next), "Next",
                    tint = CyanPrimary, modifier = Modifier.size(28.dp))
            }
        }
    }

    if (showAddToSheet) {
        ModalBottomSheet(onDismissRequest = { showAddToSheet = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
            ) {
                Text("Add to", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))

                HorizontalDivider()

                ListItem(
                    headlineContent = { Text("Queue") },
                    leadingContent = {
                        Icon(painterResource(R.drawable.queue), null, Modifier.size(15.dp))
                    },
                    modifier = Modifier.clickable {
                        MusicService.addToQueue(
                            context,
                            vm.resolveSongFromUri(currentSong.songUri!!.toUri())!!
                        )
                        showAddToSheet = false
                    }
                )

                val isFav = favourites.isFavourite(currentSong.songUri!!.toUri())
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
                        if (MusicService.currentUri == currentSong.songUri.toUri())
                            MusicService.like(favourites)
                        else
                            favourites.toggle(currentSong.songUri.toUri(), !isFav)
                        showAddToSheet = false
                    }
                )
            }
        }
    }
}

fun Long.toTimestamp(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}