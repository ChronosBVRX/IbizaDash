package com.chronosbvrx.ibizadash.nav

import android.os.SystemClock
import android.util.Log
import org.json.JSONObject

/**
 * Pushes the live navigation state to the phone over the Wi-Fi/Bluetooth
 * bridge so the companion dashboard never shows stale "sin ruta" data.
 *
 * Throttled: at most one update per 1.5 s while navigating, plus an immediate
 * snapshot when a route is set, cleared, or a new phone socket connects.
 */
object NavStateRelay {
    private const val TAG = "NavStateRelay"
    private const val MIN_INTERVAL_MS = 1500L
    private const val REPEAT_INTERVAL_MS = 15_000L

    private var started = false
    @Volatile private var lastSendMs = 0L
    @Volatile private var lastKey = ""
    @Volatile private var lastState: NavState? = null
    @Volatile private var lastRoute: NavRoute? = null
    @Volatile private var lastActive: Boolean? = null

    /** Single writer so heavy snapshots never block the UI thread. */
    private val sender = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "NavStateTx").apply { isDaemon = true }
    }

    private val listener: (NavState?) -> Unit = { onState(it) }

    fun start() {
        if (started) return
        started = true
        NavStateRepository.addListener(listener)
        Log.i(TAG, "relay iniciado")
    }

    /** Called from the route repository listener (any route origin). */
    fun onRouteSet(route: NavRoute) {
        lastRoute = route
        lastActive = true
        lastKey = ""
        lastSendMs = 0L
        // Immediate snapshot: the phone shows the destination while GPS locks.
        val obj = base(active = true)
            .put("destName", route.destinationName)
            .put("polyline", route.encodedPolyline)
            .put("totalM", route.totalDistanceMeters)
            .put("totalS", route.totalDurationSeconds)
            .put("remainingM", route.totalDistanceMeters)
            .put("remainingText", "Calculando…")
            .put("etaText", "")
            .put("distText", "")
            .put("actionText", "Esperando GPS")
            .put("icon", ManeuverIcon.STRAIGHT.name)
            .put("progress", 0.0)
        send(obj)
    }

    fun onRouteCleared() {
        lastRoute = null
        lastState = null
        lastActive = false
        lastKey = ""
        send(base(active = false))
    }

    private fun onState(st: NavState?) {
        lastState = st
        // Si el teléfono está calculando la navegación, no devolverle su
        // propio estado en eco: solo se publica en la UI del estéreo.
        if (PhoneNavBridge.active) return
        if (st == null) {
            if (lastActive == true) onRouteCleared()
            return
        }
        lastActive = true
        val now = SystemClock.elapsedRealtime()
        val key = buildString {
            append(st.destName).append('|')
            append(st.remainingText).append('|')
            append(st.etaText).append('|')
            append(st.distText).append('|')
            append(st.actionText).append('|')
            append(st.icon.name).append('|')
            append(st.arrived).append('|')
            append(st.offRoute).append('|')
            append((st.progress * 100).toInt())
        }
        val unchanged = key == lastKey
        if (unchanged && now - lastSendMs < REPEAT_INTERVAL_MS) return
        if (!unchanged && now - lastSendMs < MIN_INTERVAL_MS) return
        lastKey = key
        lastSendMs = now
        send(payloadFor(st))
    }

    /**
     * Snapshot completo (incluye la polilínea) para un cliente que acaba de
     * conectar. Se escribe directamente en su socket para no depender de qué
     * transporte elija [NavLink].
     */
    fun snapshotObject(): JSONObject {
        val st = lastState
        val route = lastRoute
        return when {
            st != null -> payloadFor(st).apply {
                // Snapshot-only heavy fields: the phone needs the geometry to
                // draw the route sketch, but it is not resent on every fix.
                route?.let { r ->
                    put("polyline", r.encodedPolyline)
                    put("totalM", r.totalDistanceMeters)
                    put("totalS", r.totalDurationSeconds)
                    put("destName", r.destinationName)
                }
            }
            route != null -> base(active = true)
                .put("destName", route.destinationName)
                .put("polyline", route.encodedPolyline)
                .put("totalM", route.totalDistanceMeters)
                .put("totalS", route.totalDurationSeconds)
                .put("remainingM", route.totalDistanceMeters)
                .put("remainingText", "Calculando…")
                .put("etaText", "")
                .put("distText", "")
                .put("actionText", "Esperando GPS")
                .put("icon", ManeuverIcon.STRAIGHT.name)
                .put("progress", 0.0)
            else -> base(active = false)
        }
    }

    /** Sends the current snapshot to every connected phone (on new socket). */
    fun sendSnapshot() {
        send(snapshotObject())
    }

    private fun payloadFor(st: NavState): JSONObject {
        return base(active = true)
            .put("destName", st.destName)
            .put("remainingM", remainingMeters(st))
            .put("remainingText", st.remainingText)
            .put("etaText", st.etaText)
            .put("distText", st.distText)
            .put("actionText", st.actionText)
            .put("icon", st.icon.name)
            .put("progress", st.progress.toDouble())
            .put("arrived", st.arrived)
            .put("offRoute", st.offRoute)
            .put("speedKmh", st.speedKmh.toDouble())
    }

    private fun remainingMeters(st: NavState): Int {
        // "quedan 1.2 km · 8 min" → 1200. Keeps the phone card accurate even
        // though NavState only carries the formatted string.
        val text = st.remainingText
        val regex = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*(km|m)")
        val m = regex.find(text) ?: return 0
        val value = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return 0
        return if (m.groupValues[2] == "km") (value * 1000).toInt() else value.toInt()
    }

    private fun base(active: Boolean): JSONObject =
        JSONObject().put("type", "NAV_STATE").put("active", active)

    private fun send(obj: JSONObject) {
        sender.execute {
            try {
                NavLink.broadcast(obj)
            } catch (e: Exception) {
                Log.w(TAG, "send: ${e.message}")
            }
        }
    }
}
