package com.pg_axis.musicaxs.models

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AlphabetScroller(
    letters: List<String>,
    modifier: Modifier = Modifier,
    onLetterSelected: (String) -> Unit,
    onScrubEnd: () -> Unit
) {
    Column(
        modifier = modifier
            .padding(end = 4.dp, top = 8.dp, bottom = 8.dp)
            .pointerInput(letters) {
                awaitEachGesture {
                    // Handle both tap and drag with a single gesture detector
                    val down = awaitFirstDown()
                    onLetterSelected(letterAtOffset(down.position.y, size.height, letters))

                    var event: PointerEvent
                    do {
                        event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            onLetterSelected(
                                letterAtOffset(change.position.y, size.height, letters)
                            )
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })

                    onScrubEnd()
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { letter ->
            Text(
                text = letter,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                lineHeight = 12.sp
            )
        }
    }
}

private fun letterAtOffset(y: Float, totalHeight: Int, letters: List<String>): String {
    val index = ((y / totalHeight) * letters.size)
        .toInt()
        .coerceIn(0, letters.lastIndex)
    return letters[index]
}