package com.chronosbvrx.ibizadash.obd

import android.os.SystemClock
import java.util.concurrent.CopyOnWriteArraySet

data class ObdTelemetry(
    val connected: Boolean = false,
    val demo: Boolean = false,
    val error: String? = null,
    val deviceName: String? = null,
    val rpm: Int? = null,
    val coolantC: Float? = null,
    val loadPct: Float? = null,
    val throttlePct: Float? = null,
    val intakeC: Float? = null,
    val speedKmh: Int? = null,
    val fuelPct: Float? = null,
    /** Real fuel level read from the instrument cluster via UDS (DID 0x2206). */
    val fuelLevelPct: Float? = null,
    val fuelLevelLiters: Float? = null,
    val fuelRaw: Int? = null,
    val fuelSource: String? = null,
    val fuelTrimPct: Float? = null,
    val timingAdvanceDeg: Float? = null,
    val batteryV: Float? = null,
    val milOn: Boolean? = null,
    val dtcCount: Int? = null,
    val dtcs: List<String> = emptyList(),
    val vin: String? = null,
    val updatedAtMs: Long = SystemClock.elapsedRealtime()
) {
    /**
     * Hay al menos una lectura utilizable en pantalla.
     *
     * `connected` solo dice que el enlace con el adaptador está abierto; la ECU
     * puede estar sin responder (contacto apagado o PID no soportado). La UI usa
     * esta bandera para no afirmar "todo en orden" sin datos reales.
     */
    val hasLiveData: Boolean
        get() = (connected || demo) && (
            rpm != null || coolantC != null || speedKmh != null ||
                loadPct != null || throttlePct != null || intakeC != null ||
                batteryV != null || fuelPct != null || fuelLevelPct != null
            )

    /** Segundos desde la última publicación de telemetría. */
    fun ageSeconds(nowMs: Long = SystemClock.elapsedRealtime()): Long =
        ((nowMs - updatedAtMs).coerceAtLeast(0L)) / 1000L
}

object ObdRepository {
    private val listeners = CopyOnWriteArraySet<(ObdTelemetry) -> Unit>()

    var current = ObdTelemetry()
        private set

    fun addListener(listener: (ObdTelemetry) -> Unit) {
        listeners.add(listener)
        listener(current)
    }

    fun removeListener(listener: (ObdTelemetry) -> Unit) {
        listeners.remove(listener)
    }

    fun publish(state: ObdTelemetry) {
        current = state
        listeners.forEach { it(state) }
    }
}
