package dev.pgaxis.musicaxs.side_pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pgaxis.musicaxs.LocalPlayerBarTotalHeight
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.models.PodcastEpisode
import dev.pgaxis.musicaxs.services.MusicService
import dev.pgaxis.musicaxs.templates.ListDivider

@Composable
fun PodcastDetailScreen(
    feedUrl: String,
    onBack: () -> Unit,
    vm: PodcastDetailViewModel = viewModel()
) {
    val feed by vm.feed.collectAsStateWithLifecycle()
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val downloading by vm.downloading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    LaunchedEffect(feedUrl) { vm.init(feedUrl) }

    val currentFeed = feed ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, shape = RoundedCornerShape(0.dp), modifier = Modifier.size(45.dp).padding(horizontal = 5.dp)) {
                Icon(
                    painterResource(R.drawable.back), "Back",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = currentFeed.title,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { MusicService.playShuffledPodcast(episodes, currentFeed) },
                enabled = episodes.isNotEmpty(),
                shape = RoundedCornerShape(0.dp)
            ) {
                Icon(
                    painterResource(R.drawable.shuffle), "Play shuffled",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            IconButton(
                onClick = { MusicService.replaceQueuePodcast(episodes, currentFeed) },
                enabled = episodes.isNotEmpty(),
                shape = RoundedCornerShape(0.dp)
            ) {
                Icon(
                    painterResource(R.drawable.play), "Play all",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        HorizontalDivider()

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error)
            }
            episodes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.podcasts_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = LocalPlayerBarTotalHeight.current
                )
            ) {
                itemsIndexed(episodes, key = { _, ep -> ep.audioUrl }) { index, episode ->
                    EpisodeRow(
                        episode = episode,
                        isDownloaded = vm.isDownloaded(episode.audioUrl),
                        isDownloading = downloading.contains(episode.audioUrl),
                        onClick = {
                            MusicService.playSingularPodcast(
                                episode.copy(audioUrl = vm.resolvePlaybackUrl(episode.audioUrl)),
                                currentFeed
                            )
                        },
                        onAddToQueue = {
                            MusicService.addToQueuePodcast(
                                episode.copy(audioUrl = vm.resolvePlaybackUrl(episode.audioUrl)),
                                currentFeed
                            )
                        },
                        onDownload = { vm.downloadEpisode(episode.audioUrl, episode.title) },
                        onDeleteDownload = { vm.deleteEpisode(episode.audioUrl) }
                    )
                    if (index < episodes.lastIndex) ListDivider(hasArt = false)
                }
            }
        }
    }
}

@Composable
fun EpisodeRow(
    episode: PodcastEpisode,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = episode.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                when {
                    isDownloading -> CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    isDownloaded -> Icon(
                        painter = painterResource(R.drawable.download_done),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                text = episode.publishDate,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = "Episode options",
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
                    onClick = { menuExpanded = false; onAddToQueue() }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.see_details),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    },
                    onClick = { menuExpanded = false }
                )
                if (isDownloaded) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.delete_download),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = { menuExpanded = false; onDeleteDownload() }
                    )
                } else {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.download),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        },
                        enabled = !isDownloading,
                        onClick = { menuExpanded = false; onDownload() }
                    )
                }
            }
        }
    }
}