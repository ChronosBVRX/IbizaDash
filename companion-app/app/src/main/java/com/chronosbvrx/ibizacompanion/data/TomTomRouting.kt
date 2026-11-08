package com.chronosbvrx.ibizacompanion.data

import android.util.Log
import com.chronosbvrx.ibizacompanion.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Enrutado con tráfico en tiempo real (TomTom Routing API v1).
 *
 * Se ejecuta en el teléfono, que es el único con Internet. Devuelve la ruta
 * con la ETA real, las maniobras ya en español y los tramos con retraso para
 * que el estéreo pinte la línea por colores.
 *
 * Si no hay clave o la API falla, el llamador cae a OSRM sin tráfico: el
 * navegador nunca se queda sin ruta por un problema del proveedor.
 */
object TomTomRouting {
    private const val TAG = "TomTomRouting"
    private const val BASE = "https://api.tomtom.com/routing/1/calculateRoute"

    data class TrafficSection(
        val fromFraction: Float,
        val toFraction: Float,
        val delaySeconds: Int,
        val speedKmh: Int
    )

    data class Result(
        val distanceMeters: Int,
        val durationSeconds: Int,
        val noTrafficSeconds: Int,
        val trafficDelaySeconds: Int,
        val encodedPolyline: String,
        val steps: List<JSONObject>,
        val trafficSections: List<TrafficSection>
    )

    fun isEnabled(context: android.content.Context): Boolean =
        keyFor(context).isNotBlank()

    /** Clave configurada en Ajustes o, si no, la de compilación local. */
    fun keyFor(context: android.content.Context): String {
        val fromPrefs = com.chronosbvrx.ibizacompanion.data.CompanionPrefs
            .trafficKey(context)
        return fromPrefs.ifBlank { BuildConfig.TOMTOM_API_KEY }
    }

