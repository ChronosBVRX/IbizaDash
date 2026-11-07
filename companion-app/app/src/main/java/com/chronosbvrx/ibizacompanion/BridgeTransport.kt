package com.chronosbvrx.ibizacompanion

import org.json.JSONObject

/** Transporte del puente con el estéreo (Wi-Fi Direct o Bluetooth). */
interface BridgeTransport {
    fun isConnected(): Boolean

    /** Envía un objeto JSON. Devuelve false si el socket no está disponible. */
    fun send(obj: JSONObject): Boolean

    fun close()
}
