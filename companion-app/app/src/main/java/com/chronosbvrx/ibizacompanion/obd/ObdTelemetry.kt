package com.chronosbvrx.ibizacompanion.obd

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
    val fuelTrimPct: Float? = null,
    val timingAdvanceDeg: Float? = null,
    val batteryV: Float? = null,
    val fuelLevelPct: Float? = null,
    val fuelRangeKm: Int? = null,
    val speedKmh: Float? = null,
    val accelerationMps2: Float? = null,
    val instantConsumptionL100: Float? = null,
    val milOn: Boolean? = null,
    val dtcCount: Int? = null,
    val dtcs: List<String> = emptyList(),
    val vin: String? = null,
    val updatedAtMs: Long = SystemClock.elapsedRealtime()
) {
    val hasLiveData: Boolean
        get() = (connected || demo) && (
            rpm != null || coolantC != null ||
                loadPct != null || throttlePct != null ||
                intakeC != null || batteryV != null ||
                fuelLevelPct != null || speedKmh != null
            )

    fun ageSeconds(nowMs: Long = SystemClock.elapsedRealtime()): Long =
        ((nowMs - updatedAtMs).coerceAtLeast(0L)) / 1000L
}

object ObdRepository {
    private val listeners = CopyOnWriteArraySet<(ObdTelemetry) -> Unit>()

    @Volatile
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