    /**
     * Calcula la ruta. Debe llamarse desde un hilo de fondo.
     * Devuelve null si no hay clave o si la API no responde.
     */
    fun route(
        key: String,
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double
    ): Result? {
        if (key.isBlank()) return null
        val url = buildString {
            append(BASE)
            append('/').append(fmt(fromLat)).append(',').append(fmt(fromLon))
            append(':').append(fmt(toLat)).append(',').append(fmt(toLon))
            append("/json?key=").append(key)
            append("&traffic=true&travelMode=car&routeType=fastest")
            append("&instructionsType=text&language=es-ES")
            append("&sectionType=traffic&computeTravelTimeFor=all")
        }
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "IbizaCompanion")
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "HTTP $code")
                return null
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            parse(JSONObject(text))
        } catch (e: Exception) {
            Log.w(TAG, "route: ${e.message}")
            null
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun parse(root: JSONObject): Result? {
        val route = root.optJSONArray("routes")?.optJSONObject(0) ?: return null
        val summary = route.optJSONObject("summary") ?: return null
        val points = route.optJSONArray("legs")?.optJSONObject(0)?.optJSONArray("points")
            ?: return null
        if (points.length() < 2) return null

        val coords = ArrayList<Pair<Double, Double>>(points.length())
        for (i in 0 until points.length()) {
            val p = points.optJSONObject(i) ?: continue
            coords.add(p.optDouble("latitude", 0.0) to p.optDouble("longitude", 0.0))
        }
        if (coords.size < 2) return null

        val totalMeters = summary.optInt("lengthInMeters", 0).coerceAtLeast(1)
        val duration = summary.optInt("travelTimeInSeconds", 0)
        val noTraffic = summary.optInt("noTrafficTravelTimeInSeconds", duration)
        val delay = summary.optInt("trafficDelayInSeconds", 0)

        // Fracción de recorrido en cada punto, para ubicar los tramos de tráfico.
        val cum = DoubleArray(coords.size)
        var acc = 0.0
        for (i in 1 until coords.size) {
            acc += haversine(coords[i - 1], coords[i])
            cum[i] = acc
        }
        val totalGeo = if (acc > 1.0) acc else totalMeters.toDouble()
        fun fractionAt(index: Int): Float {
            val i = index.coerceIn(0, coords.size - 1)
            return (cum[i] / totalGeo).toFloat().coerceIn(0f, 1f)
        }

        val sections = ArrayList<TrafficSection>()
        val arr = route.optJSONArray("sections")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                if (!s.optString("sectionType", "").equals("TRAFFIC", true)) continue
                val delayS = s.optInt("delayInSeconds", 0)
                val speed = s.optInt("effectiveSpeedInKmh", 0)
                val from = fractionAt(s.optInt("startPointIndex", 0))
                val to = fractionAt(s.optInt("endPointIndex", 0))
                if (to > from) {
                    sections.add(TrafficSection(from, to, delayS, speed))
                }
            }
        }

        val steps = ArrayList<JSONObject>()
        val guidance = route.optJSONObject("guidance")?.optJSONArray("instructions")
        if (guidance != null) {
            for (i in 0 until guidance.length()) {
                val ins = guidance.optJSONObject(i) ?: continue
                val message = ins.optString("message", "").trim()
                if (message.isEmpty()) continue
                val point = ins.optJSONObject("point")
                val lat = point?.optDouble("latitude", 0.0) ?: 0.0
                val lon = point?.optDouble("longitude", 0.0) ?: 0.0
                val (type, modifier) = mapManeuver(ins.optString("maneuver", ""))
                steps.add(
                    JSONObject()
                        .put("lat", lat)
                        .put("lon", lon)
                        .put("instruction", cleanInstruction(message))
                        .put("maneuver", type)
                        .put("modifier", modifier)
                        .put("distanceMeters", ins.optInt("routeOffsetInMeters", 0))
                )
            }
        }

        return Result(
            distanceMeters = totalMeters,
            durationSeconds = duration,
            noTrafficSeconds = noTraffic,
            trafficDelaySeconds = maxOf(0, delay),
            encodedPolyline = encodePolyline(coords),
            steps = steps,
            trafficSections = sections
        )
    }

    /**
     * TomTom indica "Salga de…", "Gire a la derecha en…". Se deja tal cual es
     * natural en español, con la primera letra en mayúscula.
     */
    private fun cleanInstruction(text: String): String {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return clean
        return clean[0].uppercase() + clean.substring(1)
    }

    /** Traduce el tipo de maniobra de TomTom al que ya usa el estéreo. */
    private fun mapManeuver(raw: String): Pair<String, String> {
        return when (raw.uppercase()) {
            "ARRIVE", "ARRIVE_LEFT", "ARRIVE_RIGHT" -> "arrive" to ""
            "DEPART" -> "depart" to ""
            "TURN_LEFT" -> "turn" to "left"
            "TURN_RIGHT" -> "turn" to "right"
            "SHARP_LEFT" -> "turn" to "sharp left"
            "SHARP_RIGHT" -> "turn" to "sharp right"
            "SLIGHT_LEFT" -> "turn" to "slight left"
            "SLIGHT_RIGHT" -> "turn" to "slight right"
            "MAKE_UTURN", "UTURN" -> "turn" to "uturn"
            "KEEP_LEFT" -> "fork" to "left"
            "KEEP_RIGHT" -> "fork" to "right"
            "TAKE_RAMP_LEFT" -> "on ramp" to "left"
            "TAKE_RAMP_RIGHT" -> "on ramp" to "right"
            "TAKE_EXIT" -> "off ramp" to ""
            "ENTER_MOTORWAY", "ENTER_HIGHWAY" -> "merge" to ""
            "WAYPOINT" -> "continue" to ""
            "FINISH" -> "arrive" to ""
            else -> {
                if (raw.uppercase().startsWith("ROUNDABOUT")) "roundabout" to ""
                else "continue" to ""
            }
        }
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private fun fmt(v: Double): String = String.format(java.util.Locale.US, "%.6f", v)

    private fun haversine(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(b.first - a.first)
        val dLon = Math.toRadians(b.second - a.second)
        val m = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(a.first)) * Math.cos(Math.toRadians(b.first)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 2 * r * Math.asin(Math.sqrt(m))
    }

    /** Codifica puntos como polyline de Google (mismo formato que OSRM). */
    fun encodePolyline(points: List<Pair<Double, Double>>): String {
        val sb = StringBuilder()
        var prevLat = 0
        var prevLon = 0
        for ((lat, lon) in points) {
            val ilat = Math.round(lat * 1e5).toInt()
            val ilon = Math.round(lon * 1e5).toInt()
            encodeValue(ilat - prevLat, sb)
            encodeValue(ilon - prevLon, sb)
            prevLat = ilat
            prevLon = ilon
        }
        return sb.toString()
    }

    private fun encodeValue(value: Int, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else (value shl 1)
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1F)) + 63).toChar())
            v = v shr 5
        }
        sb.append((v + 63).toChar())
    }

    /** JSON que consume el estéreo. */
    fun sectionsJson(sections: List<TrafficSection>): JSONArray {
        val arr = JSONArray()
        sections.forEach { s ->
            arr.put(
                JSONObject()
                    .put("from", s.fromFraction.toDouble())
                    .put("to", s.toFraction.toDouble())
                    .put("delayS", s.delaySeconds)
                    .put("kmh", s.speedKmh)
            )
        }
        return arr
    }
}
