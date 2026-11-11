package com.chronosbvrx.ibizadash

import android.content.Context

object Prefs {
    private const val FILE = "ibizadash"
    private const val SHOW_WEATHER = "show_weather"
    private const val USE_MPH = "use_mph"
    private const val DEMO_MEDIA = "demo_media"
    private const val MAP_FOLLOW = "map_follow"
    private const val OBD_MAC = "obd_mac"
    private const val OBD_DEMO = "obd_demo"
    private const val OBD_PIN = "obd_pin"

    private const val TOTAL_ODOMETER_KM = "total_odometer_km"
    private const val LAST_OIL_CHANGE_KM = "last_oil_change_km"
    private const val LAST_OIL_LEVEL_CHECK_MS = "last_oil_level_check_ms"
    private const val BRIGHTNESS_PCT = "brightness_pct"
    private const val NIGHT_MODE = "night_mode"
    private const val VOICE_ON = "voice_on"
    private const val P2P_5GHZ = "p2p_5ghz"
    private const val NAV_GPS_SOURCE = "nav_gps_source"

    private fun p(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun showWeather(context: Context): Boolean =
        p(context).getBoolean(SHOW_WEATHER, true)

    fun useMph(context: Context): Boolean =
        p(context).getBoolean(USE_MPH, false)

    fun setShowWeather(context: Context, value: Boolean) {
        p(context).edit().putBoolean(SHOW_WEATHER, value).apply()
    }

    fun setUseMph(context: Context, value: Boolean) {
        p(context).edit().putBoolean(USE_MPH, value).apply()
    }

    fun demoMedia(context: Context): Boolean =
        p(context).getBoolean(DEMO_MEDIA, false)

    fun setDemoMedia(context: Context, value: Boolean) {
        p(context).edit().putBoolean(DEMO_MEDIA, value).apply()
    }

    fun obdMac(context: Context): String? =
        p(context).getString(OBD_MAC, null)

    fun setObdMac(context: Context, mac: String?) {
        p(context).edit().putString(OBD_MAC, mac).apply()
    }

    fun obdDemo(context: Context): Boolean =
        p(context).getBoolean(OBD_DEMO, false)

    fun setObdDemo(context: Context, value: Boolean) {
        p(context).edit().putBoolean(OBD_DEMO, value).apply()
    }

    fun obdPin(context: Context): String =
        p(context).getString(OBD_PIN, "1234") ?: "1234"

    fun setObdPin(context: Context, pin: String) {
        p(context).edit().putString(OBD_PIN, pin).apply()
    }

    fun radioPreset(context: Context, slot: Int): Int =
        p(context).getInt("radio_preset_$slot", 0)

    fun setRadioPreset(context: Context, slot: Int, khz: Int) {
        p(context).edit().putInt("radio_preset_$slot", khz).apply()
    }

    /** Brillo de ventana elegido por el usuario (0–100). */
    fun brightnessPct(context: Context): Int =
        p(context).getInt(BRIGHTNESS_PCT, 80).coerceIn(0, 100)

    fun setBrightnessPct(context: Context, value: Int) {
        p(context).edit().putInt(BRIGHTNESS_PCT, value.coerceIn(0, 100)).apply()
    }

    /** Atenuación nocturna manual. Tema Home por defecto: noche. */
    fun nightMode(context: Context): Boolean =
        p(context).getBoolean(NIGHT_MODE, true)

    fun setNightMode(context: Context, value: Boolean) {
        p(context).edit().putBoolean(NIGHT_MODE, value).apply()
    }

    /** Avisos de voz de la navegación. */
    fun voiceOn(context: Context): Boolean =
        p(context).getBoolean(VOICE_ON, true)

    fun setVoiceOn(context: Context, value: Boolean) {
        p(context).edit().putBoolean(VOICE_ON, value).apply()
    }

    /**
     * Preferir la banda de 5 GHz para el grupo Wi-Fi Direct del estéreo.
     * El Bluetooth vive en 2,4 GHz: separar el enlace evita que el vídeo del
     * mapa o las teselas ahoguen el audio A2DP.
     */
    fun p2pPrefer5Ghz(context: Context): Boolean =
        p(context).getBoolean(P2P_5GHZ, true)

    fun setP2pPrefer5Ghz(context: Context, value: Boolean) {
        p(context).edit().putBoolean(P2P_5GHZ, value).apply()
    }

    /**
     * Fuente de posición para la navegación. Por defecto usa el GPS del
     * estéreo; el GPS del teléfono solo se activa como respaldo manual para
     * no llenar el enlace Wi-Fi con estados de posición cada segundo.
     */
    fun usePhoneGps(context: Context): Boolean =
        p(context).getString(NAV_GPS_SOURCE, "car") == "phone"

    fun setUsePhoneGps(context: Context, value: Boolean) {
        p(context)
            .edit()
            .putString(NAV_GPS_SOURCE, if (value) "phone" else "car")
            .apply()
    }

    fun totalOdometerKm(context: Context): Float =
        p(context).getFloat(TOTAL_ODOMETER_KM, 0f)

    fun addOdometerKm(context: Context, deltaKm: Float) {
        if (deltaKm <= 0f) return
        p(context).edit().putFloat(TOTAL_ODOMETER_KM, totalOdometerKm(context) + deltaKm).apply()
    }

    fun lastOilChangeKm(context: Context): Float =
        p(context).getFloat(LAST_OIL_CHANGE_KM, 0f)

    fun setLastOilChangeKm(context: Context, km: Float) {
        p(context).edit().putFloat(LAST_OIL_CHANGE_KM, km).apply()
    }

    fun lastOilLevelCheckMs(context: Context): Long =
        p(context).getLong(LAST_OIL_LEVEL_CHECK_MS, System.currentTimeMillis())

    fun setOilLevelCheckedNow(context: Context) {
        p(context).edit().putLong(LAST_OIL_LEVEL_CHECK_MS, System.currentTimeMillis()).apply()
    }

}
