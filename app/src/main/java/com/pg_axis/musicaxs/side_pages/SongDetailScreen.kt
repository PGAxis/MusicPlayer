package com.pg_axis.musicaxs.side_pages

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pg_axis.musicaxs.R
import com.pg_axis.musicaxs.ui.theme.BlueTertiary
import com.pg_axis.musicaxs.ui.theme.BorderColor
import com.pg_axis.musicaxs.ui.theme.CyanPrimary
import com.pg_axis.musicaxs.ui.theme.TextSecondary

@Composable
fun SongDetailScreen(
    songUri: Uri,
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
            IconButton(onClick = onBack, shape = RoundedCornerShape(0.dp)) {
                Icon(
                    painter = painterResource(R.drawable.back),
                    contentDescription = "Back",
                    tint = CyanPrimary
                )
            }

            Spacer(Modifier.width(5.dp))

            Text(
                text = "Detail",
                fontSize = 25.sp,
                color = CyanPrimary
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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

            Spacer(modifier = Modifier.height(15.dp))

            OutlinedTextField(
                value = vm.title,
                onValueChange = vm::updateTitle,
                label = { Text("Title") },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = BlueTertiary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vm.artist,
                onValueChange = vm::updateArtist,
                label = { Text("Artist") },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = BlueTertiary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vm.album,
                onValueChange = vm::updateAlbum,
                label = { Text("Album") },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = BlueTertiary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vm.duration,
                onValueChange = { },
                label = { Text("Duration") },
                readOnly = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = BorderColor,
                    focusedLabelColor = TextSecondary,
                    cursorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vm.mimeType,
                onValueChange = { },
                label = { Text("File type") },
                readOnly = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = BorderColor,
                    focusedLabelColor = TextSecondary,
                    cursorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vm.fileSize,
                onValueChange = { },
                label = { Text("Size") },
                readOnly = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = BorderColor,
                    focusedLabelColor = TextSecondary,
                    cursorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            vm.filePath?.let {
                OutlinedTextField(
                    value = it,
                    onValueChange = { },
                    label = { Text("Path") },
                    readOnly = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor = TextSecondary,
                        cursorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
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
                    vm.save()
                }
            },
            enabled = vm.isModified && !vm.isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (vm.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            } else {
                Text(if (vm.isModified) "Save" else "Saved")
            }
        }
    }
}