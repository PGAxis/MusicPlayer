package dev.pgaxis.musicaxs.orbit

data class OrbitSession(
    val sessionName: String,
    val hostDeviceId: String,
    val hostDeviceName: String,
    val localDeviceId: String,
    val localDeviceName: String,
    val isHost: Boolean
)