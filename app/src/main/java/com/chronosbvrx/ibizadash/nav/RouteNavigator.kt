package com.chronosbvrx.ibizadash.nav

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import org.osmdroid.util.GeoPoint
import kotlin.math.*

/**
 * Waze-style autonomous navigator for the stereo.
 *
 * Uses the stereo's own GPS snapped to the route polyline (projection onto
 * segments, cheap on a Cortex-A7: only a window of segments is scanned).
 * Publishes [NavState] with maneuver countdown, remaining/ETA, progress and
 * off-route detection. When lost, asks the phone to recalculate (REROUTE_REQUEST).
 */
object RouteNavigator {
    private const val TAG = "RouteNav"
    private const val OFF_ROUTE_M = 60.0
    private const val OFF_ROUTE_FIXES = 3
    private const val REROUTE_COOLDOWN_MS = 30_000L
    private const val ARRIVE_M = 45.0

    private var locationManager: LocationManager? = null
    private var listener: LocationListener? = null
    @Volatile private var running = false

    // Route geometry cache (built at start, no per-fix allocations of note).
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
    /** Distance along the route where each maneuver happens (monotonic). */
    private var stepAlong = DoubleArray(0)
    private var nextStepIdx = 0
    private var stepProfileOk = false

    fun start(context: Context) {
        val route = NavRouteRepository.current ?: return
        val pts = try {
            com.chronosbvrx.ibizadash.map.MapHelper.decodePolyline(route.encodedPolyline)
        } catch (_: Exception) {
            emptyList()
        }
        if (pts.size < 2) {
            Log.w(TAG, "ruta sin geometría, no se puede guiar")
            stop()
            return
        }
        buildSegments(pts)
        buildStepProfile(route)
        cursor = 0
        nextStepIdx = 0
        offRouteStreak = 0
        arrivedSent = false
        lastRerouteMs = 0L

        if (running) stop()
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        locationManager = lm
        running = true
        val li = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onFix(location)
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        listener = li
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, li)
        } catch (_: SecurityException) {
            running = false
        }
        Log.i(TAG, "guía iniciada: ${pts.size} puntos, ${totalM.toInt()} m")
    }

    private fun buildSegments(pts: List<GeoPoint>) {
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
            segLat1[i] = a.latitude
            segLon1[i] = a.longitude
            segLat2[i] = b.latitude
            segLon2[i] = b.longitude
            segCum[i] = acc
            acc += geoDist(a.latitude, a.longitude, b.latitude, b.longitude)
        }
        segCum[n] = acc
        totalM = acc
    }

    /**
     * Maps each maneuver to its distance along the route. Steps arrive in
     * order, so we walk forward with a small window; this lets the banner pick
     * the *next* turn instead of a passed one that is still nearby.
     */
    private fun buildStepProfile(route: NavRoute) {
        val steps = route.steps
        stepAlong = DoubleArray(steps.size)
        stepProfileOk = false
        if (steps.isEmpty() || segLat1.isEmpty()) return
        var lastValid = 0.0
        var scanFrom = 0
        var matched = 0
        for (k in steps.indices) {
            val s = steps[k]
            if (s.lat == 0.0 && s.lon == 0.0) {
                stepAlong[k] = lastValid
                continue
            }
            var bestIdx = -1
            var bestT = 0.0
            var bestDist = Double.MAX_VALUE
            val n = segLat1.size
            for (i in scanFrom until n) {
                val (t, _, lat) = projectOnSegment(
                    s.lat, s.lon,
                    segLat1[i], segLon1[i], segLat2[i], segLon2[i]
                )
                if (lat < bestDist) {
                    bestDist = lat
                    bestIdx = i
                    bestT = t
                }
                // The first local minimum is the right one: steps are ordered.
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
        Log.i(TAG, "perfil de pasos: $matched/${steps.size} ok=$stepProfileOk")
    }

    /** Interpolated road point on segment [idx] at fraction t. */
    private fun pointAt(idx: Int, t: Double): Pair<Double, Double> {
        val i = idx.coerceIn(0, segLat1.size - 1)
        val tt = t.coerceIn(0.0, 1.0)
        return Pair(
            segLat1[i] + (segLat2[i] - segLat1[i]) * tt,
            segLon1[i] + (segLon2[i] - segLon1[i]) * tt
        )
    }

    /**
     * Public map-matching: snaps any fix onto the active route, or null when
     * there is no route / the point is too far from it.
     */
    fun projectOnRoute(lat: Double, lon: Double): Pair<Double, Double>? {
        if (!running || segLat1.isEmpty()) return null
        var bestIdx = -1
        var bestT = 0.0
        var bestDist = Double.MAX_VALUE
        for (i in segLat1.indices) {
            val (t, _, lateral) = projectOnSegment(
                lat, lon, segLat1[i], segLon1[i], segLat2[i], segLon2[i]
            )
            if (lateral < bestDist) {
                bestDist = lateral
                bestIdx = i
                bestT = t
            }
        }
        if (bestIdx < 0 || bestDist > OFF_ROUTE_M) return null
        return pointAt(bestIdx, bestT)
    }

    private fun onFix(loc: Location) {
        val route = NavRouteRepository.current ?: return
        if (!running || segLat1.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        // Throttle UI publishes to ~2 Hz; math below is cheap anyway.
        if (now - lastPublishMs < 500L) return
        lastPublishMs = now

        // Search window around cursor, plus full scan if lost.
        var bestIdx = -1
        var bestT = 0.0
        var bestDist = Double.MAX_VALUE
        var bestLat = 0.0
        val lo = max(0, cursor - 4)
        val hi = min(segLat1.size - 1, cursor + 12)
        for (i in lo..hi) {
            val (t, _, lat) = projectOnSegment(
                loc.latitude, loc.longitude,
                segLat1[i], segLon1[i], segLat2[i], segLon2[i]
            )
            if (lat < bestDist) {
                bestDist = lat
                bestIdx = i
                bestT = t
            }
        }
        if (bestIdx < 0 || bestDist > OFF_ROUTE_M) {
            // Full scan before declaring off-route.
            for (i in segLat1.indices) {
                if (i in lo..hi) continue
                val (t, _, lat) = projectOnSegment(
                    loc.latitude, loc.longitude,
                    segLat1[i], segLon1[i], segLat2[i], segLon2[i]
                )
                if (lat < bestDist) {
                    bestDist = lat
                    bestIdx = i
                    bestT = t
                }
            }
        }

        if (bestIdx < 0 || bestDist > OFF_ROUTE_M) {
            offRouteStreak++
            if (offRouteStreak >= OFF_ROUTE_FIXES) {
                publishOffRoute(route, loc, now)
            }
            return
        }
        offRouteStreak = 0
        cursor = bestIdx
        val traveled = segCum[bestIdx] + bestT *
            (segCum[bestIdx + 1] - segCum[bestIdx])
        val remaining = (totalM - traveled).coerceAtLeast(0.0)
        // Map-matched position ON the road (not the raw GPS dot).
        val snapped = pointAt(bestIdx, bestT)

        // Destination = last maneuver point (or polyline end).
        val endLat: Double
        val endLon: Double
        val lastStep = route.steps.lastOrNull()
        if (lastStep != null && (lastStep.lat != 0.0 || lastStep.lon != 0.0)) {
            endLat = lastStep.lat
            endLon = lastStep.lon
        } else {
            endLat = segLat2.last()
            endLon = segLon2.last()
        }
        val distToEnd = geoDist(loc.latitude, loc.longitude, endLat, endLon)

        // Arrival.
        if (!arrivedSent && (remaining < ARRIVE_M || distToEnd < ARRIVE_M)) {
            arrivedSent = true
            publishArrival(route, loc)
            return
        }

        // Next maneuver: monotonic step cursor by distance along the route.
        // This skips turns already passed even if they are still nearby.
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
            // Fallback when the step points don't match the geometry.
            for (i in route.steps.indices) {
                val s = route.steps[i]
                if (s.lat == 0.0 && s.lon == 0.0) continue
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
            val v = if (speedKmh > 12) speedKmh / 3.6 else 12.5 // ~45 km/h urban default
            max(1, (remaining / v / 60.0).toInt())
        }
        val progress = if (totalM > 0) (traveled / totalM).toFloat().coerceIn(0f, 1f) else 0f

        if (nextIdx < 0) {
            // Between maneuvers: cruise state toward destination.
            val st = NavState(
                icon = ManeuverIcon.STRAIGHT,
                distText = fmtDist(distToEnd.toInt()),
                actionText = route.destinationName,
                thenText = null,
                remainingText = fmtRemaining(remaining.toInt(), etaMin),
                etaText = "$etaMin min",
                progress = progress,
                offRoute = false,
                arrived = false,
                destName = route.destinationName,
                lat = loc.latitude, lon = loc.longitude,
                snappedLat = snapped.first, snappedLon = snapped.second,
                bearing = if (loc.hasBearing()) loc.bearing else 0f,
                speedKmh = speedKmh
            )
            NavStateRepository.publish(st)
            keepLegacyPill(route, null, distToEnd.toInt(), remaining.toInt(), etaMin)
            return
        }

        val step = route.steps[nextIdx]
        // "Then" preview: the following step with a different instruction.
        var thenText: String? = null
        for (j in nextIdx + 1 until route.steps.size) {
            val s2 = route.steps[j]
            if (s2.instruction.isNotBlank() && s2.instruction != step.instruction) {
                thenText = "luego ${s2.instruction}"
                break
            }
        }
        val st = NavState(
            icon = maneuverIconFor(step.maneuver ?: "", step.modifier ?: ""),
            distText = fmtDist(nextDist.toInt()),
            actionText = step.instruction.ifBlank { "Continúa" },
            thenText = thenText,
            remainingText = fmtRemaining(remaining.toInt(), etaMin),
            etaText = "$etaMin min",
            progress = progress,
            offRoute = false,
            arrived = false,
            destName = route.destinationName,
            lat = loc.latitude, lon = loc.longitude,
            snappedLat = snapped.first, snappedLon = snapped.second,
            bearing = if (loc.hasBearing()) loc.bearing else 0f,
            speedKmh = speedKmh
        )
        NavStateRepository.publish(st)
        keepLegacyPill(route, step.instruction.ifBlank { "Continúa" }, nextDist.toInt(), remaining.toInt(), etaMin)
    }

    private fun publishOffRoute(route: NavRoute, loc: Location, now: Long) {
        val st = NavState(
            icon = ManeuverIcon.STRAIGHT,
            distText = "…",
            actionText = "Recalculando ruta…",
            thenText = null,
            remainingText = route.destinationName,
            etaText = "--",
            progress = NavStateRepository.current?.progress ?: 0f,
            offRoute = true,
            arrived = false,
            destName = route.destinationName,
            lat = loc.latitude, lon = loc.longitude,
            snappedLat = loc.latitude, snappedLon = loc.longitude,
            bearing = if (loc.hasBearing()) loc.bearing else 0f,
            speedKmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
        )
        NavStateRepository.publish(st)
        NavRepository.publish("Recalculando ruta…", "Te saliste del camino", null)
        if (now - lastRerouteMs > REROUTE_COOLDOWN_MS) {
            lastRerouteMs = now
            // Destination = polyline end.
            val destLat = segLat2.last()
            val destLon = segLon2.last()
            NavLink.sendAsync(
                org.json.JSONObject()
                    .put("type", "REROUTE_REQUEST")
                    .put("lat", loc.latitude)
                    .put("lon", loc.longitude)
                    .put("destLat", destLat)
                    .put("destLon", destLon)
                    .put("destName", route.destinationName)
            )
            Log.i(TAG, "REROUTE_REQUEST encolado")
        }
    }

    private fun publishArrival(route: NavRoute, loc: Location) {
        val st = NavState(
            icon = ManeuverIcon.ARRIVE,
            distText = "0 m",
            actionText = "Has llegado a ${route.destinationName}",
            thenText = null,
            remainingText = "viaje terminado",
            etaText = "0 min",
            progress = 1f,
            offRoute = false,
            arrived = true,
            destName = route.destinationName,
            lat = loc.latitude, lon = loc.longitude,
            snappedLat = loc.latitude, snappedLon = loc.longitude,
            bearing = 0f,
            speedKmh = 0f
        )
        NavStateRepository.publish(st)
        NavRepository.publish("Has llegado", route.destinationName, null)
        Log.i(TAG, "llegada a destino")
    }

    private fun keepLegacyPill(
        route: NavRoute, instruction: String?, distM: Int, remainingM: Int, etaMin: Int
    ) {
        if (instruction == null) {
            NavRepository.publish(
                route.destinationName,
                "A ${fmtDist(remainingM)} · $etaMin min", null
            )
        } else {
            NavRepository.publish(
                "$instruction · en ${fmtDist(distM)}",
                "${fmtRemaining(remainingM, etaMin)} · ${route.destinationName}", null
            )
        }
    }

    fun stop() {
        running = false
        try {
            listener?.let { l -> locationManager?.removeUpdates(l) }
        } catch (_: Exception) {
        }
        listener = null
        locationManager = null
        cursor = 0
        offRouteStreak = 0
        segLat1 = DoubleArray(0)
    }

    fun isRunning(): Boolean = running
}
