package com.pg_axis.musicaxs

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pg_axis.musicaxs.tabs.FavouritesScreen
import com.pg_axis.musicaxs.tabs.PlaylistsScreen
import com.pg_axis.musicaxs.tabs.SongsScreen
import com.pg_axis.musicaxs.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs

@SuppressLint("FrequentlyChangingValue")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val currentSong by vm.currentSong.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    val initialPage by vm.currentPageIndex.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(initialPage = initialPage) { vm.tabs.size }
    val tabScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    LaunchedEffect(pagerState.settledPage) {
        vm.onPageChanged(pagerState.settledPage)
    }

    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        val screenWidthDp = maxWidth
        val tabWidthDp = screenWidthDp * 0.275f
        val padDp = (screenWidthDp - tabWidthDp) / 2f
        val tabWidthPx = with(density) { tabWidthDp.toPx() }

        var isDraggingTab by remember { mutableStateOf(false) }
        LaunchedEffect(pagerState.currentPage, pagerState.currentPageOffsetFraction) {
            if (!isDraggingTab) {
                val continuousPage = pagerState.currentPage + pagerState.currentPageOffsetFraction
                tabScrollState.scrollTo((continuousPage * tabWidthPx).toInt())
            }
        }

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp, vertical = 20.dp)
                    .height(45.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontWeight = FontWeight.Bold,
                    fontSize = 25.sp,
                    modifier = Modifier.weight(1f),
                    color = CyanPrimary
                )

                val onPlaylists = pagerState.settledPage == 1
                IconButton(
                    onClick = { vm.onAddPlaylist() },
                    enabled = onPlaylists,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.plus),
                        contentDescription = "Add playlist",
                        modifier = Modifier.alpha(if (onPlaylists) 1f else 0f),
                        tint = CyanPrimary
                    )
                }

                Spacer(Modifier.width(4.dp))

                IconButton(onClick = vm::onSearch, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.magglass),
                        contentDescription = "Search",
                        tint = CyanPrimary
                    )
                }

                Spacer(Modifier.width(4.dp))

                IconButton(onClick = vm::onSettings, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.settings),
                        contentDescription = "Settings",
                        tint = CyanPrimary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .horizontalScroll(tabScrollState, enabled = false)
            ) {
                Spacer(Modifier.width(padDp))

                vm.tabs.forEachIndexed { index, label ->
                    val continuousPage = pagerState.currentPage + pagerState.currentPageOffsetFraction
                    val distance = abs(index - continuousPage).coerceIn(0f, 1f)
                    val fontSize = lerp(20f, 15f, distance)
                    val isCurrent = pagerState.settledPage == index

                    Box(
                        modifier = Modifier
                            .width(tabWidthDp)
                            .fillMaxHeight()
                            .clickable { scope.launch { pagerState.animateScrollToPage(index) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = fontSize.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                Spacer(Modifier.width(padDp))
            }

            // -- Content pager ----------------------------------------------------
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when (page) {
                        0 -> FavouritesScreen()
                        1 -> PlaylistsScreen()
                        2 -> SongsScreen()
                        3 -> Text(text = vm.tabs[page], color = Color.White)
                        4 -> Text(text = vm.tabs[page], color = Color.White)
                    }
                    // TODO: replace with actual page composables
                    //   3 -> AlbumsPage()
                    //   4 -> InterpretsPage()

                }
            }
        }

        // -- Now Playing bar ------------------------------------------------------
        Card(
            modifier  = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = PlayerBarDefaults.VerticalMargin)
                .height(PlayerBarDefaults.Height),
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 6.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Album art
                AsyncImage(
                    model = currentSong.albumArtPath ?: R.drawable.default_cover,
                    contentDescription = "Album art",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )

                // Song info - title marquees when too long, artist truncates
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = currentSong.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                        color = Color.White
                    )
                    Text(
                        text = currentSong.artist,
                        fontSize = 14.sp,
                        maxLines = 1,
                        color = Color.White
                    )
                }

                // Playback controls - right-aligned naturally by weight(1f) on metadata
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = vm::onPrevious, modifier = Modifier.size(35.dp)) {
                        Icon(
                            painterResource(R.drawable.prev),
                            contentDescription = "Previous",
                            modifier = Modifier.padding(8.dp),
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = vm::onPlayPause, modifier = Modifier.size(35.dp)) {
                        Icon(
                            painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.padding(8.dp),
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = vm::onNext, modifier = Modifier.size(35.dp)) {
                        Icon(
                            painterResource(R.drawable.next),
                            contentDescription = "Next",
                            modifier = Modifier.padding(8.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}