package dev.pgaxis.musicaxs.templates

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.pgaxis.musicaxs.CurrentSong
import dev.pgaxis.musicaxs.PlayerBarDefaults
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.contrastColor
import dev.pgaxis.musicaxs.services.MusicService
import dev.pgaxis.musicaxs.settings.FavouritesSave
import dev.pgaxis.musicaxs.side_pages.QueueScreen
import dev.pgaxis.musicaxs.side_pages.SongControlScreen
import dev.pgaxis.musicaxs.side_pages.toTimestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class PlayerState { Collapsed, Expanded }
enum class QueuePanelState { Hidden, Visible }

@SuppressLint("FrequentlyChangingValue")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExpandablePlayer(
    currentSong: CurrentSong,
    bgColor: Color,
    txtColor: Color,
    onBgColorChange: (Color) -> Unit,
    onTxtColorChange: (Color) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeeDetail: (uri: String) -> Unit,
    vm: ExpandablePlayerViewModel = viewModel()
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val isPlaying by MusicService.isPlayingState.collectAsStateWithLifecycle()
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val positionMs by vm.positionMs.collectAsStateWithLifecycle()
    val durationMs by vm.durationMs.collectAsStateWithLifecycle()
    val favourites = remember { FavouritesSave.getInstance(context) }

    val barHeightPx = with(density) { PlayerBarDefaults.Height.toPx() }
    val vMarginPx = with(density) { PlayerBarDefaults.VerticalMargin.toPx() }
    var controlsHeightPx by remember { mutableIntStateOf(0) }

    @Suppress("DEPRECATION")
    val playerState = remember {
        AnchoredDraggableState(
            initialValue = PlayerState.Collapsed,
            positionalThreshold = { it * 0.4f },
            velocityThreshold = { with(density) { 125.dp.toPx() } },
            snapAnimationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
            decayAnimationSpec = exponentialDecay()
        )
    }

    @Suppress("DEPRECATION")
    val queuePanelState = remember {
        AnchoredDraggableState(
            initialValue = QueuePanelState.Hidden,
            positionalThreshold = { it * 0.4f },
            velocityThreshold = { with(density) { 125.dp.toPx() } },
            snapAnimationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
            decayAnimationSpec = exponentialDecay()
        )
    }

    BackHandler(enabled = queuePanelState.currentValue == QueuePanelState.Visible) {
        scope.launch { queuePanelState.animateTo(QueuePanelState.Hidden) }
    }

    BackHandler(enabled = playerState.currentValue == PlayerState.Expanded
            && queuePanelState.currentValue == QueuePanelState.Hidden) {
        scope.launch { playerState.animateTo(PlayerState.Collapsed) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullHeightPx = with(density) { maxHeight.toPx() }
        val fullWidthPx = with(density) { maxWidth.toPx() }
        val collapsedOffset = fullHeightPx - barHeightPx - vMarginPx * 2

        SideEffect {
            playerState.updateAnchors(DraggableAnchors {
                PlayerState.Collapsed at collapsedOffset
                PlayerState.Expanded at 0f
            }, playerState.currentValue)
            queuePanelState.updateAnchors(DraggableAnchors {
                QueuePanelState.Hidden at if (isLandscape) -fullWidthPx / 2f else -fullWidthPx
                QueuePanelState.Visible at 0f
            }, queuePanelState.currentValue)
        }

        val rawPlayerOffset = if (playerState.offset.isNaN()) collapsedOffset else playerState.offset
        val progress = (1f - rawPlayerOffset / collapsedOffset).coerceIn(0f, 1f)

        var queueMounted by remember { mutableStateOf(false) }
        LaunchedEffect(progress >= 1f) {
            if (progress >= 1f) {
                delay(500)
                queueMounted = true
            } else {
                queueMounted = false
            }
        }

        val rawQueueOffset = if (queuePanelState.offset.isNaN()) -fullWidthPx else queuePanelState.offset

        val hPad = with(density) { lerp(10.dp.toPx(), 0f, progress).toDp() }
        val vPad = with(density) { lerp(vMarginPx, 0f, progress).toDp() }
        val corner = with(density) { lerp(barHeightPx / 2f, 0f, progress).toDp() }

        val expandedBg = MaterialTheme.colorScheme.background
        val animatedBg = Color(
            red = lerp(bgColor.red, expandedBg.red, progress),
            green = lerp(bgColor.green, expandedBg.green, progress),
            blue = lerp(bgColor.blue, expandedBg.blue, progress),
            alpha = 1f
        )

        val imageCollapsedSizePx = with(density) { 44.dp.toPx() }
        val imageExpandedSizePx = with(density) {
            if (isLandscape) minOf(
                (maxHeight * 0.55f).toPx(),
                (maxWidth / 2 - 80.dp).toPx()
            ) else (maxWidth - 100.dp).toPx()
        }
        val imageSizePx = lerp(imageCollapsedSizePx, imageExpandedSizePx, progress)

        val collapsedXPx = with(density) { 10.dp.toPx() }
        val collapsedYPx = (barHeightPx - imageCollapsedSizePx) / 2f
        val expandedXPx = if (isLandscape)
            (fullWidthPx / 2f - imageExpandedSizePx) / 2f
        else
            (fullWidthPx - imageExpandedSizePx) / 2f
        val expandedYPx = with(density) {
            if (isLandscape) (maxHeight.toPx() - imageExpandedSizePx) / 3f
            else 71.dp.toPx()
        }

        val imageX = with(density) { lerp(collapsedXPx, expandedXPx, progress).toDp() }
        val imageY = with(density) { lerp(collapsedYPx, expandedYPx, progress).toDp() }
        val imageCorner = with(density) { lerp(imageCollapsedSizePx / 2f, 50.dp.toPx(), progress).toDp() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, rawPlayerOffset.roundToInt()) }
                .height(with(density) { (fullHeightPx - rawPlayerOffset).toDp() })
                .anchoredDraggable(
                    playerState,
                    Orientation.Vertical,
                    enabled = queuePanelState.currentValue == QueuePanelState.Hidden
                )
                .clickable(
                    enabled = playerState.currentValue == PlayerState.Collapsed,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { scope.launch { playerState.animateTo(PlayerState.Expanded) } }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = hPad, vertical = vPad)
                    .clip(RoundedCornerShape(corner))
                    .background(animatedBg)
            ) {
                // --Collapsed bar
                val barAlpha = (1f - progress * 2f).coerceIn(0f, 1f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PlayerBarDefaults.Height)
                        .alpha(barAlpha)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Spacer(modifier = Modifier.size(44.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSong.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(),
                            color = txtColor
                        )
                        Text(
                            text = currentSong.artist,
                            fontSize = 14.sp,
                            maxLines = 1,
                            color = txtColor
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPrevious, modifier = Modifier.size(35.dp)) {
                            Icon(
                                painter = painterResource(R.drawable.prev),
                                contentDescription = "Previous",
                                modifier = Modifier.padding(8.dp),
                                tint = txtColor
                            )
                        }
                        IconButton(onClick = onPlayPause, modifier = Modifier.size(35.dp)) {
                            Icon(
                                painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                                contentDescription = "Play/Pause",
                                modifier = Modifier.padding(8.dp),
                                tint = txtColor
                            )
                        }
                        IconButton(onClick = onNext, modifier = Modifier.size(35.dp)) {
                            Icon(
                                painter = painterResource(R.drawable.next),
                                contentDescription = "Next",
                                modifier = Modifier.padding(8.dp),
                                tint = txtColor
                            )
                        }
                    }
                }

                //-- Expanded screen
                val expandedAlpha = ((progress - 0.3f) / 0.7f).coerceIn(0f, 1f)
                if (progress > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(expandedAlpha)
                    ) {
                        SongControlScreen(
                            currentSong = currentSong,
                            artSizeDp = with(density) { imageExpandedSizePx.toDp() },
                            onSeeDetail = onSeeDetail,
                            onCollapse = { scope.launch { playerState.animateTo(PlayerState.Collapsed) } }
                        )
                    }
                }

                // -- Morphing image
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(currentSong.songUri)
                        .size(imageExpandedSizePx.roundToInt())
                        .crossfade(true)
                        .build(),
                    contentDescription = "Album art",
                    error = painterResource(R.drawable.default_cover),
                    placeholder = painterResource(R.drawable.default_cover),
                    fallback = painterResource(R.drawable.default_cover),
                    modifier = Modifier
                        .size(with(density) { imageSizePx.toDp() })
                        .offset(imageX, imageY)
                        .clip(RoundedCornerShape(imageCorner)),
                    contentScale = ContentScale.Crop,
                    onSuccess = { result ->
                        val bitmap = (result.result.drawable as BitmapDrawable)
                            .bitmap.copy(Bitmap.Config.ARGB_8888, false)
                        Palette.from(bitmap).generate { palette ->
                            palette?.getDominantColor(Color.DarkGray.toArgb())
                                ?.let { dominant ->
                                    onBgColorChange(Color(dominant))
                                    onTxtColorChange(Color(dominant).contrastColor())
                                }
                        }
                    },
                    onError = {
                        onBgColorChange(Color.DarkGray)
                        onTxtColorChange(Color.DarkGray.contrastColor())
                    }
                )

                // -- Queue panel
                if (queueMounted) {
                    val queueWidthDp = if (isLandscape) with(density) { (fullWidthPx / 2f).toDp() }
                    else with(density) { fullWidthPx.toDp() }
                    val gradientHeightDp = 24.dp
                    val gradientHeightPx = with(density) { gradientHeightDp.toPx() }
                    val queueBottomPaddingPx = if (isLandscape) 0f
                    else controlsHeightPx + gradientHeightPx

                    Box(
                        modifier = Modifier
                            .width(queueWidthDp)
                            .then(
                                if (isLandscape) Modifier.fillMaxHeight()
                                else Modifier.padding(bottom = with(density) { queueBottomPaddingPx.toDp() })
                            )
                            .offset { IntOffset(rawQueueOffset.roundToInt(), 0) }
                            .anchoredDraggable(queuePanelState, Orientation.Horizontal)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        QueueScreen(
                            onBack = { scope.launch { queuePanelState.animateTo(QueuePanelState.Hidden) } },
                            onSeeDetail = onSeeDetail
                        )

                        // -- Gradient strip (portrait only)
                        if (!isLandscape) {
                            val bgColor = MaterialTheme.colorScheme.background
                            val transparent = Color(
                                red = bgColor.red,
                                green = bgColor.green,
                                blue = bgColor.blue,
                                alpha = 0f
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(gradientHeightDp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(transparent, bgColor)
                                        )
                                    )
                            )
                        }
                    }
                }

                if (progress > 0.01f) {
                    // -- Controls overlay (pinned to bottom, always on top)
                    Column(
                        modifier = Modifier
                            .then(
                                if (isLandscape) Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .width(with(density) { (fullWidthPx / 2f).toDp() })
                                else Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                            )
                            .onSizeChanged { controlsHeightPx = it.height }
                            .background(animatedBg.copy(alpha = expandedAlpha))
                            .alpha(expandedAlpha)
                            .padding(15.dp)
                    ) {
                        // -- Secondary controls
                        Row(modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { scope.launch { queuePanelState.animateTo(QueuePanelState.Visible) } },
                                enabled = queuePanelState.currentValue == QueuePanelState.Hidden,
                                shape = RoundedCornerShape(0.dp)
                            ) {
                                Icon(painterResource(R.drawable.queue), "Queue",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(25.dp))
                            }

                            Spacer(Modifier.weight(1f))

                            val isFav = favourites.isFavourite(currentSong.songUri!!.toUri())
                            IconButton(onClick = { vm.onLike() }, shape = RoundedCornerShape(0.dp)) {
                                Icon(
                                    painterResource(if (isFav) R.drawable.heart_filled else R.drawable.heart_outline),
                                    "Like", tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(25.dp)
                                )
                            }

                            Spacer(Modifier.weight(1f))

                            val isShuffled by remember { derivedStateOf { MusicService.isShuffled } }
                            IconButton(onClick = { MusicService.toggleShuffle(context) },
                                shape = RoundedCornerShape(0.dp)) {
                                Icon(
                                    painter = painterResource(R.drawable.shuffle),
                                    contentDescription = "Shuffle",
                                    tint = if (isShuffled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(25.dp)
                                )
                            }

                            Spacer(Modifier.weight(1f))

                            IconButton(onClick = { vm.changePlayType() },
                                shape = RoundedCornerShape(0.dp)) {
                                Icon(
                                    painterResource(when (uiState) {
                                        PlayType.Repeat -> R.drawable.repeat
                                        PlayType.RepeatOnce -> R.drawable.repeat_once
                                        PlayType.Continue -> R.drawable.continue_play
                                    }),
                                    "RepeatType", tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(25.dp)
                                )
                            }
                        }

                        Spacer(modifier = if (isLandscape) Modifier.weight(1f) else Modifier.height(15.dp))

                        // -- Progress bar
                        Row(modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically) {
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
                                    Box(Modifier.size(14.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary))
                                },
                                track = { sliderState ->
                                    SliderDefaults.Track(
                                        sliderState = sliderState,
                                        modifier = Modifier.height(3.dp),
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
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

                        Spacer(modifier = if (isLandscape) Modifier.weight(1f) else Modifier.height(15.dp))

                        // -- Playback controls
                        Row(modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onPrevious(); vm.setTime() }) {
                                Icon(painterResource(R.drawable.prev), "Previous",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { vm.seekBack10() }) {
                                Icon(painterResource(R.drawable.rewind), "-10s",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = onPlayPause, modifier = Modifier.size(56.dp)) {
                                Icon(
                                    painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                                    if (isPlaying) "Pause" else "Play",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { vm.seekForward10() }) {
                                Icon(painterResource(R.drawable.forward), "+10s",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { onNext(); vm.setTime() }) {
                                Icon(painterResource(R.drawable.next), "Next",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}