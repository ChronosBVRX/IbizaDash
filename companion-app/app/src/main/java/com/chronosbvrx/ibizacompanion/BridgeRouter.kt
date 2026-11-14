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
        pngBase64: String,
        packageName: String = "",
        appLabel: String = ""
    ): Boolean = send(
        mediaArtJson(title, artist, album, isPlaying, pngBase64, packageName, appLabel)
    )

    fun sendObdTelemetry(t: com.chronosbvrx.ibizacompanion.obd.ObdTelemetry): Boolean =
        send(obdTelemetryJson(t))

    /** Portada de 384 px: suficiente para el panel y menos peso en el enlace. */
    fun mediaArtMaxSize(): Int = 384

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
            pngBase64: String,
            packageName: String = "",
            appLabel: String = ""
        ): JSONObject = JSONObject()
            .put("type", "MEDIA_ART")
            .put("title", title)
            .put("artist", artist)
            .put("album", album)
            .put("isPlaying", isPlaying)
            .put("pngBase64", pngBase64)
            .put("packageName", packageName)
            .put("appLabel", appLabel)

        fun pingJson(): JSONObject = JSONObject()
            .put("type", "PING")
            .put("ts", System.currentTimeMillis())

        fun obdTelemetryJson(t: com.chronosbvrx.ibizacompanion.obd.ObdTelemetry): JSONObject = JSONObject()
            .put("type", "OBD_TELEMETRY")
            .put("connected", t.connected)
            .put("demo", t.demo)
            .apply {
                t.error?.let { put("error", it) }
                t.deviceName?.let { put("deviceName", it) }
                t.rpm?.let { put("rpm", it) }
                t.coolantC?.let { put("coolantC", it.toDouble()) }
                t.loadPct?.let { put("loadPct", it.toDouble()) }
                t.throttlePct?.let { put("throttlePct", it.toDouble()) }
                t.intakeC?.let { put("intakeC", it.toDouble()) }
                t.fuelTrimPct?.let { put("fuelTrimPct", it.toDouble()) }
                t.timingAdvanceDeg?.let { put("timingAdvanceDeg", it.toDouble()) }
                t.batteryV?.let { put("batteryV", it.toDouble()) }
                t.fuelLevelPct?.let { put("fuelLevelPct", it.toDouble()) }
                t.fuelRangeKm?.let { put("fuelRangeKm", it) }
                t.speedKmh?.let { put("speedKmh", it.toDouble()) }
                t.accelerationMps2?.let { put("accelerationMps2", it.toDouble()) }
                t.instantConsumptionL100?.let { put("instantConsumptionL100", it.toDouble()) }
                t.milOn?.let { put("milOn", it) }
                t.dtcCount?.let { put("dtcCount", it) }
                if (t.dtcs.isNotEmpty()) {
                    val arr = org.json.JSONArray()
                    t.dtcs.forEach { arr.put(it) }
                    put("dtcs", arr)
                }
                t.vin?.let { put("vin", it) }
            }
    }
}
