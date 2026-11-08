package com.chronosbvrx.ibizacompanion.data

import org.json.JSONArray
import java.net.URL
import java.net.URLEncoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class PlaceResult(
    val displayName: String,
    val lat: Double,
    val lon: Double,
    val distanceKm: Double? = null
)

/** Distancia ortodrómica para ordenar resultados por cercanía. */
object GeoDistance {
    fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}

object NominatimClient {
    private const val BASE = "https://nominatim.openstreetmap.org/search"
    private const val UA = "IbizaCompanion/1.6 (personal; Mexico)"

    private val rateLock = Any()
    private var lastCallMs = 0L

    /**
     * Búsqueda local con prioridad a la zona actual. `bounded=0` deja que
     * Nominatim devuelva primero lo cercano sin descartar coincidencias buenas,
     * y `countrycodes=mx` mantiene todo el contexto en México.
     */
    fun searchNear(
        query: String,
        lat: Double,
        lon: Double,
        limit: Int = 8,
        radiusDeg: Double = 0.5
    ): List<PlaceResult> = search(query, lat to lon, limit, radiusDeg)

    /** Respaldo nacional cuando no hay nada en la zona. */
    fun searchMexico(query: String, limit: Int = 8): List<PlaceResult> =
        search(query, null, limit, 0.0)

    private fun search(
        query: String,
        near: Pair<Double, Double>?,
        limit: Int,
        radiusDeg: Double
    ): List<PlaceResult> {
        val expanded = SearchIntent.expand(query)
        if (expanded.isBlank()) return emptyList()
        throttle()
        return try {
            val viewbox = near?.let {
                val d = radiusDeg.coerceIn(0.1, 2.0)
                // viewbox = minLon,minLat,maxLon,maxLat
                "&viewbox=${it.second - d},${it.first - d},${it.second + d},${it.first + d}&bounded=0"
            } ?: ""
            val url = BASE +
                "?q=${URLEncoder.encode(expanded, "UTF-8")}" +
                "&format=json&limit=$limit&addressdetails=1" +
                "&countrycodes=mx&accept-language=es" + viewbox
            val conn = URL(url).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            conn.disconnect()
            android.util.Log.d("Nominatim", "code=$code q=$expanded len=${text.length}")
            if (code !in 200..299) return emptyList()
            val arr = JSONArray(text)
            val originLat = near?.first
            val originLon = near?.second
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val latV = o.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
                val lonV = o.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
                if (latV == 0.0 && lonV == 0.0) return@mapNotNull null
                val display = o.optString("display_name", "").ifBlank {
                    val address = o.optJSONObject("address")
                    listOf(
                        address?.optString("road").orEmpty(),
                        address?.optString("city").orEmpty(),
                        address?.optString("state").orEmpty()
                    ).filter { it.isNotBlank() }.distinct().joinToString(", ")
                }
                val distance = if (originLat != null && originLon != null) {
                    GeoDistance.km(originLat, originLon, latV, lonV)
                } else {
                    null
                }
                PlaceResult(display, latV, lonV, distance)
            }.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
        } catch (e: Exception) {
            android.util.Log.w("Nominatim", "search: ${e.message}")
            emptyList()
        }
    }

    /** Nominatim pide máximo ~1 petición por segundo. */
    private fun throttle() {
        synchronized(rateLock) {
            val now = System.currentTimeMillis()
            val wait = 1100L - (now - lastCallMs)
            if (wait > 0) {
                try {
                    Thread.sleep(wait)
                } catch (_: InterruptedException) {
                }
            }
            lastCallMs = System.currentTimeMillis()
        }
    }
}
