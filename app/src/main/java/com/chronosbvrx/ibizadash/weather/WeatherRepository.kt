package com.chronosbvrx.ibizadash.weather

/**
 * Clima recibido del teléfono por Wi-Fi Direct.
 *
 * El estéreo no consulta Internet: IbizaCompanion descarga Open-Meteo y manda
 * el resultado con el mensaje `WEATHER` del puente.
 */
object WeatherRepository {
    /**
     * @param tempC temperatura actual en grados Celsius; null si aún no hay dato.
     * @param code código WMO de Open-Meteo (https://open-meteo.com/en/docs).
     * @param text descripción breve para accesibilidad.
     * Los campos extra son opcionales: el teléfono antiguo solo manda tempC/code/text.
     */
    data class State(
        val tempC: Double? = null,
        val code: Int = -1,
        val text: String = "",
        val maxC: Double? = null,
        val minC: Double? = null,
        val humidityPct: Int? = null,
        val windKmh: Double? = null,
        val feelsLikeC: Double? = null,
        val updatedAtMs: Long = 0L
    )

    @Volatile
    var state: State = State()
        private set

    /** Se invoca desde el hilo del puente; la UI debe pasar a su hilo. */
    @Volatile
    var onUpdate: ((State) -> Unit)? = null

    fun update(
        tempC: Double?,
        code: Int,
        text: String,
        maxC: Double? = null,
        minC: Double? = null,
        humidityPct: Int? = null,
        windKmh: Double? = null,
        feelsLikeC: Double? = null
    ) {
        val cleanTemp = tempC?.takeIf { !it.isNaN() }
        fun clean(v: Double?): Double? = v?.takeIf { !it.isNaN() && !it.isInfinite() }
        state = State(
            cleanTemp, code, text,
            clean(maxC), clean(minC),
            humidityPct?.coerceIn(0, 100),
            clean(windKmh)?.coerceAtLeast(0.0),
            clean(feelsLikeC),
            System.currentTimeMillis()
        )
        try {
            onUpdate?.invoke(state)
        } catch (_: Exception) {
        }
    }
}
