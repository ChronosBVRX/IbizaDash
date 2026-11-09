package com.chronosbvrx.ibizadash.car

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log

class CarSignals(
    private val context: Context,
    private val onUpdate: (Signals) -> Unit
) {
    data class Signals(
        val speedKmh: Float,
        val reverse: Boolean,
        val source: String
    )

    private val handler = Handler(Looper.getMainLooper())
    private var gpsKmh = 0f
    private var started = false

    /** OBD speed (most accurate). Null when the adapter is not streaming. */
    @Volatile
    private var obdKmh: Float? = null
    @Volatile
    private var obdKmhAtMs = 0L

    fun setGpsSpeed(kmh: Float) {
        gpsKmh = if (kmh.isFinite()) kmh.coerceAtLeast(0f) else 0f
    }

    /** Called from the OBD listener: OBD wins over CAN and GPS. */
    fun setObdSpeed(kmh: Float?) {
        obdKmh = if (kmh == null || !kmh.isFinite() || kmh < 0f) null else kmh
        obdKmhAtMs = android.os.SystemClock.elapsedRealtime()
    }

    private val systemProperties: Class<*>? by lazy {
        try {
            Class.forName("android.os.SystemProperties")
        } catch (e: Throwable) {
            null
        }
    }

    private val getPropMethod by lazy {
        try {
            systemProperties?.getMethod("get", String::class.java)
        } catch (e: Throwable) {
            null
        }
    }

    private val runnable = object : Runnable {
        override fun run() {
            emit()
            handler.postDelayed(this, 500L)
        }
    }

    fun start() {
        if (started) return
        started = true
        handler.post(runnable)
    }

    fun stop() {
        started = false
        handler.removeCallbacks(runnable)
    }

    private fun emit() {
        // OBD es la fuente de referencia: se conserva 10 s para que uno o dos
        // paquetes perdidos no hagan parpadear el velocímetro entre OBD y GPS.
        // El fallback solo aparece si el adaptador deja de contestar de verdad.
        val fresh = android.os.SystemClock.elapsedRealtime() - obdKmhAtMs < 10_000L
        val obd = if (fresh) obdKmh else null
        val canSpeed = readProp("persist.can.speed")?.toFloatOrNull()
        val hasCan = canSpeed != null && canSpeed > 0.5f
        // Priority: OBD (ECU, most accurate) > CAN box > GPS (least accurate).
        val speed: Float
        val source: String
        if (obd != null) {
            speed = obd
            source = "OBD"
        } else if (hasCan) {
            speed = canSpeed!!
            source = "CAN"
        } else {
            speed = gpsKmh
            source = "GPS"
        }
        val reverse = try {
            Settings.System.getInt(
                context.contentResolver,
                "REVERSE_STATUS_FOR_PROTOCOL",
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
        onUpdate(Signals(speed, reverse, source))
    }

    private fun readProp(name: String): String? {
        return try {
            getPropMethod?.invoke(null, name) as? String
        } catch (e: Throwable) {
            Log.w("CarSignals", "prop $name: ${e.message}")
            null
        }
    }
}
