package com.chronosbvrx.ibizacompanion.data

import org.json.JSONArray
import java.net.URL

data class PlaceResult(
    val displayName: String,
    val lat: Double,
    val lon: Double
)

object NominatimClient {
    fun search(query: String, limit: Int = 6): List<PlaceResult> {
        return search(query, null, limit)
    }

    /**
     * Search near a point (bounded viewbox) so suggestions are local. Also
     * accepts intent-like queries ("gasolina", "comer", "farmacia"...).
     */
    fun searchNear(query: String, lat: Double, lon: Double, limit: Int = 8): List<PlaceResult> {
        return search(query, lat to lon, limit)
    }

    private fun search(query: String, near: Pair<Double, Double>?, limit: Int): List<PlaceResult> {
        if (query.isBlank()) return emptyList()
        val expanded = expandIntent(query)
        return try {
            val viewbox = near?.let {
                val d = 0.35
                // bounded=0: prefer nearby results but don't exclude good matches.
                "&viewbox=${it.second - d},${it.first - d},${it.second + d},${it.first + d}&bounded=0"
            } ?: ""
            val url = "https://nominatim.openstreetmap.org/search?q=${java.net.URLEncoder.encode(expanded, "UTF-8")}&format=json&limit=$limit&addressdetails=1$viewbox"
            val conn = URL(url).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("User-Agent", "IbizaCompanion/1.0")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            conn.disconnect()
            android.util.Log.d("Nominatim", "code=$code q=$expanded len=${text.length}")
            if (code !in 200..299) return emptyList()
            val arr = JSONArray(text)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PlaceResult(
                    displayName = o.optString("display_name", ""),
                    lat = o.optString("lat").toDoubleOrNull() ?: 0.0,
                    lon = o.optString("lon").toDoubleOrNull() ?: 0.0
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Turns everyday intents into useful place queries:
     * "comer" -> "restaurante", "gasolina" -> "gasolinera", etc.
     */
    private fun expandIntent(query: String): String {
        val q = query.lowercase().trim()
        val map = mapOf(
            "comer" to "restaurante",
            "comida" to "restaurante",
            "cenar" to "restaurante",
            "desayunar" to "cafetería",
            "café" to "cafetería",
            "cafe" to "cafetería",
            "gasolina" to "gasolinera",
            "gas" to "gasolinera",
            "combustible" to "gasolinera",
            "súper" to "supermercado",
            "super" to "supermercado",
            "supermercado" to "supermercado",
            "farmacia" to "farmacia",
            "medicinas" to "farmacia",
            "hospital" to "hospital",
            "urgencias" to "hospital",
            "doctor" to "hospital",
            "banco" to "banco",
            "cajero" to "cajero automático",
            "dinero" to "cajero automático",
            "hotel" to "hotel",
            "dormir" to "hotel",
            "estacionamiento" to "estacionamiento",
            "parking" to "estacionamiento",
            "taller" to "taller mecánico",
            "llantas" to "taller de llantas",
            "car wash" to "lavado de autos",
            "lavado" to "lavado de autos",
            "cerveza" to "cervecería",
            "bar" to "bar",
            "cine" to "cine",
            "playa" to "playa",
            "parque" to "parque",
            "tienda" to "tienda",
            "oxxo" to "OXXO",
            "farmacias" to "farmacia",
            "hamburguesa" to "hamburguesería",
            "pizza" to "pizzería",
            "tacos" to "taquería",
            "sushi" to "sushi",
            "pan" to "panadería",
            "flores" to "florería"
        )
        return map[q] ?: query
    }
}