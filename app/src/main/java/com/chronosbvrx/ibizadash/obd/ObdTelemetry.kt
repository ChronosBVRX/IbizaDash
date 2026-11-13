package com.chronosbvrx.ibizadash.obd

import android.os.SystemClock
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Telemetría del motor por OBD.
 *
 * La velocidad vive en [com.chronosbvrx.ibizadash.car.CarSignals] y sale del
 * GPS: este Ibiza 6J no entrega `010D`. El nivel de combustible es opcional y
 * solo se puebla cuando una fuente real lo publica (en demo, 62%); si no,
 * queda en `null` y la UI muestra `--` en lugar de inventar un porcentaje.
 */
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
    /** Ajuste de mezcla (PID 0106), no nivel de gasolina. */
    val fuelTrimPct: Float? = null,
    val timingAdvanceDeg: Float? = null,
    val batteryV: Float? = null,
    /** Nivel de combustible (%) leído de la ECU (PID 012F) si lo entrega. */
    val fuelLevelPct: Float? = null,
    /** Autonomía estimada que publica el CAN del coche (RemainKON), en km. */
    val fuelRangeKm: Int? = null,
    /** Velocidad de la ECU (PID 010D), si el adaptador la consigue. */
    val speedKmh: Float? = null,
    /** Aceleración longitudinal calculada con la velocidad OBD, en m/s². */
    val accelerationMps2: Float? = null,
    /** Consumo instantáneo calculado con MAF/fuel rate y velocidad, L/100 km. */
    val instantConsumptionL100: Float? = null,
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
            rpm != null || coolantC != null ||
                loadPct != null || throttlePct != null ||
                intakeC != null || batteryV != null ||
                fuelLevelPct != null || speedKmh != null
            )

    /** Segundos desde la última publicación de telemetría. */
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
