package com.chronosbvrx.ibizacompanion.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class BuiltRoute(
    val destinationName: String,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val encodedPolyline: String,
    val steps: List<JSONObject>,
    /** Retraso total por tráfico en segundos (0 si no hay dato). */
    val trafficDelaySeconds: Int = 0,
    /** Tramos lentos para colorear la ruta en el estéreo. */
    val trafficSections: List<TomTomRouting.TrafficSection> = emptyList()
)

/**
 * Builds a stereo-ready route (polyline + maneuver steps).
 *
 * Si hay clave de TomTom, la ruta se calcula con **tráfico real** (ETA y tramos
 * lentos). Si el proveedor falla, cae a OSRM para no dejar al conductor sin
 * ruta nunca.
 */
object RouteBuilder {
    fun build(
        context: android.content.Context,
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        destinationName: String
    ): BuiltRoute? {
        val trafficKey = TomTomRouting.keyFor(context)
        if (trafficKey.isNotBlank()) {
            val traffic = try {
                TomTomRouting.route(trafficKey, fromLat, fromLon, toLat, toLon)
            } catch (e: Exception) {
                Log.w("RouteBuilder", "tomtom: ${e.message}")
                null
            }
            if (traffic != null && traffic.encodedPolyline.isNotBlank()) {
                Log.i(
                    "RouteBuilder",
                    "tomtom ${traffic.distanceMeters} m, ${traffic.durationSeconds} s, " +
                        "retraso ${traffic.trafficDelaySeconds} s"
                )
                return BuiltRoute(
                    destinationName = destinationName,
                    distanceMeters = traffic.distanceMeters,
                    durationSeconds = traffic.durationSeconds,
                    encodedPolyline = traffic.encodedPolyline,
                    steps = traffic.steps,
                    trafficDelaySeconds = traffic.trafficDelaySeconds,
                    trafficSections = traffic.trafficSections
                )
            }
            Log.w("RouteBuilder", "TomTom sin ruta; se usa OSRM")
        }
        return buildOsrm(fromLat, fromLon, toLat, toLon, destinationName)
    }

    private fun buildOsrm(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        destinationName: String
    ): BuiltRoute? {
        val json = try {
            com.chronosbvrx.ibizacompanion.OsrmClient.route(fromLat, fromLon, toLat, toLon)
        } catch (e: Exception) {
            Log.w("RouteBuilder", "osrm: ${e.message}")
            null
        }
        val r = json?.optJSONArray("routes")?.optJSONObject(0) ?: return null
        val polyline = r.optString("geometry", "")
        if (polyline.isBlank()) return null
        val distance = r.optInt("distance", 0)
        val duration = r.optInt("duration", 0)
        val steps = ArrayList<JSONObject>()
        val legSteps = r.optJSONArray("legs")?.optJSONObject(0)?.optJSONArray("steps")
        if (legSteps != null) {
            for (i in 0 until legSteps.length()) {
                val s = legSteps.optJSONObject(i) ?: continue
                val man = s.optJSONObject("maneuver")
                val loc = man?.optJSONArray("location")
                val lat = loc?.optDouble(1, 0.0) ?: 0.0
                val lon = loc?.optDouble(0, 0.0) ?: 0.0
                val name = s.optString("name", "")
                val ref = s.optString("ref", "")
                val type = man?.optString("type", "") ?: ""
                val modifier = man?.optString("modifier", "") ?: ""
                val instruction = humanInstruction(type, modifier, name)
                steps.add(JSONObject().apply {
                    put("lat", lat)
                    put("lon", lon)
                    put("instruction", instruction)
                    put("maneuver", type)
                    put("modifier", modifier)
                    put("distanceMeters", s.optInt("distance", 0))
                    put("name", name)
                    put("ref", ref)
                })
            }
        }
        return BuiltRoute(destinationName, distance, duration, polyline, steps)
    }

    /** Spanish instruction honoring one-ways/ramps/roundabouts from OSRM. */
    fun humanInstruction(type: String, modifier: String, street: String): String {
        val onStreet = street.takeIf { it.isNotBlank() }?.let { " en $it" } ?: ""
        return when (type) {
            "depart" -> "Sal hacia$onStreet"
            "arrive" -> "Llegaste a tu destino"
            "turn" -> when (modifier) {
                "left" -> "Gira a la izquierda$onStreet"
                "right" -> "Gira a la derecha$onStreet"
                "sharp left" -> "Gira muy a la izquierda$onStreet"
                "sharp right" -> "Gira muy a la derecha$onStreet"
                "slight left" -> "Mantente a la izquierda$onStreet"
                "slight right" -> "Mantente a la derecha$onStreet"
                "uturn" -> "Da vuelta en U$onStreet"
                "straight" -> "Sigue derecho$onStreet"
                else -> "Gira$onStreet"
            }
            "new name" -> "Continúa$onStreet"
            "continue" -> "Sigue derecho$onStreet"
            "merge" -> "Incorpórate$onStreet"
            "on ramp" -> "Toma la rampa$onStreet"
            "off ramp" -> "Toma la salida$onStreet"
            "fork" -> if (modifier.contains("left")) "Toma la izquierda$onStreet"
            else "Toma la derecha$onStreet"
            "end of road" -> "Al final, gira$onStreet"
            "roundabout", "rotary" -> "En la glorieta, sigue$onStreet"
            "roundabout turn" -> "En la glorieta, gira$onStreet"
            "exit roundabout", "exit rotary" -> "Sal de la glorieta$onStreet"
            "notification" -> "Continúa$onStreet"
            else -> if (street.isNotBlank()) "Sigue por $street" else "Continúa"
        }
    }

    @Suppress("unused")
    fun stepsArray(steps: List<JSONObject>): JSONArray {
        val arr = JSONArray()
        steps.forEach { arr.put(it) }
        return arr
    }
}
