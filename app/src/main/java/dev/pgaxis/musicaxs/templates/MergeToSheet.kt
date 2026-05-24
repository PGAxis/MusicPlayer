package dev.pgaxis.musicaxs.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.models.Playlist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeIntoSheet(
    source: Playlist,
    candidates: List<Playlist>,
    onMerge: (targetId: Long) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.mer_sh_into, source.name),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider()

            if (candidates.isEmpty()) {
                ListItem(headlineContent = { Text(stringResource(R.string.mer_sh_no_found)) })
            } else {
                candidates.forEach { target ->
                    val songCount by target.songCount.collectAsStateWithLifecycle()
                    ListItem(
                        headlineContent = { Text(target.name) },
                        supportingContent = { Text(pluralStringResource(R.plurals.track_count, songCount, songCount)) },
                        leadingContent = {
                            Icon(painterResource(R.drawable.default_playlist), null, Modifier.size(15.dp))
                        },
                        modifier = Modifier.clickable {
                            onMerge(target.id)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}