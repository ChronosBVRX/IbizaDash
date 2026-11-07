package com.chronosbvrx.ibizadash.nav

import org.json.JSONObject

/**
 * Selector de transporte para los mensajes estéreo → teléfono.
 *
 * Wi-Fi tiene prioridad porque las portadas y las teselas necesitan ancho de
 * banda; Bluetooth se usa cuando no hay ningún cliente Wi-Fi conectado. La
 * conexión Wi-Fi se valida con una escritura real, así que una sesión muerta
 * cae a Bluetooth en el mismo envío.
 */
object NavLink {
    /** Envíos asíncronos para callers que corren en el hilo principal. */
    private val tx = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "NavLinkTx").apply { isDaemon = true }
    }

    fun broadcast(obj: JSONObject): Boolean {
        if (WifiNavBridgeServer.hasClients() && WifiNavBridgeServer.broadcast(obj)) return true
        return BtNavBridgeServer.broadcast(obj)
    }

    fun sendToPhone(obj: JSONObject): Boolean {
        if (WifiNavBridgeServer.hasClients() && WifiNavBridgeServer.sendToPhone(obj)) return true
        return BtNavBridgeServer.sendToPhone(obj)
    }

    /** Encola un envío sin bloquear el hilo que llama (UI/GPS). */
    fun sendAsync(obj: JSONObject) {
        tx.execute {
            try {
                sendToPhone(obj)
            } catch (_: Exception) {
            }
        }
    }

    fun wifiActive(): Boolean = WifiNavBridgeServer.hasClients()
}
