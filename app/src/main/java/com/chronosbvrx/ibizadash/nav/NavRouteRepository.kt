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

data class NavRoute(
    val destinationName: String,
    val totalDistanceMeters: Int,
    val totalDurationSeconds: Int,
    val encodedPolyline: String,
    val steps: List<NavStep> = emptyList()
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