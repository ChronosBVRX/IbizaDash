package com.chronosbvrx.ibizacompanion.weather

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import com.chronosbvrx.ibizacompanion.BridgeLink
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Clima para el estéreo, calculado en el teléfono.
 *
 * El estéreo no tiene Internet: Open-Meteo se consulta aquí y el resultado
 * viaja como `WEATHER` por Wi-Fi Direct. Se actualiza cada ~10 min o cuando
 * el coche se mueve más de 2 km.
 */
object WeatherSender {
    private const val TAG = "WeatherSender"
    private const val MIN_INTERVAL_MS = 10 * 60 * 1000L
    private const val MIN_MOVE_M = 2000.0
    private const val DEFAULT_LAT = 19.7060
    private const val DEFAULT_LON = -101.1950

    @Volatile
    private var fetching = false

    @Volatile
    private var lastAttemptMs = 0L

    @Volatile
    private var lastLat = Double.NaN

    @Volatile
    private var lastLon = Double.NaN

    @Volatile
    private var lastJson: JSONObject? = null

    /** Se llama cuando el enlace Wi-Fi Direct acaba de abrirse. */
    fun onLinkUp(context: Context) {
        lastJson?.let { cached ->
            if (BridgeLink.isWifiActive()) BridgeLink.send(cached)
        }
        tick(context)
    }

    /** Llamado periódicamente por MediaBridgeService. */
    fun tick(context: Context) {
        if (!BridgeLink.isWifiActive()) return
        val now = System.currentTimeMillis()
        val (lat, lon) = lastKnown(context)
        val moved = !lastLat.isNaN() && distanceMeters(lastLat, lastLon, lat, lon) >= MIN_MOVE_M
        if (!moved && now - lastAttemptMs < MIN_INTERVAL_MS) return
        if (!fetching) {
            fetching = true
            lastAttemptMs = now
            Thread {
                try {
                    val weather = fetch(lat, lon)
                    if (weather != null) {
                        lastLat = lat
                        lastLon = lon
                        lastJson = weather
                        if (BridgeLink.isWifiActive()) BridgeLink.send(weather)
                        Log.i(
                            TAG,
                            "clima ${weather.optDouble("tempC", 0.0)} code=${weather.optInt("code")}"
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "fetch: ${e.message}")
                } finally {
                    fetching = false
                }
            }.apply {
                name = "WeatherSender"
                isDaemon = true
                start()
            }
        }
    }

    private fun lastKnown(context: Context): Pair<Double, Double> {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            var best: Location? = null
            for (provider in listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            )) {
                val loc = try {
                    lm?.getLastKnownLocation(provider)
                } catch (_: SecurityException) {
                    null
                }
                if (loc != null && (best == null || loc.time > best.time)) best = loc
            }
            if (best != null) best.latitude to best.longitude
            else DEFAULT_LAT to DEFAULT_LON
        } catch (_: Exception) {
            DEFAULT_LAT to DEFAULT_LON
        }
    }

    private fun fetch(lat: Double, lon: Double): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
                    "&daily=temperature_2m_max,temperature_2m_min&timezone=auto"
            )
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", "IbizaCompanion")
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val current = root.getJSONObject("current")
            val temp = current.getDouble("temperature_2m")
            val code = current.getInt("weather_code")
            JSONObject()
                .put("type", "WEATHER")
                .put("tempC", temp)
                .put("code", code)
                .put("text", describe(code))
                .put("humidityPct", current.optInt("relative_humidity_2m", -1))
                .put("feelsLikeC", if (current.has("apparent_temperature")) current.optDouble("apparent_temperature", Double.NaN) else Double.NaN)
                .put("windKmh", if (current.has("wind_speed_10m")) current.optDouble("wind_speed_10m", Double.NaN) else Double.NaN)
                .put("maxC", root.optJSONObject("daily")?.optJSONArray("temperature_2m_max")?.optDouble(0, Double.NaN) ?: Double.NaN)
                .put("minC", root.optJSONObject("daily")?.optJSONArray("temperature_2m_min")?.optDouble(0, Double.NaN) ?: Double.NaN)
        } catch (e: Exception) {
            Log.w(TAG, "open-meteo: ${e.message}")
            null
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /** Traducción breve de los códigos WMO de Open-Meteo. */
    private fun describe(code: Int): String = when (code) {
        0 -> "Despejado"
        1 -> "Mayormente despejado"
        2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Niebla"
        51, 53, 55, 56, 57 -> "Llovizna"
        61, 63, 65, 66, 67 -> "Lluvia"
        71, 73, 75, 77 -> "Nieve"
        80, 81, 82 -> "Chubascos"
        85, 86 -> "Nieve intensa"
        95, 96, 99 -> "Tormenta"
        else -> "Clima"
    }

    private fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a))
    }
}
