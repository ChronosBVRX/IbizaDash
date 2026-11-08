package com.chronosbvrx.ibizadash.nav

import java.util.concurrent.CopyOnWriteArraySet

data class NavStep(
    val lat: Double,
    val lon: Double,
    val instruction: String,
    val maneuver: String? = null,
    val modifier: String? = null,
    val distanceMeters: Int = 0
)

/** Tramo con retraso, en fracción 0..1 del recorrido total. */
data class TrafficSpan(
    val from: Float,
    val to: Float,
    val delaySeconds: Int,
    val speedKmh: Int
)

data class NavRoute(
    val destinationName: String,
    val totalDistanceMeters: Int,
    val totalDurationSeconds: Int,
    val encodedPolyline: String,
    val steps: List<NavStep> = emptyList(),
    /** Retraso por tráfico en segundos (0 = sin dato). */
    val trafficDelaySeconds: Int = 0,
    val trafficSections: List<TrafficSpan> = emptyList()
)

object NavRouteRepository {
    private val listeners = CopyOnWriteArraySet<(NavRoute?) -> Unit>()
    var current: NavRoute? = null
        private set

    fun addListener(listener: (NavRoute?) -> Unit) {
        listeners.add(listener)
        listener(current)
    }

    fun removeListener(listener: (NavRoute?) -> Unit) {
        listeners.remove(listener)
    }

    fun set(route: NavRoute?) {
        current = route
        listeners.forEach { it(route) }
    }

    fun clear() = set(null)
}