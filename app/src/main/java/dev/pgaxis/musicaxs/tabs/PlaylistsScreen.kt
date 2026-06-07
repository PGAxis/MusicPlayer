package dev.pgaxis.musicaxs.tabs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.pgaxis.musicaxs.LocalPlayerBarTotalHeight
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.models.Playlist
import dev.pgaxis.musicaxs.services.M3uHandler
import dev.pgaxis.musicaxs.settings.FavouritedPlaylistsSave
import dev.pgaxis.musicaxs.templates.ListDivider
import dev.pgaxis.musicaxs.templates.MergeIntoSheet

data class SmartInfo(
    val name: String,
    val size: Int,
    val uri: Uri?,
    val id: Long
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
        SmartInfo(stringResource(R.string.pls_scr_rec_added), recentlyAdded.size, recentlyAdded.firstOrNull()?.uri, 1),
        SmartInfo(stringResource(R.string.pls_scr_rec_played), recentlyPlayed.size, recentlyPlayed.firstOrNull()?.uri, 2),
        SmartInfo(stringResource(R.string.pls_scr_most_played), mostPlayed.size, mostPlayed.firstOrNull()?.uri, 3)
    )

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val playlist = exportTargetPlaylist ?: return@rememberLauncherForActivityResult
        val songs = vm.getSongsForExport(playlist)
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
            contentPadding = PaddingValues(bottom = LocalPlayerBarTotalHeight.current)
        ) {
            // -- Smart playlist row
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
                        text = stringResource(R.string.pls_scr_my_playlists),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showSheet = true }, modifier = Modifier.size(24.dp)) {
                        Icon(painterResource(R.drawable.import_export), "Import / Export",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (playlists.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.pls_scr_no_found))
                    }
                }
            } else {
                itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
                    Column {
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

                        if (index < playlists.lastIndex) {
                            ListDivider()
                        }
                    }
                }
            }
        }

        if (showSheet) {
            ModalBottomSheet(onDismissRequest = { showSheet = false }) {
                Column(Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)) {
                    Text(stringResource(R.string.pls_scr_import), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                    HorizontalDivider()

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.pls_scr_import_desc)) },
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

                    // Export
                    if (playlists.isEmpty()) {
                        ListItem(headlineContent = { Text(stringResource(R.string.pls_scr_export_desc)) })
                    } else {
                        Text(stringResource(R.string.pls_scr_export), fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                        playlists.forEach { playlist ->
                            val songCount by playlist.songCount.collectAsStateWithLifecycle()

                            ListItem(
                                headlineContent = { Text(playlist.name) },
                                supportingContent = { Text(pluralStringResource(R.plurals.track_count, songCount, songCount)) },
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
                title = { Text(stringResource(R.string.pls_scr_name_playlist)) },
                text = {
                    OutlinedTextField(
                        value = importedName,
                        onValueChange = { importedName = it },
                        label = { Text(stringResource(R.string.pls_scr_playlist_name)) },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (importedName.isNotBlank()) {
                            vm.createFromImport(importedName, pendingImportSongIds)
                            showNameDialog = false
                        }
                    }) { Text(stringResource(R.string.create)) }
                },
                dismissButton = {
                    TextButton(onClick = { showNameDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        renameTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text(stringResource(R.string.pls_scr_rename_playlist)) },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text(stringResource(R.string.pls_scr_new_name)) },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (renameText.isNotBlank()) {
                            vm.rename(target.id, renameText)
                            renameTarget = null
                        }
                    }) { Text(stringResource(R.string.rename)) }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text(stringResource(R.string.delete_w_arg, target.name)) },
                text = { Text(stringResource(R.string.no_coming_back)) },
                confirmButton = {
                    TextButton(onClick = {
                        vm.delete(target.id)
                        deleteTarget = null
                    }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
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
        Text(
            text = name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = pluralStringResource(R.plurals.track_count, songCount, songCount),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    val songCount by playlist.songCount.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 3.dp),
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
                .size(44.dp)
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
            text = pluralStringResource(R.plurals.track_count, songCount, songCount),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = "Playlist options",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(if (isFavourited) R.string.rm_from_fav else R.string.add_to_fav),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
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
                    text = { Text(stringResource(R.string.rename), color = MaterialTheme.colorScheme.onSecondaryContainer) },
                    onClick = { menuExpanded = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pls_scr_merge), color = MaterialTheme.colorScheme.onSecondaryContainer) },
                    onClick = { menuExpanded = false; onMerge() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                    onClick = { menuExpanded = false; onDelete() }
                )
            }
        }
    }
}