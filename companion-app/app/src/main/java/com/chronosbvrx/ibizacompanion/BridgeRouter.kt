package com.chronosbvrx.ibizacompanion

import org.json.JSONArray
import org.json.JSONObject

/**
 * Enrutador de mensajes teléfono -> estéreo.
 *
 * Wi-Fi Direct es el único transporte de datos. El estéreo no usa Bluetooth
 * para rutas, portadas, mapas, clima ni OTA.
 */
class BridgeRouter(private val wifi: WifiBridgeClient) {

    fun isConnected(): Boolean = wifi.isConnected()

    fun isWifiActive(): Boolean = wifi.isConnected()

    fun send(obj: JSONObject): Boolean = wifi.isConnected() && wifi.send(obj)

    fun sendPing(): Boolean = send(pingJson())

    fun sendRouteSet(
        destinationName: String,
        totalDistanceMeters: Int,
        totalDurationSeconds: Int,
        encodedPolyline: String,
        steps: List<JSONObject>,
        trafficDelaySeconds: Int = 0,
        trafficSections: List<com.chronosbvrx.ibizacompanion.data.TomTomRouting.TrafficSection> = emptyList()
    ): Boolean = send(
        routeJson(
            destinationName,
            totalDistanceMeters,
            totalDurationSeconds,
            encodedPolyline,
            steps,
            trafficDelaySeconds,
            trafficSections
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

    /** Portada de 512 px: el Wi-Fi Direct tiene ancho de banda de sobra. */
    fun mediaArtMaxSize(): Int = 512

    fun scanPeers() = wifi.scanPeers()

    fun connectPeer(address: String) = wifi.connectPeer(address)

    fun reconnectWifi() = wifi.reconnectNow()

    fun close() {
        wifi.close()
    }

    companion object {
        fun routeJson(
            destinationName: String,
            totalDistanceMeters: Int,
            totalDurationSeconds: Int,
            encodedPolyline: String,
            steps: List<JSONObject>,
            trafficDelaySeconds: Int = 0,
            trafficSections: List<com.chronosbvrx.ibizacompanion.data.TomTomRouting.TrafficSection> = emptyList()
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
                .put("trafficDelayS", trafficDelaySeconds)
                .put(
                    "trafficSections",
                    com.chronosbvrx.ibizacompanion.data.TomTomRouting.sectionsJson(trafficSections)
                )
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
