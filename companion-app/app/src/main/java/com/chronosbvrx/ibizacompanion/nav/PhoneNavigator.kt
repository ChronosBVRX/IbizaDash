package com.chronosbvrx.ibizacompanion.nav

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Navegador que corre en el teléfono (Fase 1).
 *
 * Es el mismo algoritmo que usaba el estéreo (proyección sobre la polilínea,
 * próximo giro, restante/ETA, salida de ruta), pero alimentado por el GPS del
 * teléfono. Publica [PhoneNavSnapshot] para que el estéreo solo pinte.
 */
class PhoneNavigator(
    context: Context,
    private val route: PhoneRoute,
    private val onState: (PhoneNavSnapshot) -> Unit,
    private val onOffRoute: (PhoneNavSnapshot) -> Unit
) {
    companion object {
        private const val TAG = "PhoneNav"
        private const val OFF_ROUTE_M = 60.0
        private const val OFF_ROUTE_FIXES = 3
        private const val REROUTE_COOLDOWN_MS = 30_000L
        private const val ARRIVE_M = 45.0
    }

    private data class Step(
        val lat: Double,
        val lon: Double,
        val instruction: String,
        val maneuver: String,
        val modifier: String
    )

    private val appContext = context.applicationContext
    private var locationManager: LocationManager? = null
    private var listener: LocationListener? = null

    @Volatile
    private var running = false

    private var segLat1 = DoubleArray(0)
    private var segLon1 = DoubleArray(0)
    private var segLat2 = DoubleArray(0)
    private var segLon2 = DoubleArray(0)
    private var segCum = DoubleArray(0)
    private var totalM = 0.0
    private var cursor = 0
    private var offRouteStreak = 0
    private var lastRerouteMs = 0L
    private var arrivedSent = false
    private var lastPublishMs = 0L
    private var stepAlong = DoubleArray(0)
    private var nextStepIdx = 0
    private var stepProfileOk = false
    private var lastProgress = 0f

    private val steps: List<Step> = route.steps.mapNotNull { s ->
        val lat = s.optDouble("lat", 0.0)
        val lon = s.optDouble("lon", 0.0)
        if (lat == 0.0 && lon == 0.0) return@mapNotNull null
        Step(
            lat = lat,
            lon = lon,
            instruction = s.optString("instruction", ""),
            maneuver = s.optString("maneuver", ""),
            modifier = s.optString("modifier", "")
        )
    }

    fun start(): Boolean {
        val pts = decodePolyline(route.encodedPolyline)
        if (pts.size < 2) {
            Log.w(TAG, "ruta sin geometría")
            return false
        }
        buildSegments(pts)
        buildStepProfile()
        cursor = 0
        nextStepIdx = 0
        offRouteStreak = 0
        arrivedSent = false
        lastRerouteMs = 0L
        lastProgress = 0f
        if (running) stop()

        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        locationManager = lm
        running = true
        val li = object : LocationListener {
            override fun onLocationChanged(location: Location) = onFix(location)
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        listener = li
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            Log.w(TAG, "sin proveedor de ubicación")
            running = false
            return false
        }
        try {
            lm.requestLocationUpdates(provider, 1000L, 2f, li)
            val last = lm.getLastKnownLocation(provider)
            if (last != null) {
                val age = SystemClock.elapsedRealtime() - last.elapsedRealtimeNanos / 1_000_000L
                if (age < 60_000L) onFix(last)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "sin permiso de ubicación")
            running = false
            return false
        }
        Log.i(TAG, "guía en teléfono: ${pts.size} puntos, ${totalM.toInt()} m")
        return true
    }

    fun stop() {
        running = false
        try {
            listener?.let { locationManager?.removeUpdates(it) }
        } catch (_: Exception) {
        }
        listener = null
        locationManager = null
        cursor = 0
        offRouteStreak = 0
        segLat1 = DoubleArray(0)
    }

    fun isRunning(): Boolean = running

    private fun buildSegments(pts: List<Pair<Double, Double>>) {
        val n = pts.size - 1
        segLat1 = DoubleArray(n)
        segLon1 = DoubleArray(n)
        segLat2 = DoubleArray(n)
        segLon2 = DoubleArray(n)
        segCum = DoubleArray(n + 1)
        var acc = 0.0
        for (i in 0 until n) {
            val a = pts[i]
            val b = pts[i + 1]
            segLat1[i] = a.first
            segLon1[i] = a.second
            segLat2[i] = b.first
            segLon2[i] = b.second
            segCum[i] = acc
            acc += geoDist(a.first, a.second, b.first, b.second)
        }
        segCum[n] = acc
        totalM = acc
    }

    private fun buildStepProfile() {
        stepAlong = DoubleArray(steps.size)
        stepProfileOk = false
        if (steps.isEmpty() || segLat1.isEmpty()) return
        var lastValid = 0.0
        var scanFrom = 0
        var matched = 0
        for (k in steps.indices) {
            val s = steps[k]
            var bestIdx = -1
            var bestT = 0.0
            var bestDist = Double.MAX_VALUE
            val n = segLat1.size
            for (i in scanFrom until n) {
                val (t, _, lateral) = projectOnSegment(
                    s.lat, s.lon,
                    segLat1[i], segLon1[i], segLat2[i], segLon2[i]
                )
                if (lateral < bestDist) {
                    bestDist = lateral
                    bestIdx = i
                    bestT = t
                }
                if (bestIdx >= 0 && i - bestIdx > 150) break
            }
            if (bestIdx >= 0 && bestDist < 150.0) {
                val along = segCum[bestIdx] + bestT *
                    (segCum[bestIdx + 1] - segCum[bestIdx])
                lastValid = max(lastValid, along)
                stepAlong[k] = lastValid
                matched++
                scanFrom = max(0, bestIdx - 4)
            } else {
                stepAlong[k] = lastValid
            }
        }
        stepProfileOk = matched >= 2 && stepAlong.last() > 0.0
    }

    private fun pointAt(idx: Int, t: Double): Pair<Double, Double> {
        val i = idx.coerceIn(0, segLat1.size - 1)
        val tt = t.coerceIn(0.0, 1.0)
        return Pair(
            segLat1[i] + (segLat2[i] - segLat1[i]) * tt,
            segLon1[i] + (segLon2[i] - segLon1[i]) * tt
        )
    }

    private fun onFix(loc: Location) {
        if (!running || segLat1.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastPublishMs < 500L) return
        lastPublishMs = now

        var bestIdx = -1
        var bestT = 0.0
        var bestDist = Double.MAX_VALUE
        val lo = max(0, cursor - 4)
        val hi = min(segLat1.size - 1, cursor + 12)
        for (i in lo..hi) {
            val (t, _, lateral) = projectOnSegment(
                loc.latitude, loc.longitude,
                segLat1[i], segLon1[i], segLat2[i], segLon2[i]
            )
            if (lateral < bestDist) {
                bestDist = lateral
                bestIdx = i
                bestT = t
            }
        }
        if (bestIdx < 0 || bestDist > OFF_ROUTE_M) {
            for (i in segLat1.indices) {
                if (i in lo..hi) continue
                val (t, _, lateral) = projectOnSegment(
                    loc.latitude, loc.longitude,
                    segLat1[i], segLon1[i], segLat2[i], segLon2[i]
                )
                if (lateral < bestDist) {
                    bestDist = lateral
                    bestIdx = i
                    bestT = t
                }
            }
        }

        if (bestIdx < 0 || bestDist > OFF_ROUTE_M) {
            offRouteStreak++
            if (offRouteStreak >= OFF_ROUTE_FIXES) {
                publishOffRoute(loc, now)
            }
            return
        }
        offRouteStreak = 0
        cursor = bestIdx
        val traveled = segCum[bestIdx] + bestT * (segCum[bestIdx + 1] - segCum[bestIdx])
        val remaining = (totalM - traveled).coerceAtLeast(0.0)
        val snapped = pointAt(bestIdx, bestT)

        val endLat = route.destLat
        val endLon = route.destLon
        val distToEnd = geoDist(loc.latitude, loc.longitude, endLat, endLon)

        if (!arrivedSent && (remaining < ARRIVE_M || distToEnd < ARRIVE_M)) {
            arrivedSent = true
            val snapshot = PhoneNavSnapshot(
                icon = "ARRIVE",
                distText = "0 m",
                actionText = "Has llegado a ${route.destinationName}",
                thenText = null,
                remainingText = "viaje terminado",
                etaText = "0 min",
                progress = 1f,
                offRoute = false,
                arrived = true,
                destName = route.destinationName,
                lat = loc.latitude,
                lon = loc.longitude,
                snappedLat = loc.latitude,
                snappedLon = loc.longitude,
                bearing = 0f,
                speedKmh = 0f,
                remainingM = 0
            )
            onState(snapshot)
            stop()
            return
        }

        var nextIdx = -1
        var nextDist = 0.0
        if (stepProfileOk && stepAlong.isNotEmpty()) {
            while (nextStepIdx < stepAlong.size - 1 &&
                stepAlong[nextStepIdx] + 6.0 < traveled
            ) {
                nextStepIdx++
            }
            nextIdx = nextStepIdx
            nextDist = (stepAlong[nextIdx] - traveled).coerceAtLeast(0.0)
        } else {
            for (i in steps.indices) {
                val s = steps[i]
                val d = geoDist(loc.latitude, loc.longitude, s.lat, s.lon)
                if (d < 1500) {
                    nextIdx = i
                    nextDist = d
                    break
                }
            }
        }

        val speedKmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
        val etaMin = run {
            val v = if (speedKmh > 12) speedKmh / 3.6 else 12.5
            max(1, (remaining / v / 60.0).toInt())
        }
        val progress = if (totalM > 0) (traveled / totalM).toFloat().coerceIn(0f, 1f) else 0f
        lastProgress = progress

        val snapshot = if (nextIdx < 0 || nextIdx >= steps.size) {
            PhoneNavSnapshot(
                icon = "STRAIGHT",
                distText = fmtDist(distToEnd.toInt()),
                actionText = route.destinationName,
                thenText = null,
                remainingText = fmtRemaining(remaining.toInt(), etaMin),
                etaText = "$etaMin min",
                progress = progress,
                offRoute = false,
                arrived = false,
                destName = route.destinationName,
                lat = loc.latitude,
                lon = loc.longitude,
                snappedLat = snapped.first,
                snappedLon = snapped.second,
                bearing = if (loc.hasBearing()) loc.bearing else 0f,
                speedKmh = speedKmh,
                remainingM = remaining.toInt()
            )
        } else {
            val step = steps[nextIdx]
            var thenText: String? = null
            for (j in nextIdx + 1 until steps.size) {
                val s2 = steps[j]
                if (s2.instruction.isNotBlank() && s2.instruction != step.instruction) {
                    thenText = "luego ${s2.instruction}"
                    break
                }
            }
            PhoneNavSnapshot(
                icon = maneuverIconFor(step.maneuver, step.modifier),
                distText = fmtDist(nextDist.toInt()),
                actionText = step.instruction.ifBlank { "Continúa" },
                thenText = thenText,
                remainingText = fmtRemaining(remaining.toInt(), etaMin),
                etaText = "$etaMin min",
                progress = progress,
                offRoute = false,
                arrived = false,
                destName = route.destinationName,
                lat = loc.latitude,
                lon = loc.longitude,
                snappedLat = snapped.first,
                snappedLon = snapped.second,
                bearing = if (loc.hasBearing()) loc.bearing else 0f,
                speedKmh = speedKmh,
                remainingM = remaining.toInt()
            )
        }
        onState(snapshot)
    }

    private fun publishOffRoute(loc: Location, now: Long) {
        val snapshot = PhoneNavSnapshot(
            icon = "STRAIGHT",
            distText = "…",
            actionText = "Recalculando ruta…",
            thenText = null,
            remainingText = route.destinationName,
            etaText = "--",
            progress = lastProgress,
            offRoute = true,
            arrived = false,
            destName = route.destinationName,
            lat = loc.latitude,
            lon = loc.longitude,
            snappedLat = loc.latitude,
            snappedLon = loc.longitude,
            bearing = if (loc.hasBearing()) loc.bearing else 0f,
            speedKmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f,
            remainingM = 0
        )
        onState(snapshot)
        if (now - lastRerouteMs > REROUTE_COOLDOWN_MS) {
            lastRerouteMs = now
            onOffRoute(snapshot)
        }
    }

    // ------------------------------------------------------------------
    // Utilidades (mismas fórmulas que el estéreo)
    // ------------------------------------------------------------------

    private fun maneuverIconFor(type: String, modifier: String): String {
        val t = type.lowercase()
        val m = modifier.lowercase()
        if (t == "arrive") return "ARRIVE"
        if (t == "depart") return "DEPART"
        if (t == "roundabout" || t == "rotary" || t == "roundabout turn" ||
            t == "exit roundabout" || t == "exit rotary"
        ) {
            return "ROUNDABOUT"
        }
        if (m == "uturn") return "UTURN"
        if (m.contains("left")) return "LEFT"
        if (m.contains("right")) return "RIGHT"
        if (t == "end of road") return "LEFT"
        return "STRAIGHT"
    }

    private fun fmtDist(meters: Int): String {
        val m = meters.coerceAtLeast(0)
        return if (m < 1000) "$m m" else {
            val km = m / 1000f
            if (km < 10) "%.1f km".format(km) else "%d km".format(kotlin.math.round(km).toInt())
        }
    }

    private fun fmtRemaining(meters: Int, etaMin: Int): String =
        "quedan ${fmtDist(meters)} · $etaMin min"

    private fun geoDist(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        return 2 * r * asin(sqrt(a))
    }

    private fun projectOnSegment(
        lat: Double, lon: Double,
        aLat: Double, aLon: Double,
        bLat: Double, bLon: Double
    ): Triple<Double, Double, Double> {
        val kx = 111320.0 * cos(Math.toRadians((aLat + bLat) / 2.0))
        val ky = 110540.0
        val bx = (bLon - aLon) * kx
        val by = (bLat - aLat) * ky
        val px = (lon - aLon) * kx
        val py = (lat - aLat) * ky
        val len2 = bx * bx + by * by
        if (len2 < 1.0) return Triple(0.0, 0.0, sqrt(px * px + py * py))
        var t = (px * bx + py * by) / len2
        t = t.coerceIn(0.0, 1.0)
        val segLen = sqrt(len2)
        val lateral = abs(px * by - py * bx) / segLen
        return Triple(t, t * segLen, lateral)
    }

    private fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
        if (encoded.isBlank()) return emptyList()
        val out = ArrayList<Pair<Double, Double>>()
        var index = 0
        var lat = 0
        var lon = 0
        while (index < encoded.length) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dLat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLon = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lon += dLon
            out.add(lat / 1e5 to lon / 1e5)
        }
        return out
    }
}
