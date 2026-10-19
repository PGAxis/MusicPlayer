package dev.pgaxis.musicaxs.side_pages

import android.app.Activity
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import dev.pgaxis.musicaxs.R

private val MIME_TO_EXTENSION = mapOf(
    "audio/mpeg" to "MP3",       // mp3
    "audio/wav" to "WAV",        // wav
    "audio/x-wav" to "WAV",      // wav (alternate)
    "audio/flac" to "FLAC",      // flac
    "audio/x-flac" to "FLAC",    // flac (alternate)
    "audio/ogg" to "OGG",        // ogg vorbis
    "audio/mp4" to "M4A",        // m4a / aac
    "audio/m4a" to "M4A",        // m4a (alternate)
)

@Composable
fun SongDetailScreen(
    songUri: Uri,
    onScan: () -> Unit,
    onBack: () -> Unit
) {
    val vm: SongDetailViewModel = viewModel()

    LaunchedEffect(songUri) {
        vm.load(songUri)
    }

    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.save()
        }
    }

    if (vm.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val albumArt = @Composable {
        AsyncImage(
            model = songUri,
            contentDescription = null,
            error = painterResource(R.drawable.default_cover),
            placeholder = painterResource(R.drawable.default_cover),
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
    }

    val info = @Composable {
        InfoEditableRow(
            content = vm.title,
            onUpdateTitle = vm::updateTitle,
            enabled = vm.isEditable,
            label = stringResource(R.string.song_det_title)
        )

        InfoEditableRow(
            content = vm.artist,
            onUpdateTitle = vm::updateArtist,
            enabled = vm.isEditable,
            label = stringResource(R.string.song_det_artist)
        )

        InfoEditableRow(
            content = vm.album,
            onUpdateTitle = vm::updateAlbum,
            enabled = vm.isEditable,
            label = stringResource(R.string.song_det_album)
        )

        InfoEditableRow(
            content = vm.track,
            onUpdateTitle = vm::updateTrack,
            enabled = vm.isEditable,
            number = true,
            label = stringResource(R.string.song_det_track)
        )

        InfoRow(
            content = vm.duration,
            label = stringResource(R.string.song_det_duration)
        )

        InfoRow(
            content = "${MIME_TO_EXTENSION.getOrDefault(vm.mimeType, "Unknown")} (${vm.mimeType})",
            label = stringResource(R.string.song_det_file)
        )

        InfoRow(
            content = vm.fileSize,
            label = stringResource(R.string.song_det_size)
        )

        vm.filePath?.let {
            InfoRow(
                content = it,
                label = stringResource(R.string.song_det_path)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .windowInsetsPadding(WindowInsets.systemBars),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(25.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier
                        .size(45.dp)
                        .padding(horizontal = 5.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.back),
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.width(5.dp))

                Text(
                    text = stringResource(R.string.song_det_detail),
                    fontSize = 25.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (isLandscape) {
                Row(Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        albumArt()
                    }
                    Spacer(modifier = Modifier.width(15.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .imePadding()
                            .padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        info()
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    albumArt()
                    Spacer(modifier = Modifier.height(15.dp))
                    info()
                }
            }

            Spacer(Modifier.height(5.dp))

            Button(
                onClick = {
                    val request = vm.createWriteRequest()

                    if (request != null) {
                        writeLauncher.launch(
                            IntentSenderRequest.Builder(request.intentSender).build()
                        )
                    } else {
                        vm.save(onScan)
                    }
                },
                enabled = vm.isModified && !vm.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (vm.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text(stringResource(if (vm.isModified) R.string.song_det_save else R.string.song_det_saved))
                }
            }
        }
    }
}

@Composable
fun InfoEditableRow(
    label: String,
    content: String,
    enabled: Boolean,
    number: Boolean = false,
    onUpdateTitle: (String) -> Unit
) {
    OutlinedTextField(
        value = content,
        onValueChange = onUpdateTitle,
        enabled = enabled,
        label = { Text(label) },
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.tertiary
        ),
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun InfoRow(
    label: String,
    content: String
) {
    OutlinedTextField(
        value = content,
        onValueChange = { },
        label = { Text(label) },
        readOnly = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.White,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}