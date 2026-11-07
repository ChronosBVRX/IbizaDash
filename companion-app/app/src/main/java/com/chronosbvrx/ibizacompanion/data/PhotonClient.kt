package com.chronosbvrx.ibizacompanion.data

import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

/**
 * Photon (Komoot) geocoder: free, no API key, designed for autocomplete and
 * less aggressive rate limiting than public Nominatim.
 */
object PhotonClient {
    fun search(query: String, lat: Double, lon: Double, limit: Int = 8): List<PlaceResult> {
        if (query.isBlank()) return emptyList()
        val expanded = expandIntent(query)
        return try {
            val q = URLEncoder.encode(expanded, "UTF-8")
            val url = "https://photon.komoot.io/api/?q=$q&lat=$lat&lon=$lon&limit=$limit"
            val conn = URL(url).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("User-Agent", "IbizaCompanion/1.0")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            conn.disconnect()
            android.util.Log.d("Photon", "code=$code q=$expanded len=${text.length}")
            if (code !in 200..299) return emptyList()
            val features = JSONObject(text).optJSONArray("features") ?: return emptyList()
            (0 until features.length()).mapNotNull { i ->
                val f = features.optJSONObject(i) ?: return@mapNotNull null
                val p = f.optJSONObject("properties") ?: JSONObject()
                val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
                if (coords.length() < 2) return@mapNotNull null
                val lonV = coords.optDouble(0, 0.0)
                val latV = coords.optDouble(1, 0.0)
                if (latV == 0.0 && lonV == 0.0) return@mapNotNull null
                val name = p.optString("name", "")
                val street = p.optString("street", "")
                val city = p.optString("city", "")
                val state = p.optString("state", "")
                val display = listOf(name, street, city, state)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(", ")
                PlaceResult(
                    displayName = display.ifBlank { "${"%.4f".format(latV)}, ${"%.4f".format(lonV)}" },
                    lat = latV,
                    lon = lonV
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("Photon", "search: ${e.message}")
            emptyList()
        }
    }

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
            "lavado" to "lavado de autos",
            "cerveza" to "cervecería",
            "bar" to "bar",
            "cine" to "cine",
            "playa" to "playa",
            "parque" to "parque",
            "tienda" to "tienda",
            "oxxo" to "OXXO",
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