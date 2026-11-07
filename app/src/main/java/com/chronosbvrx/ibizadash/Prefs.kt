package com.chronosbvrx.ibizadash

import android.content.Context

object Prefs {
    private const val FILE = "ibizadash"
    private const val SHOW_WEATHER = "show_weather"
    private const val USE_MPH = "use_mph"
    private const val DEMO_MEDIA = "demo_media"
    private const val MAP_FOLLOW = "map_follow"
    private const val ONLINE_COVERS = "online_covers"
    private const val OBD_MAC = "obd_mac"
    private const val OBD_DEMO = "obd_demo"
    private const val OBD_PIN = "obd_pin"

    private const val TOTAL_ODOMETER_KM = "total_odometer_km"
    private const val LAST_OIL_CHANGE_KM = "last_oil_change_km"
    private const val LAST_OIL_LEVEL_CHECK_MS = "last_oil_level_check_ms"
    private const val ESTIMATED_RANGE_KM = "estimated_range_km"
    private const val FUEL_TANK_L = "fuel_tank_l"
    private const val FUEL_FULL_RAW = "fuel_full_raw"
    private const val FUEL_EMPTY_RAW = "fuel_empty_raw"
    private const val FUEL_UDS_ENABLED = "fuel_uds_enabled"
    private const val FUEL_UDS_DID = "fuel_uds_did"

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

    fun onlineCovers(context: Context): Boolean =
        p(context).getBoolean(ONLINE_COVERS, true)

    fun setOnlineCovers(context: Context, value: Boolean) {
        p(context).edit().putBoolean(ONLINE_COVERS, value).apply()
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

    fun estimatedRangeKm(context: Context): Float =
        p(context).getFloat(ESTIMATED_RANGE_KM, 520f)

    fun setEstimatedRangeKm(context: Context, km: Float) {
        p(context).edit().putFloat(ESTIMATED_RANGE_KM, km.coerceAtLeast(0f)).apply()
    }

    fun addEstimatedRangeKm(context: Context, deltaKm: Float) {
        setEstimatedRangeKm(context, estimatedRangeKm(context) + deltaKm)
    }

    fun refillTank(context: Context, km: Float) {
        setEstimatedRangeKm(context, km)
    }

    // --- Nivel de combustible real (UDS del cuadro de instrumentos) ---

    /** Capacidad del tanque del Ibiza 6J: 45 litros. */
    fun fuelTankLiters(context: Context): Float =
        p(context).getFloat(FUEL_TANK_L, 45f).coerceIn(20f, 90f)

    fun setFuelTankLiters(context: Context, liters: Float) {
        p(context).edit().putFloat(FUEL_TANK_L, liters.coerceIn(20f, 90f)).apply()
    }

    /** Valor UDS crudo con el tanque lleno (calibración). Por defecto 45 L. */
    fun fuelFullRaw(context: Context): Int =
        p(context).getInt(FUEL_FULL_RAW, 45).coerceIn(1, 255)

    /** Valor UDS crudo con el tanque vacío/reserva (calibración). */
    fun fuelEmptyRaw(context: Context): Int =
        p(context).getInt(FUEL_EMPTY_RAW, 0).coerceIn(0, 254)

    fun setFuelCalibration(context: Context, fullRaw: Int, emptyRaw: Int) {
        val full = fullRaw.coerceIn(1, 255)
        val empty = emptyRaw.coerceIn(0, 254)
        p(context).edit()
            .putInt(FUEL_FULL_RAW, full)
            .putInt(FUEL_EMPTY_RAW, if (empty >= full) 0 else empty)
            .apply()
    }

    fun fuelUdsEnabled(context: Context): Boolean =
        p(context).getBoolean(FUEL_UDS_ENABLED, true)

    fun setFuelUdsEnabled(context: Context, value: Boolean) {
        p(context).edit().putBoolean(FUEL_UDS_ENABLED, value).apply()
    }

    /** DID UDS del nivel de combustible; por defecto 0x2206 (VAG). */
    fun fuelUdsDid(context: Context): Int =
        p(context).getInt(FUEL_UDS_DID, 0x2206).coerceIn(0, 0xFFFF)

    fun setFuelUdsDid(context: Context, did: Int) {
        p(context).edit().putInt(FUEL_UDS_DID, did.coerceIn(0, 0xFFFF)).apply()
    }
}
