package dev.pgaxis.musicaxs.orbit

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PendingOrbitJoin {
    private val _payload = MutableStateFlow<String?>(null)
    val payload: StateFlow<String?> = _payload.asStateFlow()

    fun set(uri: String) {
        _payload.value = uri
    }

    fun consume(): String? {
        val current = _payload.value
        _payload.value = null
        return current
    }
}