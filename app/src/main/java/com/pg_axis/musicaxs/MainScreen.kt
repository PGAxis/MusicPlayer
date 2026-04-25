package com.pg_axis.musicaxs

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import com.pg_axis.musicaxs.tabs.AlbumsScreen
import com.pg_axis.musicaxs.tabs.FavouritesScreen
import com.pg_axis.musicaxs.tabs.ArtistsScreen
import com.pg_axis.musicaxs.tabs.PlaylistsScreen
import com.pg_axis.musicaxs.tabs.SongsScreen
import com.pg_axis.musicaxs.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs

@SuppressLint("FrequentlyChangingValue")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    goToDetail: (uri: String) -> Unit,
    vm: MainViewModel = viewModel()
) {
    val currentSong by vm.currentSong.collectAsState()
    val initialPage by vm.currentPageIndex.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(initialPage = initialPage) { vm.tabs.size }
    val tabScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var bgColor by remember { mutableStateOf(Color.DarkGray) }

    fun Color.darken(factor: Float = 0.75f): Color {
        return copy(
            red = red * factor,
            green = green * factor,
            blue = blue * factor,
            alpha = 1f
        )
    }

    LaunchedEffect(pagerState.settledPage) {
        vm.onPageChanged(pagerState.settledPage)
    }

    BoxWithConstraints(Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.systemBars)) {
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

        // -- Header -------------------
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp, vertical = 20.dp)
                    .height(35.dp),
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
                    modifier = Modifier.size(35.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.plus),
                        contentDescription = "Add playlist",
                        modifier = Modifier.alpha(if (onPlaylists) 1f else 0f),
                        tint = CyanPrimary
                    )
                }

                Spacer(Modifier.width(4.dp))

                IconButton(onClick = vm::onSearch, modifier = Modifier.size(35.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.magglass),
                        contentDescription = "Search",
                        tint = CyanPrimary
                    )
                }

                Spacer(Modifier.width(4.dp))

                IconButton(onClick = vm::onSettings, modifier = Modifier.size(35.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.settings),
                        contentDescription = "Settings",
                        tint = CyanPrimary
                    )
                }
            }

            // -- Tab titles -------------------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .horizontalScroll(tabScrollState, enabled = true)
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

            // -- Content pager --------------------------------------
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
                        0 -> FavouritesScreen(goToDetail = goToDetail )
                        1 -> PlaylistsScreen()
                        2 -> SongsScreen(goToDetail = goToDetail )
                        3 -> AlbumsScreen(goToDetail = goToDetail )
                        4 -> ArtistsScreen(goToDetail = goToDetail )
                    }
                }
            }
        }

        // -- Now Playing bar -------------------------------------------------------------------------------------
        if (currentSong != null) {
            Card(
                modifier  = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        horizontal = 10.dp,
                        vertical = PlayerBarDefaults.VerticalMargin
                    )
                    .height(PlayerBarDefaults.Height),
                shape = CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = bgColor
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 10.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    AsyncImage(
                        model = currentSong?.songUri,
                        error = painterResource(R.drawable.default_cover),
                        placeholder = painterResource(R.drawable.default_cover),
                        fallback = painterResource(R.drawable.default_cover),
                        contentDescription = "Album art",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        onSuccess = { state ->
                            val drawable = state.result.drawable
                            val bitmap = (drawable as BitmapDrawable).bitmap

                            val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)

                            Palette.from(softwareBitmap).generate { palette ->
                                val colorInt = palette?.getDominantColor(Color.DarkGray.toArgb())
                                if (colorInt != null) {
                                    bgColor = Color(colorInt).darken()
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(0.dp))

                    // Song info
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        currentSong?.let {
                            Text(
                                text = it.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee(),
                                color = Color.White
                            )
                            Text(
                                text = it.artist,
                                fontSize = 14.sp,
                                maxLines = 1,
                                color = Color.White
                            )
                        }
                    }

                    // Playback controls
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
                                painter = painterResource(
                                    if (vm.isPlaying) R.drawable.pause else R.drawable.play
                                ),
                                contentDescription = if (vm.isPlaying) "Pause" else "Play",
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
}