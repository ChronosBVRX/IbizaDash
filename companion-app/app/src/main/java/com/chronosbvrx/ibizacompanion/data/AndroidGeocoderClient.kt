package com.chronosbvrx.ibizacompanion.data

import android.content.Context
import android.location.Geocoder
import java.util.Locale

/**
 * Geocodificador del sistema (en un teléfono con Google Play es el de Google).
 * Se usa como fuente adicional para direcciones y negocios, limitado a la
 * ventana alrededor del coche y a México.
 */
object AndroidGeocoderClient {
    fun search(
        context: Context,
        query: String,
        lat: Double,
        lon: Double,
        limit: Int = 10,
        radiusDeg: Double = 0.35
    ): List<PlaceResult> {
        if (query.isBlank()) return emptyList()
        if (!Geocoder.isPresent()) return emptyList()
        val expanded = SearchIntent.expand(query)
        return try {
            val d = radiusDeg.coerceIn(0.05, 2.0)
            val geocoder = Geocoder(context, Locale("es", "MX"))
            val addresses = geocoder.getFromLocationName(
                expanded,
                limit,
                lat - d,
                lon - d,
                lat + d,
                lon + d
            ).orEmpty()
            addresses.mapNotNull { address ->
                val country = address.countryCode
                if (!country.isNullOrBlank() && !country.equals("MX", ignoreCase = true)) {
                    return@mapNotNull null
                }
                val display = address.getAddressLine(0)
                    ?: listOf(
                        address.thoroughfare,
                        address.locality,
                        address.adminArea
                    ).filterNotNull().joinToString(", ")
                if (display.isBlank()) return@mapNotNull null
                PlaceResult(
                    displayName = display,
                    lat = address.latitude,
                    lon = address.longitude,
                    distanceKm = GeoDistance.km(lat, lon, address.latitude, address.longitude)
                )
            }.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
        } catch (e: Exception) {
            android.util.Log.w("AndroidGeocoder", "search: ${e.message}")
            emptyList()
        }
    }
}
