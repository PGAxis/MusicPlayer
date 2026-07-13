package dev.pgaxis.musicaxs.templates

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun BounceMarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    val offset = remember { Animatable(0f) }
    var textWidth by remember { mutableIntStateOf(0) }
    var containerWidth by remember { mutableIntStateOf(0) }

    LaunchedEffect(textWidth, containerWidth, text) {
        val maxOffset = (textWidth - containerWidth).toFloat()

        if (maxOffset <= 0f) {
            val staticOffset = when (style.textAlign) {
                TextAlign.Center -> -maxOffset / 2f
                TextAlign.End, TextAlign.Right -> -maxOffset
                else -> 0f
            }
            offset.snapTo(staticOffset)
            return@LaunchedEffect
        }

        val initialOffset = when (style.textAlign) {
            TextAlign.Center -> -maxOffset / 2f
            TextAlign.End, TextAlign.Right -> -maxOffset
            else -> 0f
        }
        offset.snapTo(initialOffset)

        while (true) {
            delay(1500.milliseconds)
            offset.animateTo(
                targetValue = -maxOffset,
                animationSpec = tween(
                    durationMillis = (maxOffset * 8).toInt(),
                    easing = LinearEasing
                )
            )
            delay(1500.milliseconds)
            offset.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = (maxOffset * 8).toInt(),
                    easing = LinearEasing
                )
            )
        }
    }

    SubcomposeLayout(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { containerWidth = it.width }
    ) { constraints ->
        val textPlaceable = subcompose("text") {
            Text(
                text = text,
                style = style,
                softWrap = false,
            )
        }.first().measure(constraints.copy(maxWidth = Int.MAX_VALUE))

        textWidth = textPlaceable.width

        layout(constraints.maxWidth, textPlaceable.height) {
            textPlaceable.placeRelative(offset.value.roundToInt(), 0)
        }
    }
}