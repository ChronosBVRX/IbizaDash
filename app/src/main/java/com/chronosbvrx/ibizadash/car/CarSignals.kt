package com.chronosbvrx.ibizadash.car

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * Señales visibles del coche.
 *
 * La velocidad sale **solo del GPS**. El OBD de este coche no entrega 010D y
 * el CAN box no publica `persist.can.speed`, así que no se inventa una fuente
 * alternativa: el GPS (con la estimación de [GpsEngine]) es la única fuente.
 */
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

    fun setGpsSpeed(kmh: Float) {
        gpsKmh = if (kmh.isFinite()) kmh.coerceIn(0f, 260f) else 0f
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
        val reverse = try {
            Settings.System.getInt(
                context.contentResolver,
                "REVERSE_STATUS_FOR_PROTOCOL",
                0
            ) == 1
        } catch (_: Exception) {
            false
        }
        onUpdate(Signals(gpsKmh, reverse, "GPS"))
    }
}
