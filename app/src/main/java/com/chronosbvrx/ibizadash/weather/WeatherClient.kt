package com.chronosbvrx.ibizadash.weather

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs

class WeatherClient(
    private val context: Context,
    private val onWeather: (String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var lastFetch = 0L
    private var lastLat = Double.NaN
    private var lastLon = Double.NaN

    fun update(latitude: Double, longitude: Double) {
        val now = System.currentTimeMillis()
        val samePlace = !lastLat.isNaN() &&
            abs(latitude - lastLat) < 0.01 &&
            abs(longitude - lastLon) < 0.01
        if (now - lastFetch < 10 * 60 * 1000L && samePlace) return

        lastFetch = now
        lastLat = latitude
        lastLon = longitude

        Thread {
            val text = fetch(latitude, longitude)
            handler.post { onWeather(text) }
        }.apply { isDaemon = true }.start()
    }

    private fun fetch(latitude: Double, longitude: Double): String {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$latitude" +
                    "&longitude=$longitude&current=temperature_2m,weather_code&timezone=auto"
            )
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            if (connection.responseCode !in 200..299) return "--°"

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(body).getJSONObject("current")
            val temperature = current.getDouble("temperature_2m")
            val code = current.getInt("weather_code")
            val icon = when (code) {
                0 -> "☀"
                1, 2 -> "⛅"
                3 -> "☁"
                45, 48 -> "🌫"
                in 51..67 -> "🌧"
                in 71..77 -> "❄"
                in 80..82 -> "🌦"
                in 95..99 -> "⛈"
                else -> "·"
            }
            String.format(Locale.getDefault(), "%s %.0f°", icon, temperature)
        } catch (e: Exception) {
            "--°"
        } finally {
            connection?.disconnect()
        }
    }
}
