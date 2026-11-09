package com.chronosbvrx.ibizacompanion.nav

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.chronosbvrx.ibizacompanion.BridgeLink
import com.chronosbvrx.ibizacompanion.core.state.CompanionState
import com.chronosbvrx.ibizacompanion.data.RouteBuilder
import com.chronosbvrx.ibizacompanion.service.MediaBridgeService
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Controlador de la navegación "phone-first".
 *
 * - Mantiene el [PhoneNavigator] con el GPS del teléfono.
 * - Publica `PHONE_NAV_STATE` al estéreo y actualiza la UI local.
 * - Si el auto se sale de la ruta, recalcula con OSRM y reenvía `ROUTE_SET`.
 */
object PhoneNavController {
    private const val TAG = "PhoneNavCtl"

    @Volatile
    var active: Boolean = false
        private set

    private var appContext: Context? = null
    private var navigator: PhoneNavigator? = null
    private var lastRerouteMs = 0L
    @Volatile private var lastStateMs = 0L
    @Volatile private var lastSendMs = 0L
    @Volatile private var lastSnapshot: PhoneNavSnapshot? = null
    @Volatile private var completed = false
    /** Si el estéreo usa su GPS, no hace falta inundar el enlace con estados. */
    @Volatile private var positionStreaming = true
    private var watchdog: Thread? = null
    /** Red en un hilo propio: el GPS entrega en el hilo principal. */
    private val sender = Executors.newSingleThreadExecutor { r ->
        Thread(r, "PhoneNavTx").apply { isDaemon = true }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        if (watchdog == null) {
            watchdog = Thread {
                while (true) {
                    try {
                        Thread.sleep(2000L)
                        if (!active) continue
                        val now = SystemClock.elapsedRealtime()
                        val snapshot = lastSnapshot
                        if (snapshot != null && now - lastSendMs > 2500L) {
                            // Reenvío periódico: mantiene vivo el estado en el
                            // estéreo aunque el GPS no entregue fixes nuevos o
                            // el socket se haya reconectado.
                            sendToStereo(snapshot)
                        }
                        if (!completed && now - lastStateMs > 10_000L) {
                            // Sin GPS/fixes: dejar que el estéreo vuelva a su
                            // guía local y aceptar sus NAV_STATE como respaldo.
                            active = false
                            Log.w(TAG, "sin fixes del teléfono; guía local del estéreo")
                        }
                    } catch (_: InterruptedException) {
                        break
                    } catch (_: Exception) {
                    }
                }
            }.apply {
                name = "PhoneNavWatch"
                isDaemon = true
                start()
            }
        }
    }

    /** Llamado al enviar una ruta desde la app o al recalcular. */
    fun setRoute(
        context: Context,
        destinationName: String,
        distanceMeters: Int,
        durationSeconds: Int,
        encodedPolyline: String,
        steps: List<JSONObject>
    ) {
        val points = decodePolyline(encodedPolyline)
        val end = points.lastOrNull()
        if (end == null) {
            Log.w(TAG, "ruta sin puntos")
            return
        }
        setRoute(
            context.applicationContext,
            PhoneRoute(
                destinationName = destinationName,
                distanceMeters = distanceMeters,
                durationSeconds = durationSeconds,
                encodedPolyline = encodedPolyline,
                steps = steps,
                destLat = end.first,
                destLon = end.second
            )
        )
    }

    private fun setRoute(context: Context, route: PhoneRoute) {
        appContext = context.applicationContext
        PhoneRouteRepository.current = route
        // El GPS debe seguir con la app en segundo plano/pantalla apagada.
        MediaBridgeService.promoteLocationForeground()
        navigator?.stop()
        navigator = PhoneNavigator(
            context = context.applicationContext,
            route = route,
            onState = { snapshot -> publish(snapshot) },
            onOffRoute = { snapshot -> reroute(snapshot) }
        )
        val started = navigator?.start() == true
        active = started
        lastStateMs = if (started) SystemClock.elapsedRealtime() else 0L
        lastSendMs = 0L
        lastSnapshot = null
        completed = false
        CompanionState.update {
            it.copy(
                routeActive = true,
                routeDestination = route.destinationName,
                routePolyline = route.encodedPolyline,
                routeDistanceM = route.distanceMeters,
                routeDurationS = route.durationSeconds,
                routeRemainingM = route.distanceMeters,
                routeRemainingText = "Calculando…",
                routeEtaText = "",
                routeNextStep = "Esperando GPS",
                routeIcon = "STRAIGHT",
                routeProgress = 0f,
                routeArrived = false,
                routeOffRoute = false
            )
        }
    }

