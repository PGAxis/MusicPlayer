package dev.pgaxis.musicaxs.orbit

sealed interface OrbitFailureReason {
    data object PermissionDenied : OrbitFailureReason
    data object ConnectionLost : OrbitFailureReason
    data object HostNotFound : OrbitFailureReason
    data object ConnectTimedOut : OrbitFailureReason
    data object Generic : OrbitFailureReason
}

sealed interface OrbitConnectionState {
    data object Idle : OrbitConnectionState
    data object Hosting : OrbitConnectionState
    data object Discovering : OrbitConnectionState
    data object Connecting : OrbitConnectionState
    data class Connected(
        val isGroupOwner: Boolean,
        val groupOwnerAddress: String
    ) : OrbitConnectionState
    data class Failed(val reason: OrbitFailureReason, val debugDetail: String? = null) : OrbitConnectionState
}

data class ConnectedPeer(val deviceAddress: String, val deviceName: String)