package dev.pgaxis.musicaxs

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pgaxis.musicaxs.models.TabType
import dev.pgaxis.musicaxs.models.labelRes
import dev.pgaxis.musicaxs.orbit.PendingOrbitJoin
import dev.pgaxis.musicaxs.tabs.AlbumsScreen
import dev.pgaxis.musicaxs.tabs.FavouritesScreen
import dev.pgaxis.musicaxs.tabs.ArtistsScreen
import dev.pgaxis.musicaxs.tabs.OrbitScreen
import dev.pgaxis.musicaxs.tabs.PlaylistsScreen
import dev.pgaxis.musicaxs.tabs.PodcastsScreen
import dev.pgaxis.musicaxs.tabs.SongsScreen
import dev.pgaxis.musicaxs.templates.ExpandablePlayer
import dev.pgaxis.musicaxs.ui.theme.TextDarkPrimary
import dev.pgaxis.musicaxs.ui.theme.TextWhitePrimary
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("FrequentlyChangingValue")
@Composable
fun MainScreen(
    goToDetail: (uri: String) -> Unit,
    goToPlaylist: (id: String) -> Unit,
    goToSearch: () -> Unit,
    goToSettings: () -> Unit,
    onChooseSongsForPlaylist: (playlistId: String) -> Unit,
    onOpenAlbum: (albumId: String) -> Unit,
    onOpenArtist: (name: String) -> Unit,
    onOpenPodcast: (feedUrl: String) -> Unit,
    vm: MainViewModel = viewModel()
) {
    val currentSong by vm.currentSong.collectAsState()
    val initialPage by vm.currentPageIndex.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName  by remember { mutableStateOf("") }
    var showAddFeedDialog by remember { mutableStateOf(false) }

    val visibleTabs = vm.visibleTabs
    val tabContent = remember {
        mapOf<TabType, @Composable () -> Unit>(
            TabType.FAVOURITES to { FavouritesScreen(goToPlaylist) },
            TabType.PLAYLISTS to { PlaylistsScreen(goToPlaylist) },
            TabType.SONGS to { SongsScreen(goToDetail = goToDetail, scanSongs = vm::scanAll) },
            TabType.ALBUMS to { AlbumsScreen(onOpenAlbum = onOpenAlbum) },
            TabType.ARTISTS to { ArtistsScreen(onOpenArtist = onOpenArtist) },
            TabType.PODCASTS to { PodcastsScreen(showAddDialog = showAddFeedDialog, onAddDialogDismiss = { showAddFeedDialog = false }, onOpenPodcast = onOpenPodcast) },
            TabType.ORBIT to { OrbitScreen() }
        )
    }

    val pagerState = rememberPagerState(initialPage = initialPage) { visibleTabs.size }
    val tabScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var bgColor by remember { mutableStateOf(Color.DarkGray) }
    val animatedBgColor by animateColorAsState(
        targetValue = bgColor,
        animationSpec = tween(
            durationMillis = 500
        ),
        label = "bgColor"
    )

    var textColor by remember { mutableStateOf(Color.DarkGray.contrastColor()) }
    val animatedTextColor by animateColorAsState(
        targetValue = textColor,
        animationSpec = tween(
            durationMillis = 500
        ),
        label = "textColor"
    )

    LaunchedEffect(pagerState.settledPage) {
        vm.onPageChanged(pagerState.settledPage)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    coroutineScope.launch {
                        vm.playlistToQueue.syncQueueWithPlaylist()
                    }
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        PendingOrbitJoin.payload.collect { payload ->
            if (payload != null) {
                val orbitIndex = visibleTabs.indexOfFirst { it.tab == TabType.ORBIT.name }
                if (orbitIndex >= 0) {
                    scope.launch { pagerState.animateScrollToPage(orbitIndex) }
                }
            }
        }
    }

    BoxWithConstraints(Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        val screenWidthDp = maxWidth
        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val tabWidthDp = screenWidthDp * if (isLandscape) 0.15f else 0.275f
        val padDp = (screenWidthDp - tabWidthDp) / 2f
        val tabWidthPx = with(density) { tabWidthDp.toPx() }

        var isDraggingTab by remember { mutableStateOf(false) }
        var fingerDown by remember { mutableStateOf(false) }
        var needsSnap by remember { mutableStateOf(false) }
        var snapJob by remember { mutableStateOf<Job?>(null) }

        LaunchedEffect(pagerState.currentPage, pagerState.currentPageOffsetFraction, tabWidthPx, isDraggingTab) {
            if (!isDraggingTab) {
                val continuousPage = pagerState.currentPage + pagerState.currentPageOffsetFraction
                tabScrollState.scrollTo((continuousPage * tabWidthPx).toInt())
            }
        }

        LaunchedEffect(tabScrollState, tabWidthPx) {
            snapshotFlow { needsSnap && !fingerDown && !tabScrollState.isScrollInProgress }
                .collectLatest { idle ->
                    if (!idle) return@collectLatest

                    delay(100.milliseconds)

                    if (snapJob?.isActive == true) return@collectLatest
                    val closestIndex = (tabScrollState.value / tabWidthPx)
                        .roundToInt()
                        .coerceIn(0, visibleTabs.size - 1)

                    snapJob = scope.launch {
                        coroutineScope {
                            launch { tabScrollState.animateScrollTo((closestIndex * tabWidthPx).toInt()) }
                            launch { pagerState.animateScrollToPage(closestIndex) }
                        }
                        needsSnap = false
                        isDraggingTab = false
                    }
                }
        }

        val totalHeight by remember {
            derivedStateOf {
                if (vm.settings.lastSongUri.isEmpty()) 0.dp
                else PlayerBarDefaults.Height + PlayerBarDefaults.VerticalMargin * 2
            }
        }

        CompositionLocalProvider(LocalPlayerBarTotalHeight provides totalHeight) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // -- Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp, vertical = 10.dp)
                        .height(35.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                        fontSize = 25.sp,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.primary
                    )

                    val onPlaylists = visibleTabs.getOrNull(pagerState.settledPage)?.tab == TabType.PLAYLISTS.name
                    val onPodcasts = visibleTabs.getOrNull(pagerState.settledPage)?.tab == TabType.PODCASTS.name
                    val showPlusButton = onPlaylists || onPodcasts

                    IconButton(
                        onClick = {
                            if (onPlaylists) showCreateDialog = true
                            else if (onPodcasts) showAddFeedDialog = true
                        },
                        enabled = showPlusButton,
                        modifier = Modifier.size(35.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.plus),
                            contentDescription = "Add playlist",
                            modifier = Modifier.alpha(if (showPlusButton) 1f else 0f),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    IconButton(onClick = goToSearch, modifier = Modifier.size(35.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.magglass),
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    IconButton(onClick = goToSettings, modifier = Modifier.size(35.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.settings),
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // -- Tab titles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)

                                snapJob?.cancel()
                                isDraggingTab = true
                                fingerDown = true

                                do {
                                    val event = awaitPointerEvent()
                                    if (event.changes.any { abs(it.position.x - down.position.x) > viewConfiguration.touchSlop }) {
                                        needsSnap = true
                                    }
                                } while (event.changes.any { it.pressed })

                                fingerDown = false
                                if (!needsSnap) isDraggingTab = false
                            }
                        }
                        .horizontalScroll(tabScrollState, enabled = true)
                ) {
                    Spacer(Modifier.width(padDp))

                    visibleTabs.forEachIndexed { index, titleVis ->
                        val tabType = TabType.valueOf(titleVis.tab)
                        val continuousPage = pagerState.currentPage + pagerState.currentPageOffsetFraction
                        val distance = abs(index - continuousPage).coerceIn(0f, 1f)
                        val isCurrent = pagerState.settledPage == index
                        val label = stringResource(tabType.labelRes())
                        var fittingActiveFontSize by remember(label) { mutableFloatStateOf(20f) }
                        val actualFontSize = lerp(fittingActiveFontSize, fittingActiveFontSize * (15f / 20f), distance)

                        Box(
                            modifier = Modifier
                                .width(tabWidthDp)
                                .fillMaxHeight()
                                .graphicsLayer {}
                                .clickable { scope.launch { pagerState.animateScrollToPage(index) } },
                            contentAlignment = Alignment.Center
                        ) {
                            // Hidden measuring text at max size
                            Text(
                                text = label,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.alpha(0f),
                                autoSize = TextAutoSize.StepBased(
                                    minFontSize = 8.sp,
                                    maxFontSize = 20.sp,
                                    stepSize = 0.5.sp
                                ),
                                onTextLayout = { result ->
                                    fittingActiveFontSize = result.layoutInput.style.fontSize.value
                                }
                            )

                            // Visible text using the captured size
                            Text(
                                text = label,
                                fontSize = actualFontSize.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(Modifier.width(padDp))
                }

                // -- Content pager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    val tabType = TabType.valueOf(visibleTabs[page].tab)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        tabContent[tabType]?.invoke()
                    }
                }
            }

            // -- Now Playing bar
            currentSong?.let { it1 ->
                ExpandablePlayer(
                    currentSong = it1,
                    bgColor = animatedBgColor,
                    txtColor = animatedTextColor,
                    onBgColorChange = { bgColor = it },
                    onTxtColorChange = { textColor = it },
                    onPrevious = vm::onPrevious,
                    onPlayPause = vm::onPlayPause,
                    onNext = vm::onNext,
                    onSeeDetail = goToDetail
                )
            }

            if (showCreateDialog) {
                AlertDialog(
                    onDismissRequest = { showCreateDialog = false; newPlaylistName = "" },
                    title = { Text(stringResource(R.string.main_new_playlist)) },
                    text = {
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            label = { Text(stringResource(R.string.name)) },
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
                        }) { Text(stringResource(R.string.create)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreateDialog = false; newPlaylistName = "" }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
        }
    }
}

fun Color.contrastColor(): Color {
    return if (luminance() > 0.5f) {
        TextDarkPrimary
    } else {
        TextWhitePrimary
    }
}