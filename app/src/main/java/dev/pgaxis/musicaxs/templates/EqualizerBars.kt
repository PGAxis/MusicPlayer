package dev.pgaxis.musicaxs.templates

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pgaxis.musicaxs.services.MusicService

@Composable
fun EqualizerBars(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    barWidth: Dp = 3.dp,
    maxHeight: Dp = 16.dp
) {
    val visualizerActive by MusicService.visualizerActiveState.collectAsStateWithLifecycle()
    val bands by MusicService.audioBandsState.collectAsStateWithLifecycle()

    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    val fakeBar1 by infiniteTransition.animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bar1"
    )
    val fakeBar2 by infiniteTransition.animateFloat(
        1f, 0.3f, infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse), label = "bar2"
    )
    val fakeBar3 by infiniteTransition.animateFloat(
        0.5f, 1f, infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bar3"
    )

    val b by animateFloatAsState(bands.first, tween(120), label = "bass")
    val m by animateFloatAsState(bands.second, tween(120), label = "mid")
    val t by animateFloatAsState(bands.third, tween(120), label = "treble")

    val fractions = if (visualizerActive) listOf(b, m, t) else listOf(fakeBar1, fakeBar2, fakeBar3)

    Row(
        modifier = modifier.height(maxHeight),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        fractions.forEach { fraction ->
            val height = if (isPlaying) maxHeight * fraction.coerceIn(0.15f, 1f) else maxHeight * 0.3f
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(height)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}