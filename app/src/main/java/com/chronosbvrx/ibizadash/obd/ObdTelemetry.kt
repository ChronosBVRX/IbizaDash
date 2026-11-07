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
)

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
