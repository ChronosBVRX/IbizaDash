package com.chronosbvrx.ibizacompanion.data

import android.content.Context
import android.util.LruCache
import java.util.Locale

/**
 * Búsqueda de lugares con contexto local y México:
 *  1. Photon con bbox de ~39 km (negocios y direcciones de la zona).
 *  2. Photon con bbox de ~100 km si la zona inmediata no tiene resultados.
 *  3. Nominatim con viewbox y `countrycodes=mx`.
 *  4. Nominatim nacional, solo si todo lo anterior falló.
 *
 * Los resultados se ordenan por distancia y se cachean 5 minutos para no
 * repetir peticiones mientras el usuario escribe.
 */
object PlaceSearch {
    private const val TTL_MS = 5 * 60 * 1000L
    private const val MAX_RESULTS = 10

    private data class Entry(val at: Long, val results: List<PlaceResult>)

    private val cache = object : LruCache<String, Entry>(32) {}

    fun search(context: Context, query: String, lat: Double, lon: Double): List<PlaceResult> {
        val raw = query.trim()
        if (raw.length < 2) return emptyList()
        val key = "${SearchIntent.normalize(raw)}|" +
            String.format(Locale.US, "%.2f", lat) + "|" +
            String.format(Locale.US, "%.2f", lon)
        cache.get(key)?.let { hit ->
            if (System.currentTimeMillis() - hit.at < TTL_MS) return hit.results
        }

        val expanded = SearchIntent.expand(raw)
        val category = SearchIntent.isCategory(raw)
        var out: List<PlaceResult> = if (category) {
            PhotonClient.search(expanded, lat, lon, MAX_RESULTS, radiusDeg = 0.35)
        } else {
            // Para direcciones y nombres, el geocodificador del teléfono suele
            // tener más cobertura; se combina con OSM.
            AndroidGeocoderClient.search(context, expanded, lat, lon, MAX_RESULTS, 0.35)
        }

        if (category || out.size < 4) {
            out = merge(
                out,
                PhotonClient.search(expanded, lat, lon, MAX_RESULTS, radiusDeg = 0.35)
            )
        }
        if (category && out.size < 4) {
            out = merge(
                out,
                AndroidGeocoderClient.search(context, expanded, lat, lon, MAX_RESULTS, 0.35)
            )
        }
        if (out.size < 4) {
            out = merge(
                out,
                PhotonClient.search(expanded, lat, lon, MAX_RESULTS, radiusDeg = 0.9)
            )
        }
        if (out.size < 4) {
            out = merge(
                out,
                NominatimClient.searchNear(expanded, lat, lon, 8, radiusDeg = 0.5)
            )
        }
        if (out.isEmpty()) {
            out = merge(out, NominatimClient.searchMexico(expanded, 8))
        }

        val ordered = out
            .sortedBy { it.distanceKm ?: Double.MAX_VALUE }
            .take(MAX_RESULTS)
        android.util.Log.d("PlaceSearch", "\"$expanded\" -> ${ordered.size} resultados")
        cache.put(key, Entry(System.currentTimeMillis(), ordered))
        return ordered
    }

    /** Une fuentes sin repetir el mismo lugar (nombre + coordenada). */
    private fun merge(a: List<PlaceResult>, b: List<PlaceResult>): List<PlaceResult> {
        val byKey = LinkedHashMap<String, PlaceResult>()
        (a + b).forEach { place ->
            val key = SearchIntent.normalize(place.displayName) + "|" +
                String.format(Locale.US, "%.4f", place.lat) + "," +
                String.format(Locale.US, "%.4f", place.lon)
            val previous = byKey[key]
            if (previous == null ||
                (place.distanceKm ?: Double.MAX_VALUE) <
                (previous.distanceKm ?: Double.MAX_VALUE)
            ) {
                byKey[key] = place
            }
        }
        return byKey.values.toList()
    }
}
