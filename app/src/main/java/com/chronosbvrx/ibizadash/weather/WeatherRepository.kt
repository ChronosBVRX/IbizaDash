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
     */
    data class State(
        val tempC: Double? = null,
        val code: Int = -1,
        val text: String = ""
    )

    @Volatile
    var state: State = State()
        private set

    /** Se invoca desde el hilo del puente; la UI debe pasar a su hilo. */
    @Volatile
    var onUpdate: ((State) -> Unit)? = null

    fun update(tempC: Double?, code: Int, text: String) {
        val cleanTemp = tempC?.takeIf { !it.isNaN() }
        state = State(cleanTemp, code, text)
        try {
            onUpdate?.invoke(state)
        } catch (_: Exception) {
        }
    }
}
