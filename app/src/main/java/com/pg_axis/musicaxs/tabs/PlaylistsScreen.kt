package com.pg_axis.musicaxs.tabs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pg_axis.musicaxs.PlayerBarDefaults
import com.pg_axis.musicaxs.R
import com.pg_axis.musicaxs.models.Playlist
import com.pg_axis.musicaxs.services.M3uHandler
import com.pg_axis.musicaxs.settings.FavouritedPlaylistsSave
import com.pg_axis.musicaxs.templates.MergeIntoSheet

data class SmartInfo(
    val first: String,
    val second: Int,
    val third: Uri?,
    val fourth: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onOpenPlaylist: (id: String) -> Unit,
    vm: PlaylistsViewModel = viewModel()
) {
    val context = LocalContext.current
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val recentlyAdded by vm.recentlyAdded.collectAsStateWithLifecycle()
    val recentlyPlayed by vm.recentlyPlayed.collectAsStateWithLifecycle()
    val mostPlayed by vm.mostPlayed.collectAsStateWithLifecycle()
    val favPlaylists = remember { FavouritedPlaylistsSave.getInstance(context) }

    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }
    var mergeSource by remember { mutableStateOf<Playlist?>(null) }

    var showSheet by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var pendingImportSongIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var importedName by remember { mutableStateOf("") }

    var exportTargetPlaylist by remember { mutableStateOf<Playlist?>(null) }

    val smartPlaylists = listOf(
        SmartInfo("Recently Added", recentlyAdded.size, recentlyAdded.firstOrNull()?.uri, 1),
        SmartInfo("Recently Played", recentlyPlayed.size, recentlyPlayed.firstOrNull()?.uri, 2),
        SmartInfo("Most Played", mostPlayed.size, mostPlayed.firstOrNull()?.uri, 3)
    )

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val playlist = exportTargetPlaylist ?: return@rememberLauncherForActivityResult
        val songs = vm.getSongsForExport(context, playlist)
        context.contentResolver.openOutputStream(uri)?.use {
            M3uHandler.export(context, songs, it)
        }
        exportTargetPlaylist = null
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val songIds = context.contentResolver.openInputStream(uri)?.use {
            M3uHandler.import(context, it)
        } ?: emptyList()
        if (songIds.isNotEmpty()) {
            pendingImportSongIds = songIds
            importedName = ""
            showNameDialog = true
        }
    }

    TabSurface {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = PlayerBarDefaults.TotalHeight)
        ) {
            // ── Smart playlist row ────────────────────────────────────────────
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(smartPlaylists) { (name, size, iconUri, id) ->
                        SmartPlaylistCard(
                            name = name,
                            iconUri = iconUri,
                            songCount = size,
                            onClick = { onOpenPlaylist(id.toString()) }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Playlists",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showSheet = true }, modifier = Modifier.size(24.dp)) {
                        Icon(painterResource(R.drawable.import_export), "Import / Export",
                            tint = Color.White
                        )
                    }
                }
                HorizontalDivider()
            }

            if (playlists.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No playlists yet.")
                    }
                }
            } else {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        isFavourited = favPlaylists.isFavourited(playlist.id),
                        onClick = { onOpenPlaylist(playlist.id.toString()) },
                        onRename = {
                            renameTarget = playlist
                            renameText = playlist.name
                        },
                        onDelete = { deleteTarget = playlist },
                        onMerge  = { mergeSource = playlist },
                        onToggleFavourite = { favPlaylists.toggle(playlist.id) }
                    )
                }
            }
        }

        if (showSheet) {
            ModalBottomSheet(onDismissRequest = { showSheet = false }) {
                Column(Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)) {
                    Text("Playlists", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                    HorizontalDivider()

                    ListItem(
                        headlineContent = { Text("Import .m3u") },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.import_icon),
                                contentDescription = "Import",
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier.clickable {
                            showSheet = false
                            importLauncher.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "*/*"))
                        }
                    )

                    // Export — shows sub-list of playlists to pick from
                    if (playlists.isEmpty()) {
                        ListItem(headlineContent = { Text("Export .m3u — no playlists yet") })
                    } else {
                        Text("Export", fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                        playlists.forEach { playlist ->
                            ListItem(
                                headlineContent = { Text(playlist.name) },
                                supportingContent = { Text("${playlist.getSongCount()} songs") },
                                modifier = Modifier.clickable {
                                    showSheet = false
                                    exportTargetPlaylist = playlist
                                    exportLauncher.launch("${playlist.name}.m3u8")
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showNameDialog) {
            AlertDialog(
                onDismissRequest = { showNameDialog = false },
                title = { Text("Name your playlist") },
                text = {
                    OutlinedTextField(
                        value = importedName,
                        onValueChange = { importedName = it },
                        label = { Text("Playlist name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (importedName.isNotBlank()) {
                            vm.createFromImport(importedName, pendingImportSongIds)
                            showNameDialog = false
                        }
                    }) { Text("Create") }
                },
                dismissButton = {
                    TextButton(onClick = { showNameDialog = false }) { Text("Cancel") }
                }
            )
        }

        renameTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text("Rename playlist") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("New name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (renameText.isNotBlank()) {
                            vm.rename(target.id, renameText)
                            renameTarget = null
                        }
                    }) { Text("Rename") }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
                }
            )
        }

        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Delete \"${target.name}\"?") },
                text = { Text("This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.delete(target.id)
                        deleteTarget = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
                }
            )
        }

        mergeSource?.let { source ->
            MergeIntoSheet(
                source = source,
                candidates = playlists.filter { it.id != source.id },
                onMerge = { targetId -> vm.merge(source.id, targetId) },
                onDismiss = { mergeSource = null }
            )
        }
    }
}

@Composable
private fun SmartPlaylistCard(
    name: String,
    iconUri: Uri?,
    songCount: Int,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(iconUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Album art",
                    error = painterResource(R.drawable.default_cover),
                    placeholder = painterResource(R.drawable.default_cover),
                    fallback = painterResource(R.drawable.default_cover),
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        Text("$songCount songs", fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    isFavourited: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMerge: () -> Unit,
    onToggleFavourite: () -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(playlist.getImageUri())
                .size(48)
                .crossfade(true)
                .build(),
            contentDescription = playlist.name,
            error = painterResource(R.drawable.default_cover),
            placeholder = painterResource(R.drawable.default_cover),
            fallback = painterResource(R.drawable.default_cover),
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        Text(
            text = playlist.name,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "${playlist.getSongCount()} songs",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = "Playlist options",
                    tint = Color.White
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isFavourited) "Remove from Favourites" else "Add to Favourites",
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                painter = painterResource(if (isFavourited) R.drawable.heart_filled else R.drawable.heart_outline),
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    },
                    onClick = { menuExpanded = false; onToggleFavourite() }
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { menuExpanded = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Merge into…") },
                    onClick = { menuExpanded = false; onMerge() }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuExpanded = false; onDelete() }
                )
            }
        }
    }
}