    fun setPositionStreaming(enabled: Boolean) {
        positionStreaming = enabled
        Log.i(TAG, "PHONE_NAV_STATE streaming=$enabled")
    }

    fun clear() {
        active = false
        completed = false
        lastStateMs = 0L
        lastSendMs = 0L
        lastSnapshot = null
        navigator?.stop()
        navigator = null
        PhoneRouteRepository.current = null
        lastRerouteMs = 0L
    }

    private fun publish(snapshot: PhoneNavSnapshot) {
        active = true
        lastStateMs = SystemClock.elapsedRealtime()
        if (snapshot.arrived) completed = true
        lastSnapshot = snapshot
        // La velocidad alimenta el fps adaptativo del mapa remoto.
        com.chronosbvrx.ibizacompanion.map.MapFrameSender.setSpeed(snapshot.speedKmh)
        Log.d(TAG, "state ${snapshot.icon} ${snapshot.actionText} · ${snapshot.remainingText}")
        sendToStereo(snapshot)

        CompanionState.update {
            it.copy(
                routeActive = true,
                routeDestination = snapshot.destName,
                routeRemainingM = snapshot.remainingM,
                routeRemainingText = snapshot.remainingText,
                routeEtaText = snapshot.etaText,
                routeNextStep = snapshot.actionText,
                routeIcon = snapshot.icon,
                routeProgress = snapshot.progress,
                routeArrived = snapshot.arrived,
                routeOffRoute = snapshot.offRoute
            )
        }
    }

    private fun sendToStereo(snapshot: PhoneNavSnapshot) {
        lastSendMs = SystemClock.elapsedRealtime()
        // El estéreo navega con su GPS: el teléfono conserva su UI local,
        // pero no transmite posiciones cada segundo.
        if (!positionStreaming) return
        val json = JSONObject()
            .put("type", "PHONE_NAV_STATE")
            .put("active", true)
            .put("destName", snapshot.destName)
            .put("remainingM", snapshot.remainingM)
            .put("remainingText", snapshot.remainingText)
            .put("etaText", snapshot.etaText)
            .put("distText", snapshot.distText)
            .put("actionText", snapshot.actionText)
            .put("thenText", snapshot.thenText ?: JSONObject.NULL)
            .put("icon", snapshot.icon)
            .put("progress", snapshot.progress.toDouble())
            .put("arrived", snapshot.arrived)
            .put("offRoute", snapshot.offRoute)
            .put("lat", snapshot.lat)
            .put("lon", snapshot.lon)
            .put("snappedLat", snapshot.snappedLat)
            .put("snappedLon", snapshot.snappedLon)
            .put("bearing", snapshot.bearing.toDouble())
            .put("speedKmh", snapshot.speedKmh.toDouble())
        sender.execute {
            BridgeLink.send(json)
        }
    }

    private fun reroute(from: PhoneNavSnapshot) {
        val route = PhoneRouteRepository.current ?: return
        val context = appContext ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRerouteMs < 30_000L) return
        lastRerouteMs = now
        Thread {
            try {
                val built = RouteBuilder.build(
                    context,
                    from.lat,
                    from.lon,
                    route.destLat,
                    route.destLon,
                    route.destinationName
                )
                if (built == null) {
                    Log.w(TAG, "reroute: OSRM sin respuesta")
                    return@Thread
                }
                val sent = BridgeLink.sendRouteSet(
                    built.destinationName,
                    built.distanceMeters,
                    built.durationSeconds,
                    built.encodedPolyline,
                    built.steps,
                    built.trafficDelaySeconds,
                    built.trafficSections
                )
                Log.i(TAG, "reroute enviada=$sent")
                if (sent) {
                    setRoute(
                        context,
                        built.destinationName,
                        built.distanceMeters,
                        built.durationSeconds,
                        built.encodedPolyline,
                        built.steps
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "reroute: ${e.message}")
            }
        }.apply {
            name = "PhoneReroute"
            isDaemon = true
            start()
        }
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
