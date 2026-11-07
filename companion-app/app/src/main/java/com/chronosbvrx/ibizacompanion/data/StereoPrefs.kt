package com.chronosbvrx.ibizacompanion.data

import android.content.Context

/** Preferencias del enlace con el estéreo (Bluetooth + Wi-Fi Direct). */
object StereoPrefs {
    private const val FILE = "stereo_prefs"
    private const val KEY_MAC = "stereo_mac"
    private const val KEY_WIFI_HOST = "wifi_host"
    private const val KEY_P2P_ADDRESS = "p2p_address"
    private const val KEY_WIFI_ENABLED = "wifi_enabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun mac(context: Context): String? =
        prefs(context).getString(KEY_MAC, null)

    fun setMac(context: Context, mac: String?) {
        prefs(context).edit().putString(KEY_MAC, mac).apply()
    }

    /** IP del estéreo en una red compartida (LAN/hotspot). Opcional. */
    fun wifiHost(context: Context): String? =
        prefs(context).getString(KEY_WIFI_HOST, null)

    fun setWifiHost(context: Context, host: String?) {
        prefs(context).edit().putString(KEY_WIFI_HOST, host?.trim()?.takeIf { it.isNotBlank() }).apply()
    }

    /** Dirección P2P del estéreo (Wi-Fi Direct). */
    fun p2pAddress(context: Context): String? =
        prefs(context).getString(KEY_P2P_ADDRESS, null)

    fun setP2pAddress(context: Context, address: String?) {
        prefs(context).edit().putString(KEY_P2P_ADDRESS, address?.trim()?.takeIf { it.isNotBlank() }).apply()
    }

    /** Si se desactiva, la app sólo usa Bluetooth (respaldo). */
    fun wifiEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WIFI_ENABLED, true)

    fun setWifiEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_WIFI_ENABLED, enabled).apply()
    }
}
