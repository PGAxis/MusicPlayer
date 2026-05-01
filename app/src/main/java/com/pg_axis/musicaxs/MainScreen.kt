package com.pg_axis.musicaxs

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pg_axis.musicaxs.tabs.AlbumsScreen
import com.pg_axis.musicaxs.tabs.FavouritesScreen
import com.pg_axis.musicaxs.tabs.ArtistsScreen
import com.pg_axis.musicaxs.tabs.PlaylistsScreen
import com.pg_axis.musicaxs.tabs.SongsScreen
import com.pg_axis.musicaxs.templates.ExpandablePlayer
import com.pg_axis.musicaxs.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs

@SuppressLint("FrequentlyChangingValue")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    goToDetail: (uri: String) -> Unit,
    goToPlaylist: (id: String) -> Unit,
    goToSearch: () -> Unit,
    onChooseSongsForPlaylist: (playlistId: String) -> Unit,
    vm: MainViewModel = viewModel()
) {
    val currentSong by vm.currentSong.collectAsState()
    val initialPage by vm.currentPageIndex.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(initialPage = initialPage) { vm.tabs.size }
    val tabScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName  by remember { mutableStateOf("") }

    var bgColor by remember { mutableStateOf(Color.DarkGray) }

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

        Column(Modifier.fillMaxSize()) {
            // -- Header -------------------
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
                    onClick = { showCreateDialog = true },
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

                IconButton(onClick = goToSearch, modifier = Modifier.size(35.dp)) {
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
                        0 -> FavouritesScreen(goToPlaylist )
                        1 -> PlaylistsScreen(goToPlaylist)
                        2 -> SongsScreen(goToDetail = goToDetail )
                        3 -> AlbumsScreen(goToDetail = goToDetail )
                        4 -> ArtistsScreen(goToDetail = goToDetail )
                    }
                }
            }
        }

        // -- Now Playing bar -------------------------------------------------------------------------------------
        currentSong?.let { it1 ->
            ExpandablePlayer(
                currentSong = it1,
                isPlaying = vm.isPlaying,
                bgColor = bgColor,
                onBgColorChange = { bgColor = it },
                onPrevious = vm::onPrevious,
                onPlayPause = vm::onPlayPause,
                onNext = vm::onNext,
                onSeeDetail = goToDetail
            )
        }

        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false; newPlaylistName = "" },
                title = { Text("New Playlist") },
                text = {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            val playlist = vm.createAndGetPlaylist(newPlaylistName)
                            showCreateDialog = false
                            newPlaylistName = ""
                            onChooseSongsForPlaylist(playlist.id.toString())
                        }
                    }) { Text("Create") }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false; newPlaylistName = "" }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}