package com.chronosbvrx.ibizadash.nav

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import org.json.JSONObject
import java.net.URL

/**
 * Positions the car ON the road instead of the raw GPS dot.
 *
 * Priority (cheap → expensive, nothing wasted on a Cortex-A7):
 *  1. Active route projection (free, computed by [RouteNavigator]).
 *  2. OSRM `nearest` snap when online and moving (throttled, one call/3 s).
 *  3. Smoothed raw fix (exponential filter + jump rejection).
 */
object RoadSnapper {
    private const val TAG = "RoadSnap"
    private const val MIN_INTERVAL_MS = 3000L
    private const val MIN_MOVE_M = 12.0
    private const val JUMP_REJECT_M = 90.0

    @Volatile private var shownLat = Double.NaN
    @Volatile private var shownLon = Double.NaN
    @Volatile private var lastNetMs = 0L
    @Volatile private var lastNetLat = 0.0
    @Volatile private var lastNetLon = 0.0
    @Volatile private var netInFlight = false

    fun reset() {
        shownLat = Double.NaN
        shownLon = Double.NaN
    }

    /**
     * Synchronous local snap: route projection, else smoothed raw.
     * Returns Triple(lat, lon, snapped).
     */
    fun snapLocal(lat: Double, lon: Double, speedKmh: Float, accuracyM: Float): Triple<Double, Double, Boolean> {
        RouteNavigator.projectOnRoute(lat, lon)?.let { (plat, plon) ->
            shownLat = plat
            shownLon = plon
            return Triple(plat, plon, true)
        }
        // Jump rejection: ignore wild fixes while moving.
        if (!shownLat.isNaN() && speedKmh > 8 && accuracyM > 0 && accuracyM < 200) {
            val jump = geoDist(shownLat, shownLon, lat, lon)
            if (jump > JUMP_REJECT_M) {
                return Triple(shownLat, shownLon, false)
            }
        }
        if (shownLat.isNaN()) {
            shownLat = lat
            shownLon = lon
            return Triple(lat, lon, false)
        }
        // Exponential smoothing: steadier at low speed, responsive when fast.
        val alpha = when {
            speedKmh < 5 -> 0.25
            speedKmh < 40 -> 0.45
            else -> 0.65
        }
        shownLat += (lat - shownLat) * alpha
        shownLon += (lon - shownLon) * alpha
        return Triple(shownLat, shownLon, false)
    }

    /** True when a network snap is worth attempting right now. */
    fun wantNetworkSnap(speedKmh: Float, accuracyM: Float): Boolean {
        if (speedKmh < 8) return false
        if (netInFlight) return false
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastNetMs < MIN_INTERVAL_MS) return false
        if (shownLat.isNaN()) return false
        if (geoDist(shownLat, shownLon, lastNetLat, lastNetLon) < MIN_MOVE_M &&
            now - lastNetMs < 15_000L
        ) {
            return false
        }
        return true
    }

    fun hasNetwork(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val info = cm?.activeNetworkInfo
            info?.isConnected == true
        } catch (_: Exception) {
            false
        }
    }

    /** OSRM nearest-road snap on a worker thread. Null on any failure. */
    fun snapNetwork(
        context: Context,
        lat: Double,
        lon: Double,
        cb: (Double?, Double?) -> Unit
    ) {
        netInFlight = true
        lastNetMs = android.os.SystemClock.elapsedRealtime()
        lastNetLat = lat
        lastNetLon = lon
        Thread {
            var rLat: Double? = null
            var rLon: Double? = null
            try {
                val url = "https://router.project-osrm.org/nearest/v1/driving/$lon,$lat"
                val conn = URL(url).openConnection() as java.net.HttpURLConnection
                conn.setRequestProperty("User-Agent", "IbizaDash/1.0")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                if (conn.responseCode in 200..299) {
                    val text = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    val wp = JSONObject(text).optJSONArray("waypoints")?.optJSONObject(0)
                    val loc = wp?.optJSONArray("location")
                    if (loc != null && loc.length() >= 2) {
                        rLon = loc.optDouble(0, Double.NaN)
                        rLat = loc.optDouble(1, Double.NaN)
                        if (rLat == null || rLat!!.isNaN()) {
                            rLat = null
                            rLon = null
                        } else if (geoDist(lat, lon, rLat!!, rLon!!) > 80) {
                            // Too far: don't yank the dot across blocks.
                            rLat = null
                            rLon = null
                        }
                    }
                } else {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "nearest: ${e.message}")
            } finally {
                netInFlight = false
            }
            if (rLat != null && rLon != null) {
                shownLat = rLat!!
                shownLon = rLon!!
            }
            cb(rLat, rLon)
        }.apply {
            isDaemon = true
            start()
        }
    }
}
