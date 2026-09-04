package dev.pgaxis.musicaxs.side_pages

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.models.AlwaysCrossfade
import dev.pgaxis.musicaxs.models.Song
import dev.pgaxis.musicaxs.services.MusicService
import dev.pgaxis.musicaxs.templates.AddToSheet
import dev.pgaxis.musicaxs.templates.ListDivider
import dev.pgaxis.musicaxs.templates.SideScreenHeader
import dev.pgaxis.musicaxs.templates.SongRow
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private val ART_FULL_SIZE = 150.dp
private val ART_TITLE_SPACING = 16.dp
private val HEADER_VERTICAL_PADDING = 8.dp

@Composable
fun AlbumDetailScreen(
    albumId: Long,
    onBack: () -> Unit,
    onSeeDetail: (uri: String) -> Unit,
    vm: AlbumDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val album by vm.album.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    val listState = rememberLazyListState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val density = LocalDensity.current
    val headerCollapseRangePx = with(density) { ART_FULL_SIZE.toPx() }
    var imageOffsetPx by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val newOffset = (imageOffsetPx + available.y).coerceIn(-headerCollapseRangePx, 0f)
                val consumed = newOffset - imageOffsetPx
                imageOffsetPx = newOffset
                return Offset(0f, consumed)
            }
        }
    }

    // 0f = fully expanded header, 1f = fully collapsed header.
    val scrollFraction by remember {
        derivedStateOf { (-imageOffsetPx / headerCollapseRangePx).coerceIn(0f, 1f) }
    }

    LaunchedEffect(albumId) { vm.init(albumId) }

    LaunchedEffect(songs.isEmpty()) {
        if (songs.isEmpty()) {
            delay(3000.milliseconds)
            if (songs.isEmpty()) onBack()
        }
    }

    val currentAlbum = album ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SideScreenHeader(
            onBack = onBack
        ) {
            Spacer(Modifier.weight(1f))

            IconButton(onClick = {
                if (songs.isNotEmpty()) MusicService.playShuffled(context, songs)
            }, shape = RoundedCornerShape(0.dp)) {
                Icon(painterResource(R.drawable.shuffle), "Play shuffled",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = {
                if (songs.isNotEmpty()) MusicService.playNormal(context, songs)
            }, shape = RoundedCornerShape(0.dp)) {
                Icon(painterResource(R.drawable.play), "Play all",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
        }

        HorizontalDivider()

        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection)
                ) {
                    AlbumHeader(
                        song = songs.first(),
                        name = currentAlbum.name,
                        interpret = currentAlbum.artist,
                        isLandscape = isLandscape,
                        scrollFraction = scrollFraction
                    )

                    Surface(
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            state = listState
                        ) {
                            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${song.track} -",
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(start = 8.dp).width(44.dp)
                                        )

                                        SongRow(
                                            song = song,
                                            onSeeDetails = onSeeDetail,
                                            onAddTo = { selectedSong = song },
                                            showsImage = false
                                        )
                                    }

                                    if (index < songs.lastIndex) {
                                        ListDivider(hasArt = false)
                                    }
                                }
                            }
                        }
                    }
                }

                selectedSong?.let { song ->
                    AddToSheet(song = song, onDismiss = { selectedSong = null })
                }
            }
        }
    }
}

@Composable
private fun AlbumHeader(
    song: Song,
    name: String,
    interpret: String,
    isLandscape: Boolean,
    scrollFraction: Float
) {
    val artSize = ART_FULL_SIZE * (1f - scrollFraction)
    val spacing = ART_TITLE_SPACING * (1f - scrollFraction)

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = HEADER_VERTICAL_PADDING),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(song = song, size = artSize)
            if (artSize > 0.dp) Spacer(Modifier.width(spacing))
            AlbumTitle(name = name, interpret = interpret)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = HEADER_VERTICAL_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AlbumArt(song = song, size = artSize)
            if (artSize > 0.dp) Spacer(Modifier.height(spacing))
            AlbumTitle(name = name, interpret = interpret)
        }
    }
}

@Composable
private fun AlbumArt(song: Song, size: Dp) {
    val context = LocalContext.current
    val validAlbumArtUri = song.albumArtUri.takeIf {
        it != Uri.EMPTY && (it?.lastPathSegment?.toLongOrNull() ?: 0L) > 0L
    }
    var useFallbackUri by remember(song.uri) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(size.coerceAtLeast(0.dp))
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(if (useFallbackUri) song.uri else validAlbumArtUri)
                .size(150)
                .transitionFactory(AlwaysCrossfade)
                .build(),
            contentDescription = "Album art",
            error = painterResource(R.drawable.default_cover),
            placeholder = painterResource(R.drawable.default_cover),
            fallback = painterResource(R.drawable.default_cover),
            modifier = Modifier
                .size(ART_FULL_SIZE)
                .clip(RoundedCornerShape(15.dp)),
            contentScale = ContentScale.Crop,
            onError = { if (!useFallbackUri) useFallbackUri = true }
        )
    }
}

@Composable
private fun AlbumTitle(name: String, interpret: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            name,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.basicMarquee()
        )
        Text(
            interpret,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}