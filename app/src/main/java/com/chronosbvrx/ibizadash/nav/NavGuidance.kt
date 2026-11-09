package com.chronosbvrx.ibizadash.nav

import com.chronosbvrx.ibizadash.R
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.*

/**
 * Waze-style guidance state: next maneuver with countdown, following street,
 * remaining distance/ETA, route progress and off-route/arrival flags.
 */
enum class ManeuverIcon(val resId: Int) {
    DEPART(R.drawable.ic_mav_depart),
    STRAIGHT(R.drawable.ic_mav_straight),
    LEFT(R.drawable.ic_mav_left),
    RIGHT(R.drawable.ic_mav_right),
    UTURN(R.drawable.ic_mav_uturn),
    ROUNDABOUT(R.drawable.ic_mav_roundabout),
    ARRIVE(R.drawable.ic_mav_finish)
}

data class NavState(
    val icon: ManeuverIcon,
    /** Big countdown text, e.g. "200 m" or "1.2 km". */
    val distText: String,
    /** Action + street, e.g. "Gira a la derecha · Av. Madero". */
    val actionText: String,
    /** Following maneuver preview, null when unknown. */
    val thenText: String?,
    val remainingText: String,
    val etaText: String,
    /** 0..1 fraction of the route already traveled. */
    val progress: Float,
    val offRoute: Boolean,
    val arrived: Boolean,
    val destName: String,
    /** Last GPS fix, for heading rotation. */
    val lat: Double,
    val lon: Double,
    /** Map-matched position on the route (road-snapped). Falls back to raw fix. */
    val snappedLat: Double,
    val snappedLon: Double,
    val bearing: Float,
    val speedKmh: Float
)

object NavStateRepository {
    private val listeners = CopyOnWriteArraySet<(NavState?) -> Unit>()
    var current: NavState? = null
        private set

    fun addListener(listener: (NavState?) -> Unit) {
        listeners.add(listener)
        listener(current)
    }

    fun removeListener(listener: (NavState?) -> Unit) {
        listeners.remove(listener)
    }

    fun publish(state: NavState?) {
        current = state
        listeners.forEach { it(state) }
    }

    fun clear() = publish(null)
}

/** Maps OSRM maneuver type+modifier to a dashboard icon. */
fun maneuverIconFor(type: String, modifier: String): ManeuverIcon {
    val t = type.lowercase()
    val m = modifier.lowercase()
    if (t == "arrive") return ManeuverIcon.ARRIVE
    if (t == "depart") return ManeuverIcon.DEPART
    if (t == "roundabout" || t == "rotary" || t == "roundabout turn" ||
        t == "exit roundabout" || t == "exit rotary"
    ) {
        return ManeuverIcon.ROUNDABOUT
    }
    if (m == "uturn") return ManeuverIcon.UTURN
    if (m.contains("left")) return ManeuverIcon.LEFT
    if (m.contains("right")) return ManeuverIcon.RIGHT
    if (t == "end of road") return ManeuverIcon.LEFT
    return ManeuverIcon.STRAIGHT
}

/** Short Spanish countdown: "80 m", "1.2 km". */
fun fmtDist(meters: Int): String {
    val m = meters.coerceAtLeast(0)
    return if (m < 1000) "$m m" else {
        val km = m / 1000f
        if (km < 10) "%.1f km".format(km) else "%d km".format(km.roundToInt())
    }
}

/** "quedan 3.4 km · 8 min". */
fun fmtRemaining(meters: Int, etaMin: Int): String =
    "quedan ${fmtDist(meters)} · $etaMin min"

/** Haversine distance in meters. */
fun geoDist(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2.0) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
    return 2 * r * asin(sqrt(a))
}

/**
 * Projects a point onto segment AB (equirectangular approx, fine for <1 km).
 * Returns Triple(fraction 0..1 along AB, distance from A in meters, lateral meters).
 */
fun projectOnSegment(
    lat: Double, lon: Double,
    aLat: Double, aLon: Double,
    bLat: Double, bLon: Double
): Triple<Double, Double, Double> {
    val kx = 111320.0 * cos(Math.toRadians((aLat + bLat) / 2.0))
    val ky = 110540.0
    val ax = 0.0
    val ay = 0.0
    val bx = (bLon - aLon) * kx
    val by = (bLat - aLat) * ky
    val px = (lon - aLon) * kx
    val py = (lat - aLat) * ky
    val len2 = bx * bx + by * by
    if (len2 < 1.0) return Triple(0.0, 0.0, sqrt(px * px + py * py))
    var t = (px * bx + py * by) / len2
    t = t.coerceIn(0.0, 1.0)
    val segLen = sqrt(len2)
    // Distancia al punto MÁS CERCANO DEL SEGMENTO, no a la recta infinita.
    // Sin esto, en una calle recta cualquier punto de la misma longitud
    // "encaja" con el primer segmento y el cursor de ruta nunca avanza.
    val projX = t * bx
    val projY = t * by
    val dx = px - projX
    val dy = py - projY
    val distance = sqrt(dx * dx + dy * dy)
    return Triple(t, t * segLen, distance)
}
