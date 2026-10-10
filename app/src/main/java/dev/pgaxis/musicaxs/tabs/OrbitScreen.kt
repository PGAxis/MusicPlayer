package dev.pgaxis.musicaxs.tabs

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.pgaxis.musicaxs.LocalPlayerBarTotalHeight
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.models.AlphabetScroller
import dev.pgaxis.musicaxs.models.OrbitSong
import dev.pgaxis.musicaxs.orbit.DiscoveredHost
import dev.pgaxis.musicaxs.orbit.OrbitCaptureActivity
import dev.pgaxis.musicaxs.orbit.OrbitConnectionState
import dev.pgaxis.musicaxs.orbit.OrbitFailureReason
import dev.pgaxis.musicaxs.orbit.OrbitPeerIdentity
import dev.pgaxis.musicaxs.orbit.OrbitQrCode
import dev.pgaxis.musicaxs.orbit.PendingOrbitJoin
import dev.pgaxis.musicaxs.templates.ListDivider
import dev.pgaxis.musicaxs.templates.OrbitSongRow
import dev.pgaxis.musicaxs.templates.SectionHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private sealed interface OrbitSongListItem {
    data class Header(val letter: String) : OrbitSongListItem
    data class Item(val song: OrbitSong) : OrbitSongListItem
}

private fun groupSongs(songs: List<OrbitSong>): Pair<List<OrbitSongListItem>, Map<String, Int>> {
    val items = mutableListOf<OrbitSongListItem>()
    val letterIndex = mutableMapOf<String, Int>()

    songs
        .groupBy { song ->
            val first = song.title.firstOrNull()?.uppercaseChar()
            when {
                first == null -> "#"
                first.isLetter() -> first.toString()
                else -> "#"
            }
        }
        .toSortedMap(compareBy { if (it == "#") "\u0000" else it })
        .forEach { (letter, group) ->
            letterIndex[letter] = items.size
            items.add(OrbitSongListItem.Header(letter))
            group.forEach { items.add(OrbitSongListItem.Item(it)) }
        }

    return items to letterIndex
}

@Composable
private fun OrbitFailureReason.message(): String = when (this) {
    OrbitFailureReason.PermissionDenied -> stringResource(R.string.orb_err_permission)
    OrbitFailureReason.ConnectionLost -> stringResource(R.string.orb_err_connection_lost)
    OrbitFailureReason.HostNotFound -> stringResource(R.string.orb_err_host_not_found)
    OrbitFailureReason.ConnectTimedOut -> stringResource(R.string.orb_err_timeout)
    OrbitFailureReason.Generic -> stringResource(R.string.orb_err_generic)
}

