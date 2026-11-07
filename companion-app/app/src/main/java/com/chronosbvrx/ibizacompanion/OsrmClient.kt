package com.chronosbvrx.ibizacompanion

import android.util.Log
import org.json.JSONObject
import java.net.URL

object OsrmClient {
    fun route(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): JSONObject? {
        return try {
            val url = "https://router.project-osrm.org/route/v1/driving/" +
                "$fromLon,$fromLat;$toLon,$toLat?overview=full&geometries=polyline&steps=true"
            val conn = URL(url).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("User-Agent", "IbizaCompanion/1.0")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            conn.disconnect()
            Log.d("Osrm", "code=$code len=${text.length}")
            if (code !in 200..299) return null
            JSONObject(text)
        } catch (e: Exception) {
            Log.e("Osrm", "route failed: ${e.message}", e)
            null
        }
    }
}