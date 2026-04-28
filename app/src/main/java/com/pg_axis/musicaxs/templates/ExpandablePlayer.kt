package com.pg_axis.musicaxs.templates

import android.annotation.SuppressLint
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pg_axis.musicaxs.CurrentSong
import com.pg_axis.musicaxs.PlayerBarDefaults
import com.pg_axis.musicaxs.R
import com.pg_axis.musicaxs.side_pages.QueueScreen
import com.pg_axis.musicaxs.side_pages.SongControlScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class PlayerState { Collapsed, Expanded }
enum class QueuePanelState { Hidden, Visible }

@SuppressLint("FrequentlyChangingValue")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandablePlayer(
    currentSong: CurrentSong,
    isPlaying: Boolean,
    bgColor: Color,
    onBgColorChange: (Color) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeeDetail: (uri: String) -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val barHeightPx = with(density) { PlayerBarDefaults.Height.toPx() }
    val vMarginPx = with(density) { PlayerBarDefaults.VerticalMargin.toPx() }

    // ── Vertical (player expand) state ────────────────────────────────────────
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

    // ── Horizontal (queue panel) state ────────────────────────────────────────
    @Suppress("DEPRECATION")
    val queueState = remember {
        AnchoredDraggableState(
            initialValue = QueuePanelState.Hidden,
            positionalThreshold = { it * 0.4f },
            velocityThreshold = { with(density) { 125.dp.toPx() } },
            snapAnimationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
            decayAnimationSpec = exponentialDecay()
        )
    }

    BackHandler(enabled = queueState.currentValue == QueuePanelState.Visible) {
        scope.launch { queueState.animateTo(QueuePanelState.Hidden) }
    }

    BackHandler(enabled = playerState.currentValue == PlayerState.Expanded
            && queueState.currentValue == QueuePanelState.Hidden) {
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
            })
            queueState.updateAnchors(DraggableAnchors {
                QueuePanelState.Hidden at -fullWidthPx
                QueuePanelState.Visible at 0f
            })
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

        val rawQueueOffset = if (queueState.offset.isNaN()) -fullWidthPx else queueState.offset

        // Animated shape values
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

        // Image morph values
        val imageCollapsedSizePx = with(density) { 44.dp.toPx() }
        val imageExpandedSizePx = with(density) { (maxWidth - 100.dp).toPx() }
        val imageSizePx = lerp(imageCollapsedSizePx, imageExpandedSizePx, progress)

        val collapsedXPx = with(density) { 10.dp.toPx() }
        val collapsedYPx = (barHeightPx - imageCollapsedSizePx) / 2f
        val expandedXPx = (fullWidthPx - imageExpandedSizePx) / 2f
        val expandedYPx = with(density) { 71.dp.toPx() }

        val imageX = with(density) { lerp(collapsedXPx, expandedXPx, progress).toDp() }
        val imageY = with(density) { lerp(collapsedYPx, expandedYPx, progress).toDp() }
        val imageCorner = with(density) { lerp(imageCollapsedSizePx / 2f, 50.dp.toPx(), progress).toDp() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, rawPlayerOffset.roundToInt()) }
                .height(with(density) { (fullHeightPx - rawPlayerOffset).toDp() })
                .anchoredDraggable(playerState, Orientation.Vertical)
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
                            color = Color.White
                        )
                        Text(
                            text = currentSong.artist,
                            fontSize = 14.sp,
                            maxLines = 1,
                            color = Color.White
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPrevious, modifier = Modifier.size(35.dp)) {
                            Icon(painterResource(R.drawable.prev), null,
                                Modifier.padding(8.dp), tint = Color.White)
                        }
                        IconButton(onClick = onPlayPause, modifier = Modifier.size(35.dp)) {
                            Icon(painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                                null, Modifier.padding(8.dp), tint = Color.White)
                        }
                        IconButton(onClick = onNext, modifier = Modifier.size(35.dp)) {
                            Icon(painterResource(R.drawable.next), null,
                                Modifier.padding(8.dp), tint = Color.White)
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
                            onSeeDetail = onSeeDetail,
                            onCollapse = { scope.launch { playerState.animateTo(PlayerState.Collapsed) } },
                            onOpenQueue = { scope.launch { queueState.animateTo(QueuePanelState.Visible) } },
                            onPrevious = onPrevious,
                            onPlayPause = onPlayPause,
                            onNext = onNext
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
                                ?.let { onBgColorChange(Color(it).darken()) }
                        }
                    },
                    onError = { onBgColorChange(Color.DarkGray) }
                )

                // -- Queue panel
                if (queueMounted) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(rawQueueOffset.roundToInt(), 0) }
                            .anchoredDraggable(queueState, Orientation.Horizontal)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        QueueScreen(
                            onBack = { scope.launch { queueState.animateTo(QueuePanelState.Hidden) } },
                            onSeeDetail = onSeeDetail
                        )
                    }
                }
            }
        }
    }
}

private fun Color.darken(factor: Float = 0.75f) =
    copy(red = red * factor, green = green * factor, blue = blue * factor, alpha = 1f)