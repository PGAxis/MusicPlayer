package dev.pgaxis.musicaxs.orbit

import dev.pgaxis.musicaxs.models.OrbitSong
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object OrbitProtocol {
    const val VERSION = 1
}

sealed interface OrbitMessage {
    data class Hello(
        val deviceId: String,
        val deviceName: String,
        val protocolVersion: Int = OrbitProtocol.VERSION
    ) : OrbitMessage

    data class LibraryChunk(
        val ownerDeviceId: String,
        val index: Int,
        val total: Int,
        val songs: List<OrbitSong>
    ) : OrbitMessage

    data class Roster(val peers: List<OrbitPeerIdentity>) : OrbitMessage

    data class StreamRequest(val requestId: String, val localSongId: Long, val position: Long) : OrbitMessage

    data object Unknown : OrbitMessage

    companion object {
        fun fromJson(json: JSONObject): OrbitMessage? = when (json.optString("type")) {
            "hello" -> {
                val id = json.optString("deviceId")
                if (id.isEmpty()) null
                else Hello(id, json.optString("deviceName"), json.optInt("proto", 1))
            }
            "library_chunk" -> parseLibraryChunk(json)
            "roster" -> parseRoster(json)
            "stream_request" -> parseStreamRequest(json)
            else -> Unknown
        }

        private fun parseLibraryChunk(json: JSONObject): OrbitMessage = try {
            val owner = json.getString("ownerDeviceId")
            val index = json.getInt("index")
            val total = json.getInt("total")
            if (owner.isEmpty() || total < 1 || index !in 0 until total) {
                Unknown
            } else {
                val array = json.getJSONArray("songs")
                LibraryChunk(
                    ownerDeviceId = owner,
                    index = index,
                    total = total,
                    songs = List(array.length()) { array.getJSONObject(it).toOrbitSong() }
                )
            }
        } catch (_: JSONException) {
            Unknown
        }

        private fun parseRoster(json: JSONObject): OrbitMessage = try {
            val array = json.getJSONArray("peers")
            val peers = (0 until array.length()).mapNotNull { i ->
                val p = array.getJSONObject(i)
                val id = p.optString("deviceId")
                if (id.isEmpty()) null
                else OrbitPeerIdentity(id, p.optString("deviceName"), p.optInt("proto", 1))
            }
            Roster(peers)
        } catch (_: JSONException) {
            Unknown
        }

        private fun parseStreamRequest(json: JSONObject): OrbitMessage = try {
            StreamRequest(json.getString("requestId"), json.getLong("songId"), json.getLong("position"))
        } catch (_: JSONException) {
            Unknown
        }
    }
}

fun OrbitMessage.toJson(): JSONObject = when (this) {
    is OrbitMessage.Hello -> JSONObject().apply {
        put("type", "hello")
        put("deviceId", deviceId)
        put("deviceName", deviceName)
        put("proto", protocolVersion)
    }
    is OrbitMessage.LibraryChunk -> JSONObject().apply {
        put("type", "library_chunk")
        put("ownerDeviceId", ownerDeviceId)
        put("index", index)
        put("total", total)
        put("songs", JSONArray().also { array -> songs.forEach { array.put(it.toWireJson()) } })
    }
    is OrbitMessage.Roster -> JSONObject().apply {
        put("type", "roster")
        put("peers", JSONArray().also { array ->
            peers.forEach { p ->
                array.put(JSONObject().apply {
                    put("deviceId", p.deviceId)
                    put("deviceName", p.deviceName)
                    put("proto", p.protocolVersion)
                })
            }
        })
    }
    is OrbitMessage.StreamRequest -> JSONObject().apply {
        put("type", "stream_request")
        put("requestId", requestId)
        put("songId", localSongId)
        put("position", position)
    }
    // Receive-only; never meant to be sent.
    is OrbitMessage.Unknown -> JSONObject().apply { put("type", "unknown") }
}

private fun OrbitSong.toWireJson(): JSONObject = JSONObject().apply {
    put("orbitId", orbitId)
    put("ownerDeviceId", ownerDeviceId)
    put("localSongId", localSongId)
    put("title", title)
    put("artist", artist)
    put("album", album)
    put("durationMs", durationMs)
    put("hasAlbumArt", hasAlbumArt)
}

private fun JSONObject.toOrbitSong(): OrbitSong = OrbitSong(
    orbitId = getString("orbitId"),
    ownerDeviceId = getString("ownerDeviceId"),
    localSongId = getLong("localSongId"),
    title = getString("title"),
    artist = getString("artist"),
    album = getString("album"),
    durationMs = getLong("durationMs"),
    hasAlbumArt = getBoolean("hasAlbumArt")
)