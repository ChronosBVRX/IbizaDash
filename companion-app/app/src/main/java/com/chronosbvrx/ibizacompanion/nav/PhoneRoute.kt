package com.chronosbvrx.ibizacompanion.nav

import org.json.JSONObject

/** Ruta activa en el teléfono, con los pasos completos de OSRM. */
data class PhoneRoute(
    val destinationName: String,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val encodedPolyline: String,
    val steps: List<JSONObject>,
    val destLat: Double,
    val destLon: Double
)

/** Ruta activa en el teléfono (para recalcular y para el mapa). */
object PhoneRouteRepository {
    @Volatile
    var current: PhoneRoute? = null
}

/** Estado de navegación que el teléfono manda al estéreo. */
data class PhoneNavSnapshot(
    val icon: String,
    val distText: String,
    val actionText: String,
    val thenText: String?,
    val remainingText: String,
    val etaText: String,
    val progress: Float,
    val offRoute: Boolean,
    val arrived: Boolean,
    val destName: String,
    val lat: Double,
    val lon: Double,
    val snappedLat: Double,
    val snappedLon: Double,
    val bearing: Float,
    val speedKmh: Float,
    val remainingM: Int,
    /** Datos de la próxima maniobra para los globos/escudos del estéreo. */
    val nextName: String = "",
    val nextRef: String = "",
    val nextLat: Double = 0.0,
    val nextLon: Double = 0.0,
    val nextIcon: String = "",
    val thenName: String? = null,
    val thenIcon: String? = null
)
