package com.chronosbvrx.ibizadash.nav

/**
 * Posiciona el coche SOBRE la carretera en lugar del punto GPS crudo.
 *
 * Todo el cálculo es local; el estéreo no consulta ningún servicio de red.
 * Prioridad:
 *  1. Proyección sobre la ruta activa (gratis, la calcula [RouteNavigator]).
 *  2. Fix crudo suavizado (filtro exponencial + rechazo de saltos).
 */
object RoadSnapper {
    private const val JUMP_REJECT_M = 90.0

    @Volatile
    private var shownLat = Double.NaN

    @Volatile
    private var shownLon = Double.NaN

    fun reset() {
        shownLat = Double.NaN
        shownLon = Double.NaN
    }

    /**
     * Snap local sincrónico: proyección sobre la ruta o fix suavizado.
     * Devuelve Triple(lat, lon, snapped).
     *
     * Con mala exactitud se devuelve el fix crudo: suavizar con ±60 m de
     * error hace que el coche se quede clavado en la misma cuadra.
     */
    fun snapLocal(
        lat: Double,
        lon: Double,
        speedKmh: Float,
        accuracyM: Float
    ): Triple<Double, Double, Boolean> {
        RouteNavigator.projectOnRoute(lat, lon)?.let { (plat, plon) ->
            shownLat = plat
            shownLon = plon
            return Triple(plat, plon, true)
        }
        if (accuracyM > 30f) {
            shownLat = lat
            shownLon = lon
            return Triple(lat, lon, false)
        }
        // Rechazo de saltos: ignora fixes absurdos mientras el coche se mueve.
        if (!shownLat.isNaN() && speedKmh > 8 && accuracyM > 0 && accuracyM < 200) {
            val jump = geoDist(shownLat, shownLon, lat, lon)
            if (jump > JUMP_REJECT_M) {
                return Triple(shownLat, shownLon, false)
            }
        }
        if (shownLat.isNaN()) {
            shownLat = lat
            shownLon = lon
            return Triple(lat, lon, false)
        }
        // Suavizado exponencial: estable a baja velocidad, reactivo a alta.
        val alpha = when {
            speedKmh < 5 -> 0.25
            speedKmh < 40 -> 0.45
            else -> 0.65
        }
        shownLat += (lat - shownLat) * alpha
        shownLon += (lon - shownLon) * alpha
        return Triple(shownLat, shownLon, false)
    }

    private fun geoDist(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) *
            kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return 2 * r * kotlin.math.asin(kotlin.math.sqrt(a))
    }
}
