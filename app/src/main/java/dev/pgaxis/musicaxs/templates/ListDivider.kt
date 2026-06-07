package dev.pgaxis.musicaxs.templates

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ListDivider(
    hasArt: Boolean = true
) {
    HorizontalDivider(
        modifier = if (hasArt) Modifier.padding(start = 64.dp, end = 12.dp) else Modifier.padding(horizontal = 12.dp),
        color = MaterialTheme.colorScheme.outline
    )
}