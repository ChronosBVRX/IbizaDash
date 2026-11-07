package com.chronosbvrx.ibizacompanion

import org.json.JSONArray
import org.json.JSONObject

/**
 * Enrutador de mensajes teléfono → estéreo.
 *
 * Wi-Fi Direct tiene prioridad; si el socket Wi-Fi no acepta la escritura se
 * cae a Bluetooth en el mismo envío. La app mantiene ambos canales vivos para
 * que el respaldo sea inmediato.
 */
class BridgeRouter(
    private val bt: BluetoothNavClient,
    private val wifi: WifiBridgeClient
) {
    fun isConnected(): Boolean = wifi.isConnected() || bt.isConnected()

    fun isWifiActive(): Boolean = wifi.isConnected()

    fun send(obj: JSONObject): Boolean {
        if (wifi.isConnected() && wifi.send(obj)) return true
        return bt.send(obj)
    }

    fun sendPing(): Boolean = send(pingJson())

    fun sendRouteSet(
        destinationName: String,
        totalDistanceMeters: Int,
        totalDurationSeconds: Int,
        encodedPolyline: String,
        steps: List<JSONObject>
    ): Boolean = send(
        routeJson(
            destinationName,
            totalDistanceMeters,
            totalDurationSeconds,
            encodedPolyline,
            steps
        )
    )

    fun sendStepUpdate(
        nextInstruction: String,
        maneuver: String,
        distanceToTurnMeters: Int,
        remainingDistanceMeters: Int,
        etaText: String
    ): Boolean = send(
        stepJson(
            nextInstruction,
            maneuver,
            distanceToTurnMeters,
            remainingDistanceMeters,
            etaText
        )
    )

    fun clearRoute(): Boolean = send(JSONObject().put("type", "ROUTE_CLEAR"))

    fun sendMediaArt(
        title: String,
        artist: String,
        album: String,
        isPlaying: Boolean,
        pngBase64: String
    ): Boolean = send(mediaArtJson(title, artist, album, isPlaying, pngBase64))

    /** Portada más grande cuando sobra ancho de banda. */
    fun mediaArtMaxSize(): Int = if (wifi.isConnected()) 512 else 256

    fun scanPeers() = wifi.scanPeers()

    fun connectPeer(address: String) = wifi.connectPeer(address)

    fun reconnectWifi() = wifi.reconnectNow()

    fun close() {
        wifi.close()
        bt.close()
    }

    companion object {
        fun routeJson(
            destinationName: String,
            totalDistanceMeters: Int,
            totalDurationSeconds: Int,
            encodedPolyline: String,
            steps: List<JSONObject>
        ): JSONObject {
            val arr = JSONArray()
            steps.forEach { arr.put(it) }
            return JSONObject()
                .put("type", "ROUTE_SET")
                .put("destinationName", destinationName)
                .put("totalDistanceMeters", totalDistanceMeters)
                .put("totalDurationSeconds", totalDurationSeconds)
                .put("encodedPolyline", encodedPolyline)
                .put("steps", arr)
        }

        fun stepJson(
            nextInstruction: String,
            maneuver: String,
            distanceToTurnMeters: Int,
            remainingDistanceMeters: Int,
            etaText: String
        ): JSONObject = JSONObject()
            .put("type", "NAV_STEP_UPDATE")
            .put("nextInstruction", nextInstruction)
            .put("maneuver", maneuver)
            .put("distanceToTurnMeters", distanceToTurnMeters)
            .put("remainingDistanceMeters", remainingDistanceMeters)
            .put("etaText", etaText)

        fun mediaArtJson(
            title: String,
            artist: String,
            album: String,
            isPlaying: Boolean,
            pngBase64: String
        ): JSONObject = JSONObject()
            .put("type", "MEDIA_ART")
            .put("title", title)
            .put("artist", artist)
            .put("album", album)
            .put("isPlaying", isPlaying)
            .put("pngBase64", pngBase64)

        fun pingJson(): JSONObject = JSONObject()
            .put("type", "PING")
            .put("ts", System.currentTimeMillis())
    }
}
