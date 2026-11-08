package com.chronosbvrx.ibizacompanion

import android.content.Context
import com.chronosbvrx.ibizacompanion.data.StereoPrefs
import org.json.JSONObject

/**
 * Punto único para que la UI use el enlace que mantiene el servicio.
 * El único transporte es Wi-Fi Direct/TCP; no hay respaldo Bluetooth.
 */
object BridgeLink {
    @Volatile
    var router: BridgeRouter? = null

    fun isConnected(): Boolean = router?.isConnected() == true

    fun isWifiActive(): Boolean = router?.isWifiActive() == true

    fun send(obj: JSONObject): Boolean {
        val r = router ?: return false
        return r.isConnected() && r.send(obj)
    }

    fun sendRouteSet(
        destinationName: String,
        totalDistanceMeters: Int,
        totalDurationSeconds: Int,
        encodedPolyline: String,
        steps: List<JSONObject>,
        trafficDelaySeconds: Int = 0,
        trafficSections: List<com.chronosbvrx.ibizacompanion.data.TomTomRouting.TrafficSection> = emptyList()
    ): Boolean {
        val r = router ?: return false
        return r.isConnected() && r.sendRouteSet(
            destinationName,
            totalDistanceMeters,
            totalDurationSeconds,
            encodedPolyline,
            steps,
            trafficDelaySeconds,
            trafficSections
        )
    }

    fun clearRoute(): Boolean {
        val r = router ?: return false
        return r.isConnected() && r.clearRoute()
    }

    fun scanPeers() {
        router?.scanPeers()
    }

    fun connectPeer(address: String) {
        router?.connectPeer(address)
    }

    fun reconnectWifi() {
        router?.reconnectWifi()
    }

    fun setWifiEnabled(context: Context, enabled: Boolean) {
        StereoPrefs.setWifiEnabled(context, enabled)
        router?.reconnectWifi()
    }
}
