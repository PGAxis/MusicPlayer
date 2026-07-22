package dev.pgaxis.musicaxs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.time.Duration.Companion.milliseconds

data class BannerEvent(
    val id: Long = System.nanoTime(),
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val durationMs: Long = 3000L
)

object BannerCenter {
    private val _events = MutableSharedFlow<BannerEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun show(
        message: String,
        actionLabel: String? = null,
        durationMs: Long = 3000L,
        onAction: (() -> Unit)? = null
    ) {
        _events.tryEmit(
            BannerEvent(message = message, actionLabel = actionLabel, onAction = onAction, durationMs = durationMs)
        )
    }
}

@Composable
fun TopBannerHost(modifier: Modifier = Modifier) {
    val queue = remember { mutableStateListOf<BannerEvent>() }
    var current by remember { mutableStateOf<BannerEvent?>(null) }
    val visibleState = remember { MutableTransitionState(false) }

    LaunchedEffect(Unit) {
        BannerCenter.events.collect { event -> queue.add(event) }
    }

    LaunchedEffect(queue.size, current, visibleState.isIdle, visibleState.currentState) {
        if (current == null && queue.isNotEmpty() && visibleState.isIdle && !visibleState.currentState) {
            current = queue.removeAt(0)
            visibleState.targetState = true
        }
    }

    LaunchedEffect(current?.id) {
        val event = current ?: return@LaunchedEffect
        delay(event.durationMs.milliseconds)
        visibleState.targetState = false
    }

    LaunchedEffect(visibleState.isIdle, visibleState.currentState) {
        if (current != null && visibleState.isIdle && !visibleState.currentState) {
            current = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = slideInVertically(animationSpec = tween(250)) { -it } + fadeIn(tween(250)),
            exit = slideOutVertically(animationSpec = tween(200)) { -it } + fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp)
        ) {
            current?.let { event ->
                BannerCard(event = event, onDismiss = { visibleState.targetState = false })
            }
        }
    }
}


@Composable
private fun BannerCard(event: BannerEvent, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDismiss)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(event.message, modifier = Modifier.weight(1f))
            if (event.actionLabel != null && event.onAction != null) {
                TextButton(onClick = {
                    event.onAction.invoke()
                    onDismiss()
                }) {
                    Text(event.actionLabel, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
