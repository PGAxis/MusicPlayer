package dev.pgaxis.musicaxs.orbit

import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Handler
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class DiscoveredHost(
    val deviceAddress: String,
    val deviceName: String,
    val orbitId: String,
    val sessionName: String
)

class OrbitConnectionManager(private val context: Context) {

    companion object {
        private const val SERVICE_TYPE = "_orbit._tcp"
        private const val TXT_ID = "orbit_id"
        private const val TXT_NAME = "orbit_name"
        private const val TXT_SESSION = "orbit_session"
        private val CONNECT_ACCEPT_TIMEOUT = 45.seconds
    }

    private val manager: WifiP2pManager =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel: WifiP2pManager.Channel =
        manager.initialize(context, context.mainLooper, null)

    private val _state = MutableStateFlow<OrbitConnectionState>(OrbitConnectionState.Idle)
    val state: StateFlow<OrbitConnectionState> = _state.asStateFlow()

    private var receiverRegistered = false

    private val _discoveredHosts = MutableStateFlow<List<DiscoveredHost>>(emptyList())
    val discoveredHosts: StateFlow<List<DiscoveredHost>> = _discoveredHosts.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<ConnectedPeer>>(emptyList())
    val connectedPeers: StateFlow<List<ConnectedPeer>> = _connectedPeers.asStateFlow()
    private val _knownPeers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())

    @Volatile
    private var joinTargetAddress: String? = null

    private var rediscoverJob: Job? = null
    private var awaitHostJob: Job? = null
    private val managerScope = CoroutineScope(Dispatchers.Main)

    private val handler = Handler(context.mainLooper)

    private var localServiceInfo: WifiP2pDnsSdServiceInfo? = null
    private var serviceRequest: WifiP2pDnsSdServiceRequest? = null

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private val receiver = OrbitBroadcastReceiver(
        manager = manager,
        channel = channel,
        onPeersChanged = ::handlePeersChanged,
        onConnectionChanged = ::handleConnectionChanged,
        onThisDeviceChanged = { }
    )

    fun start() {
        if (!receiverRegistered) {
            context.registerReceiver(receiver, intentFilter)
            receiverRegistered = true
        }
        try {
            manager.setDnsSdResponseListeners(
                channel,
                { _, _, _ -> },
                { _, txtRecord, srcDevice ->
                    val id = txtRecord[TXT_ID]
                    if (id.isNullOrEmpty()) return@setDnsSdResponseListeners
                    val name = txtRecord[TXT_NAME] ?: srcDevice.deviceName
                    val session = txtRecord[TXT_SESSION] ?: ""
                    Log.d("Orbit", "Discovered Orbit host via DNS-SD: $name ($id) at ${srcDevice.deviceAddress}")
                    val host = DiscoveredHost(srcDevice.deviceAddress, name, id, session)
                    _discoveredHosts.value = _discoveredHosts.value.filterNot { it.orbitId == id } + host
                }
            )
        } catch (_: SecurityException) { }
    }

    fun stop() {
        if (receiverRegistered) {
            context.unregisterReceiver(receiver)
            receiverRegistered = false
        }
        leaveSession()
    }

    fun reportMissingPermission() {
        _state.value = OrbitConnectionState.Failed(OrbitFailureReason.PermissionDenied)
    }

    fun startHosting(deviceId: String, deviceName: String, sessionName: String) {
        if (_state.value is OrbitConnectionState.Hosting) {
            Log.d("Orbit", "startHosting ignored, already hosting")
            return
        }
        _state.value = OrbitConnectionState.Hosting
        try {
            manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { scheduleCreateGroup(deviceId, deviceName, sessionName) }
                override fun onFailure(reason: Int) { scheduleCreateGroup(deviceId, deviceName, sessionName) }
            })
        } catch (e: SecurityException) {
            _state.value = OrbitConnectionState.Failed(OrbitFailureReason.PermissionDenied, e.message)
        }
    }

    private fun scheduleCreateGroup(deviceId: String, deviceName: String, sessionName: String) {
        handler.postDelayed({ createGroup(deviceId, deviceName, sessionName) }, 500)
    }

    private fun createGroup(deviceId: String, deviceName: String, sessionName: String) {
        try {
            manager.createGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d("Orbit", "Group created, now Group Owner")
                    registerLocalService(deviceId, deviceName, sessionName)
                    handleConnectionChanged()
                }
                override fun onFailure(reason: Int) {
                    _state.value = OrbitConnectionState.Failed(
                        OrbitFailureReason.Generic, "createGroup: ${reasonToString(reason)}"
                    )
                }
            })
        } catch (e: SecurityException) {
            _state.value = OrbitConnectionState.Failed(OrbitFailureReason.PermissionDenied, e.message)
        }
    }

    private fun registerLocalService(deviceId: String, deviceName: String, sessionName: String) {
        val record = mapOf(TXT_ID to deviceId, TXT_NAME to deviceName, TXT_SESSION to sessionName)
        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance("_orbit", SERVICE_TYPE, record)
        localServiceInfo = serviceInfo
        try {
            manager.addLocalService(channel, serviceInfo, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { Log.d("Orbit", "Advertising Orbit session via DNS-SD") }
                override fun onFailure(reason: Int) { Log.d("Orbit", "addLocalService failed: ${reasonToString(reason)}") }
            })
        } catch (_: SecurityException) { }
    }

    fun startDiscovering() {
        if (_state.value is OrbitConnectionState.Discovering ||
            _state.value is OrbitConnectionState.Connecting
        ) {
            Log.d("Orbit", "startDiscovering ignored, already ${_state.value}")
            return
        }
        _discoveredHosts.value = emptyList()
        _state.value = OrbitConnectionState.Discovering
        beginServiceDiscovery()

        rediscoverJob?.cancel()
        rediscoverJob = managerScope.launch {
            while (_state.value is OrbitConnectionState.Discovering) {
                kotlinx.coroutines.delay(10_000.milliseconds)
                if (_state.value is OrbitConnectionState.Discovering) {
                    beginServiceDiscovery()
                }
            }
        }
    }

    private fun beginServiceDiscovery() {
        try {
            val request = serviceRequest ?: WifiP2pDnsSdServiceRequest.newInstance().also { serviceRequest = it }
            manager.addServiceRequest(channel, request, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    manager.discoverServices(channel, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() { Log.d("Orbit", "Service discovery (re)started") }
                        override fun onFailure(reason: Int) { Log.d("Orbit", "discoverServices failed: ${reasonToString(reason)}") }
                    })
                }
                override fun onFailure(reason: Int) { Log.d("Orbit", "addServiceRequest failed: ${reasonToString(reason)}") }
            })
        } catch (e: SecurityException) {
            _state.value = OrbitConnectionState.Failed(OrbitFailureReason.PermissionDenied, e.message)
        }
    }

    /**
     * @param rediscover true for QR joins: nothing has been discovered yet, so run the same
     * repeating discovery the "Join" button uses until the scanned host shows up.
     */
    fun joinSession(orbitId: String, rediscover: Boolean = false) {
        Log.d("Orbit", "joinSession($orbitId, rediscover=$rediscover) in state ${_state.value}")

        if (_state.value is OrbitConnectionState.Connecting) {
            Log.d("Orbit", "joinSession ignored, already connecting")
            return
        }
        rediscoverJob?.cancel()
        awaitHostJob?.cancel()
        _state.value = OrbitConnectionState.Connecting

        if (rediscover) {
            _discoveredHosts.value = emptyList()
        }
        beginServiceDiscovery()
        if (rediscover) {
            rediscoverJob = managerScope.launch {
                while (_state.value is OrbitConnectionState.Connecting) {
                    kotlinx.coroutines.delay(10_000.milliseconds)
                    if (_state.value is OrbitConnectionState.Connecting) beginServiceDiscovery()
                }
            }
        }

        awaitHostJob = managerScope.launch {
            val list = withTimeoutOrNull(if (rediscover) 30.seconds else 15.seconds) {
                _discoveredHosts.first { list -> list.any { it.orbitId == orbitId } }
            }

            val match = list?.firstOrNull { it.orbitId == orbitId }
            if (match == null) {
                rediscoverJob?.cancel()
                Log.d("Orbit", "Timed out waiting for Orbit host $orbitId to appear nearby")
                _state.value = OrbitConnectionState.Failed(OrbitFailureReason.HostNotFound)
                return@launch
            }

            if (rediscover) {
                try {
                    manager.requestPeers(channel) { handlePeersChanged(it) }
                } catch (_: SecurityException) { }
                val known = withTimeoutOrNull(8.seconds) {
                    _knownPeers.first { peers ->
                        peers.any { it.deviceAddress.equals(match.deviceAddress, ignoreCase = true) && it.isGroupOwner }
                    }
                }
                Log.d(
                    "Orbit",
                    "Host ${match.deviceAddress} " +
                            if (known != null) "is listed as group owner" else "not listed as group owner after 8 s, connecting anyway"
                )
            }
            rediscoverJob?.cancel()

            Log.d("Orbit", "Found host ${match.deviceName} at ${match.deviceAddress}, connecting")
            actuallyConnect(match.deviceAddress)

            kotlinx.coroutines.delay(CONNECT_ACCEPT_TIMEOUT)
            if (_state.value is OrbitConnectionState.Connecting) {
                Log.d("Orbit", "Host did not accept the connection in time")
                try {
                    manager.cancelConnect(channel, null)
                } catch (_: SecurityException) { }
                _state.value = OrbitConnectionState.Failed(OrbitFailureReason.ConnectTimedOut)
            }
        }
    }

    private fun peerStatusName(status: Int): String = when (status) {
        WifiP2pDevice.CONNECTED -> "CONNECTED"
        WifiP2pDevice.INVITED -> "INVITED"
        WifiP2pDevice.FAILED -> "FAILED"
        WifiP2pDevice.AVAILABLE -> "AVAILABLE"
        WifiP2pDevice.UNAVAILABLE -> "UNAVAILABLE"
        else -> "UNKNOWN($status)"
    }

    private fun actuallyConnect(deviceAddress: String) {
        joinTargetAddress = deviceAddress
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
            groupOwnerIntent = 0
        }
        try {
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d("Orbit", "Connect request sent to $deviceAddress")
                }
                override fun onFailure(reason: Int) {
                    Log.d("Orbit", "connect() failed: ${reasonToString(reason)}")
                    _state.value = OrbitConnectionState.Failed(
                        OrbitFailureReason.Generic, "connect: ${reasonToString(reason)}"
                    )
                }
            })
        } catch (e: SecurityException) {
            _state.value = OrbitConnectionState.Failed(OrbitFailureReason.PermissionDenied, e.message)
        }
    }

    fun leaveSession() {
        joinTargetAddress = null
        rediscoverJob?.cancel()
        awaitHostJob?.cancel()
        try {
            manager.cancelConnect(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { Log.d("Orbit", "cancelConnect succeeded") }
                override fun onFailure(reason: Int) { Log.d("Orbit", "cancelConnect: ${reasonToString(reason)}") }
            })
        } catch (_: SecurityException) { }
        manager.stopPeerDiscovery(channel, null)

        localServiceInfo?.let { info ->
            try {
                manager.removeLocalService(channel, info, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { }
                    override fun onFailure(reason: Int) { }
                })
            } catch (_: SecurityException) { }
            localServiceInfo = null
        }
        serviceRequest?.let { req ->
            try {
                manager.removeServiceRequest(channel, req, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { }
                    override fun onFailure(reason: Int) { }
                })
            } catch (_: SecurityException) { }
            serviceRequest = null
        }

        try {
            manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d("Orbit", "Left group (removeGroup succeeded)")
                    forceIdleAndVerify()
                }
                override fun onFailure(reason: Int) {
                    Log.d("Orbit", "removeGroup reported failure: ${reasonToString(reason)}")
                    forceIdleAndVerify()
                }
            })
        } catch (e: SecurityException) {
            _state.value = OrbitConnectionState.Failed(OrbitFailureReason.PermissionDenied, e.message)
        }
    }

    private fun forceIdleAndVerify() {
        _connectedPeers.value = emptyList()
        _state.value = OrbitConnectionState.Idle
        handler.postDelayed({
            try {
                manager.requestConnectionInfo(channel) { info: WifiP2pInfo ->
                    if (info.groupFormed) {
                        Log.d("Orbit", "Still in a group after leaveSession -- correcting state")
                        _state.value = OrbitConnectionState.Connected(
                            isGroupOwner = info.isGroupOwner,
                            groupOwnerAddress = info.groupOwnerAddress?.hostAddress ?: ""
                        )
                    }
                }
            } catch (_: SecurityException) { }
        }, 800)
    }

    private fun handlePeersChanged(peers: WifiP2pDeviceList) {
        _knownPeers.value = peers.deviceList.toList()
        joinTargetAddress?.let { target ->
            peers.deviceList.firstOrNull { it.deviceAddress.equals(target, ignoreCase = true) }?.let {
                Log.d("Orbit", "Join target ${it.deviceName}: status=${peerStatusName(it.status)}, isGroupOwner=${it.isGroupOwner}")
            }
        }
        if (_state.value.let { it is OrbitConnectionState.Connected && it.isGroupOwner }) {
            refreshConnectedPeers()
        }
    }

    private fun handleConnectionChanged() {
        try {
            manager.requestConnectionInfo(channel) { info: WifiP2pInfo ->
                Log.d("Orbit", "Connection changed: groupFormed=${info.groupFormed}, isGroupOwner=${info.isGroupOwner}, state=${_state.value}")
                if (info.groupFormed) {
                    if (_state.value is OrbitConnectionState.Connecting && info.isGroupOwner) {
                        Log.d("Orbit", "Joining, but a group we own is reported -- dropping it")
                        awaitHostJob?.cancel()
                        try {
                            manager.removeGroup(channel, null)
                        } catch (_: SecurityException) { }
                        _state.value = OrbitConnectionState.Failed(
                            OrbitFailureReason.Generic, "Ended up as group owner while joining"
                        )
                        return@requestConnectionInfo
                    }
                    _state.value = OrbitConnectionState.Connected(
                        isGroupOwner = info.isGroupOwner,
                        groupOwnerAddress = info.groupOwnerAddress?.hostAddress ?: ""
                    )
                    if (info.isGroupOwner) refreshConnectedPeers()
                } else {
                    _connectedPeers.value = emptyList()
                    if (_state.value is OrbitConnectionState.Connected) {
                        _state.value = OrbitConnectionState.Failed(OrbitFailureReason.ConnectionLost)
                    }
                }
            }
        } catch (e: SecurityException) {
            _state.value = OrbitConnectionState.Failed(OrbitFailureReason.PermissionDenied, e.message)
        }
    }

    private fun refreshConnectedPeers() {
        try {
            manager.requestGroupInfo(channel) { group ->
                _connectedPeers.value = group?.clientList?.map {
                    ConnectedPeer(it.deviceAddress, it.deviceName)
                } ?: emptyList()
            }
        } catch (e: SecurityException) {
            Log.e("Orbit", "Missing permission for requestGroupInfo: ${e.message}")
        }
    }

    private fun reasonToString(reason: Int): String = when (reason) {
        WifiP2pManager.P2P_UNSUPPORTED -> "P2P unsupported on this device"
        WifiP2pManager.BUSY -> "System busy, try again"
        WifiP2pManager.ERROR -> "Internal error"
        else -> "Unknown ($reason)"
    }
}