package com.chronosbvrx.ibizacompanion.data

import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

/**
 * Photon (Komoot) para autocompletado y negocios. Se consulta con un `bbox`
 * alrededor de la ubicación actual para que los resultados sean de la zona y no
 * del otro lado del país; además se ordenan por distancia.
 */
object PhotonClient {
    fun search(
        query: String,
        lat: Double,
        lon: Double,
        limit: Int = 10,
        radiusDeg: Double = 0.35
    ): List<PlaceResult> {
        if (query.isBlank()) return emptyList()
        val expanded = SearchIntent.expand(query)
        return try {
            val d = radiusDeg.coerceIn(0.05, 2.0)
            val bbox = "${lon - d},${lat - d},${lon + d},${lat + d}"
            val q = URLEncoder.encode(expanded, "UTF-8")
            // Photon no soporta lang=es; los nombres vienen del OSM local.
            val url = "https://photon.komoot.io/api/" +
                "?q=$q&lat=$lat&lon=$lon&limit=$limit&bbox=$bbox"
            val conn = URL(url).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("User-Agent", "IbizaCompanion/1.6 (personal; Mexico)")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            conn.disconnect()
            android.util.Log.d("Photon", "code=$code q=$expanded bbox=$bbox len=${text.length}")
            if (code !in 200..299) return emptyList()
            val features = JSONObject(text).optJSONArray("features") ?: return emptyList()
            (0 until features.length()).mapNotNull { i ->
                val f = features.optJSONObject(i) ?: return@mapNotNull null
                val p = f.optJSONObject("properties") ?: JSONObject()
                val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates")
                    ?: return@mapNotNull null
                if (coords.length() < 2) return@mapNotNull null
                val lonV = coords.optDouble(0, 0.0)
                val latV = coords.optDouble(1, 0.0)
                if (latV == 0.0 && lonV == 0.0) return@mapNotNull null

                val name = p.optString("name", "")
                val street = p.optString("street", "")
                val number = p.optString("housenumber", "")
                val city = firstNonBlank(
                    p.optString("city"),
                    p.optString("town"),
                    p.optString("village"),
                    p.optString("locality"),
                    p.optString("county")
                )
                val state = p.optString("state", "")
                val road = listOf(street, number)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                val display = listOf(name, road, city, state)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(", ")
                val distance = GeoDistance.km(lat, lon, latV, lonV)
                PlaceResult(
                    displayName = display.ifBlank {
                        "%.4f, %.4f".format(latV, lonV)
                    },
                    lat = latV,
                    lon = lonV,
                    distanceKm = distance
                )
            }.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
        } catch (e: Exception) {
            android.util.Log.w("Photon", "search: ${e.message}")
            emptyList()
        }
    }

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }.orEmpty()
}
