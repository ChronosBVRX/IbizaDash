package com.chronosbvrx.ibizadash.nav

import org.json.JSONObject

/**
 * Salida de mensajes estéreo -> teléfono por Wi-Fi Direct.
 *
 * Es el único canal de datos de la app: rutas, estado de navegación, media,
 * avisos, clima y OTA viajan por el mismo TCP. Bluetooth no se usa para datos.
 */
object NavLink {
    /** Envíos asíncronos para callers que corren en el hilo principal. */
    private val tx = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "NavLinkTx").apply { isDaemon = true }
    }

    fun broadcast(obj: JSONObject): Boolean = WifiNavBridgeServer.broadcast(obj)

    fun sendToPhone(obj: JSONObject): Boolean = WifiNavBridgeServer.sendToPhone(obj)

    /** Encola un envío sin bloquear el hilo que llama (UI/GPS). */
    fun sendAsync(obj: JSONObject) {
        tx.execute {
            try {
                sendToPhone(obj)
            } catch (_: Exception) {
            }
        }
    }

    /** Encola un envío a todos los clientes conectados. */
    fun broadcastAsync(obj: JSONObject) {
        tx.execute {
            try {
                broadcast(obj)
            } catch (_: Exception) {
            }
        }
    }

    fun wifiActive(): Boolean = WifiNavBridgeServer.hasClients()
}
