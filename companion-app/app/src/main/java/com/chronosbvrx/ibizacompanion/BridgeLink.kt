package com.chronosbvrx.ibizacompanion

import android.content.Context
import com.chronosbvrx.ibizacompanion.data.StereoPrefs
import org.json.JSONObject

/**
 * Punto único para que la UI use el enlace que mantiene el servicio.
 *
 * Si el servicio aún no está enlazado (por ejemplo, al abrir la app por
 * primera vez) las rutas se envían con un socket Bluetooth puntual para no
 * romper el flujo anterior.
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
        context: Context,
        destinationName: String,
        totalDistanceMeters: Int,
        totalDurationSeconds: Int,
        encodedPolyline: String,
        steps: List<JSONObject>
    ): Boolean {
        val json = BridgeRouter.routeJson(
            destinationName,
            totalDistanceMeters,
            totalDurationSeconds,
            encodedPolyline,
            steps
        )
        val r = router
        if (r != null && r.isConnected() && r.sendRouteSet(
                destinationName,
                totalDistanceMeters,
                totalDurationSeconds,
                encodedPolyline,
                steps
            )
        ) {
            return true
        }
        return oneShotBluetooth(context, json)
    }

    fun clearRoute(context: Context): Boolean {
        val json = JSONObject().put("type", "ROUTE_CLEAR")
        val r = router
        if (r != null && r.isConnected() && r.clearRoute()) return true
        return oneShotBluetooth(context, json)
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

    /**
     * Socket Bluetooth de emergencia. Se mantiene abierto un instante para que
     * el estéreo alcance a leer el mensaje antes del cierre (RST).
     */
    private fun oneShotBluetooth(context: Context, obj: JSONObject): Boolean {
        val client = BluetoothNavClient(context)
        return try {
            val device = client.findStereo() ?: return false
            if (!client.connect(device)) return false
            val ok = client.send(obj)
            if (ok) {
                try {
                    Thread.sleep(1200L)
                } catch (_: InterruptedException) {
                }
            }
            ok
        } catch (_: Exception) {
            false
        } finally {
            client.close()
        }
    }
}