@Composable
fun OrbitScreen(vm: OrbitViewModel = viewModel()) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val connectionState by vm.connectionState.collectAsStateWithLifecycle()
    val discoveredHosts by vm.discoveredHosts.collectAsStateWithLifecycle()
    val discoveryTimedOut by vm.discoveryTimedOut.collectAsStateWithLifecycle()
    val orbitPeers by vm.orbitPeers.collectAsStateWithLifecycle()
    val currentSession by vm.currentSession.collectAsStateWithLifecycle()

    val discoveryPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.NEARBY_WIFI_DEVICES
    else
        Manifest.permission.ACCESS_FINE_LOCATION

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.onDiscoveryPermissionGranted()
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { vm.joinFromQr(it) }
    }

    val scanQrPrompt = stringResource(R.string.orb_scan_qr)

    LaunchedEffect(Unit) {
        PendingOrbitJoin.consume()?.let { vm.joinFromQr(it) }
    }

    val startMenu: @Composable (Boolean) -> Unit = { waitingForSync ->
        OrbitStartMenu(
            connectionState = connectionState,
            discoveredHosts = discoveredHosts,
            discoveryTimedOut = discoveryTimedOut,
            orbitPeers = orbitPeers,
            hostDisplayName = currentSession?.hostDeviceName,
            waitingForSync = waitingForSync,
            qrPayload = { vm.buildJoinQrPayload() },
            onStartSession = { vm.startSession() },
            onDiscoverSessions = { vm.discoverSessions() },
            onJoinHost = { vm.joinHost(it) },
            onCancel = { vm.stopSession() },
            onScanQr = {
                scanLauncher.launch(
                    ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setCaptureActivity(OrbitCaptureActivity::class.java)
                        .setOrientationLocked(true)
                        .setPrompt(scanQrPrompt)
                )
            }
        )
    }

    TabSurface {
        Box(Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is OrbitSongsUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is OrbitSongsUiState.PermissionRequired -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.orb_perm_ask), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { permissionLauncher.launch(discoveryPermission) }) {
                            Text(stringResource(R.string.song_scr_grant))
                        }
                    }
                }

                is OrbitSongsUiState.NotConnected -> startMenu(false)

                is OrbitSongsUiState.Session -> {
                    Column(Modifier.fillMaxSize()) {
                        OwnerDropdown(
                            owners = state.owners,
                            selected = state.selected,
                            onSelectSession = { vm.selectSession() },
                            onSelectOwner = { vm.selectOwner(it.deviceId) }
                        )

                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            val selected = state.selected
                            when {
                                selected == null ->
                                    startMenu(state.owners.isEmpty() || state.owners.any { !it.isSynced })

                                !selected.isSynced -> SyncingPlaceholder()

                                else -> OrbitSongList(songs = state.songs)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrbitStartMenu(
    connectionState: OrbitConnectionState,
    discoveredHosts: List<DiscoveredHost>,
    discoveryTimedOut: Boolean,
    orbitPeers: List<OrbitPeerIdentity>,
    hostDisplayName: String?,
    waitingForSync: Boolean,
    qrPayload: () -> String,
    onStartSession: () -> Unit,
    onDiscoverSessions: () -> Unit,
    onJoinHost: (DiscoveredHost) -> Unit,
    onCancel: () -> Unit,
    onScanQr: () -> Unit
) {
    val showButtons = connectionState !is OrbitConnectionState.Connected &&
            (discoveryTimedOut ||
                    (connectionState !is OrbitConnectionState.Discovering && connectionState !is OrbitConnectionState.Connecting))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = LocalPlayerBarTotalHeight.current + 16.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    connectionState is OrbitConnectionState.Connected && connectionState.isGroupOwner -> {
                        Text(
                            stringResource(R.string.orb_hosting_session),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(16.dp))

                        val payload = qrPayload()
                        val bitmap = remember(payload) { OrbitQrCode.generate(payload) }
                        bitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = stringResource(R.string.orb_show_qr),
                                modifier = Modifier.size(220.dp)
                            )
                        } ?: Text(stringResource(R.string.orb_qr_error))
                        Spacer(Modifier.height(16.dp))

                        if (orbitPeers.isEmpty()) {
                            Text(stringResource(R.string.orb_no_one_joined))
                        } else {
                            Text(
                                stringResource(R.string.orb_connected_peers),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(Modifier.height(8.dp))
                            orbitPeers.forEach { peer ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                    Text(peer.deviceName)
                                }
                                ListDivider()
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onCancel, shape = CircleShape) {
                            Text(stringResource(R.string.orb_stop_hosting))
                        }
                    }

                    connectionState is OrbitConnectionState.Connected -> {
                        Text(
                            stringResource(R.string.orb_connected),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.orb_joined_session, hostDisplayName ?: ""))
                        if (waitingForSync) {
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.orb_waiting_sync))
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onCancel, shape = CircleShape) {
                            Text(stringResource(R.string.orb_leave_session))
                        }
                    }

                    connectionState is OrbitConnectionState.Discovering && discoveredHosts.isEmpty() -> {
                        Text(stringResource(R.string.orb_no_peer))
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.orb_searching))
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = onCancel, shape = CircleShape) {
                            Text(stringResource(R.string.cancel))
                        }
                    }

                    connectionState is OrbitConnectionState.Discovering -> {
                        Text(
                            stringResource(R.string.orb_nearby_hosts),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(Modifier.height(8.dp))
                        discoveredHosts.forEach { host ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onJoinHost(host) }
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(host.deviceName)
                                    if (host.sessionName.isNotEmpty()) {
                                        Text(
                                            host.sessionName,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            ListDivider(hasArt = false)
                        }
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = onCancel, shape = CircleShape) {
                            Text(stringResource(R.string.cancel))
                        }
                    }

                    connectionState is OrbitConnectionState.Connecting -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.orb_connecting))
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = onCancel, shape = CircleShape) {
                            Text(stringResource(R.string.cancel))
                        }
                    }

                    discoveryTimedOut -> {
                        Text(stringResource(R.string.orb_no_hosts_found), textAlign = TextAlign.Center)
                    }

                    connectionState is OrbitConnectionState.Failed -> {
                        Text(connectionState.reason.message(), textAlign = TextAlign.Center)
                    }

                    else -> {
                        Text(stringResource(R.string.orb_no_peer), textAlign = TextAlign.Center)
                    }
                }
            }
        }

        if (showButtons) {
            StartJoinButtons(onStartSession, onDiscoverSessions, onScanQr)
        }
    }
}

@Composable
private fun StartJoinButtons(
    onStartSession: () -> Unit,
    onDiscoverSessions: () -> Unit,
    onScanQr: () -> Unit
) {
    val pad = 12.dp

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(25.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary)
    ) {
        Column(
            modifier = Modifier.padding(pad),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(pad)
        ) {
            Text(text = stringResource(R.string.orb_session_con))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(pad)
            ) {
                Button(
                    onClick = onStartSession,
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.orb_start))
                }

                VerticalDivider()

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDiscoverSessions,
                        shape = RoundedCornerShape(15.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.orb_join))
                    }
                    Button(
                        onClick = onScanQr,
                        shape = RoundedCornerShape(15.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.orb_scan_qr))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnerDropdown(
    owners: List<OrbitOwner>,
    selected: OrbitOwner?,
    onSelectSession: () -> Unit,
    onSelectOwner: (OrbitOwner) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val sessionLabel = stringResource(R.string.orb_session_con)
    val syncingIcon: @Composable () -> Unit = {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.padding(12.dp)
    ) {
        OutlinedTextField(
            value = selected?.displayName ?: sessionLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.set_scr_library)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(
                    ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true
                )
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Always first, so the session controls are never more than a tap away
            DropdownMenuItem(
                text = { Text(sessionLabel, fontWeight = FontWeight.Bold) },
                onClick = {
                    onSelectSession()
                    expanded = false
                }
            )
            if (owners.isNotEmpty()) HorizontalDivider()

            owners.forEach { owner ->
                DropdownMenuItem(
                    text = { Text(owner.displayName) },
                    trailingIcon = if (owner.isSynced) null else syncingIcon,
                    onClick = {
                        onSelectOwner(owner)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SyncingPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = LocalPlayerBarTotalHeight.current),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.orb_syncing))
    }
}

@Composable
private fun OrbitSongList(songs: List<OrbitSong>) {
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.orb_empty_lib))
        }
        return
    }

    val (listItems, letterIndex) = remember(songs) { groupSongs(songs) }
    val letters = remember(letterIndex) { letterIndex.keys.toList() }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var activeLetter by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 20.dp),
            contentPadding = PaddingValues(bottom = LocalPlayerBarTotalHeight.current)
        ) {
            itemsIndexed(items = listItems, key = { _, item ->
                when (item) {
                    is OrbitSongListItem.Header -> "header_${item.letter}"
                    is OrbitSongListItem.Item -> item.song.orbitId
                }
            }) { index, item ->
                when (item) {
                    is OrbitSongListItem.Header -> SectionHeader(item.letter)
                    is OrbitSongListItem.Item -> {
                        Column {
                            OrbitSongRow(song = item.song)

                            val nextItem = listItems.getOrNull(index + 1)
                            if (nextItem !is OrbitSongListItem.Header && nextItem != null) {
                                ListDivider()
                            }
                        }
                    }
                }
            }
        }

        AlphabetScroller(
            letters = letters,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(bottom = LocalPlayerBarTotalHeight.current),
            onLetterSelected = { letter ->
                activeLetter = letter
                val index = letterIndex[letter] ?: return@AlphabetScroller
                scope.launch { listState.scrollToItem(index) }
            },
            onScrubEnd = {
                scope.launch {
                    delay(600.milliseconds)
                    activeLetter = null
                }
            }
        )

        activeLetter?.let { letter ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(90.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}