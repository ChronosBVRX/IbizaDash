package com.chronosbvrx.ibizadash.car

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Motor GPS del estéreo.
 *
 * Reglas estrictas:
 *  - Escucha GPS, red y pasivo; inyecta XTRA/tiempo al arrancar.
 *  - Rechaza saltos imposibles (> 220 km/h implícitos) y fixes con mala
 *    exactitud cuando ya hay uno reciente y bueno.
 *  - Suaviza velocidad y rumbo; si no hay rumbo GPS lo deriva del movimiento.
 *  - Entre fixes buenos mantiene la posición con estimación de velocidad
 *    constante que se va agotando: 20 s de extrapolación y luego se declara
 *    "sin señal" con la última posición conocida.
 *
 * No inventa posición indefinidamente: un túnel o un estacionamiento sin
 * cielo no se pueden adivinar. Lo que garantiza es que el velocímetro y el
 * mapa no se queden en blanco ni salten al recuperar la señal.
 */
data class GpsFix(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float,
    val bearingDeg: Float,
    val accuracyM: Float,
    val ageMs: Long,
    val extrapolated: Boolean,
    val stale: Boolean,
    val lost: Boolean,
    val satellites: Int,
    val source: String,
    val timestampMs: Long
)

class GpsEngine(private val context: Context) : LocationListener {

    companion object {
        private const val TAG = "GpsEngine"
        private const val GPS_MIN_TIME_MS = 500L
        private const val NETWORK_MIN_TIME_MS = 1000L
        private const val TICK_MS = 250L

        /** Un fix se considera vivo durante este tiempo. */
        private const val FRESH_MS = 2_500L

        /** Horizonte de estimación (velocidad constante con decaimiento). */
        private const val EXTRAPOLATE_MS = 20_000L

        /** Después de esto se declara pérdida de señal y la velocidad cae a 0. */
        private const val LOST_MS = 25_000L

        private const val MAX_ACCURACY_M = 80f
        private const val MAX_JUMP_KMH = 220f
        private const val EARTH_RADIUS_M = 6_371_000.0
    }

    var onFix: ((GpsFix) -> Unit)? = null
    var onNoFix: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val manager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private var running = false
    private var updatesRegistered = false
    private var gnssCallback: Any? = null
    private var providerReceiver: BroadcastReceiver? = null
    private var lastNoFixAtMs = 0L

    private var last: Location? = null
    private var lastElapsedMs = 0L
    private var lastWallMs = 0L
    private var lastSpeedKmh = 0f
    private var lastBearingDeg = 0f
    private var lastBearingValid = false
    private var lastAccuracyM = 999f
    private var lastSource = "gps"
    private var satellites = 0
    private var stuckCount = 0
    private var lastRecoveryAtMs = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            emitState()
            handler.postDelayed(this, TICK_MS)
        }
    }

    fun start() {
        if (running) return
        if (!hasPermission()) return
        val lm = manager ?: return
        running = true
        lastRecoveryAtMs = SystemClock.elapsedRealtime()
        injectAssist(lm)
        registerProviders()
        registerGnss(lm)
        registerProviderReceiver()
        loadLastKnown(lm)
        handler.removeCallbacks(tick)
        handler.post(tick)
        Log.i(TAG, "motor GPS iniciado")
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(tick)
        unregisterProviders()
        unregisterGnss()
        unregisterProviderReceiver()
        Log.i(TAG, "motor GPS detenido")
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun registerProviders() {
        val lm = manager ?: return
        unregisterProviders()
        var any = false
        runCatching {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    GPS_MIN_TIME_MS,
                    0f,
                    this
                )
                any = true
            }
        }.onFailure { Log.w(TAG, "GPS updates: ${it.message}") }
        runCatching {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    NETWORK_MIN_TIME_MS,
                    0f,
                    this
                )
                any = true
            }
        }.onFailure { Log.w(TAG, "red updates: ${it.message}") }
        runCatching {
            if (lm.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 0L, 0f, this)
                any = true
            }
        }.onFailure { Log.w(TAG, "pasivo updates: ${it.message}") }
        updatesRegistered = any
    }

    @SuppressLint("MissingPermission")
    private fun unregisterProviders() {
        if (!updatesRegistered) return
        runCatching { manager?.removeUpdates(this) }
        updatesRegistered = false
    }

    @SuppressLint("MissingPermission")
    private fun loadLastKnown(lm: LocationManager) {
        val now = System.currentTimeMillis()
        val candidates = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).mapNotNull { provider ->
            runCatching {
                if (lm.isProviderEnabled(provider)) lm.getLastKnownLocation(provider) else null
            }.getOrNull()
        }
        val best = candidates
            .filter { it.time in 1..now && now - it.time < 15 * 60_000L }
            .maxByOrNull { it.time }
        if (best != null && (last == null || best.time > lastWallMs)) {
            ingest(best, fromCache = true)
        } else if (last == null) {
            onNoFix?.invoke()
        }
    }

    /** Pide al chip que descargue asistencia XTRA y hora; acelera el primer fix. */
    @SuppressLint("MissingPermission")
    private fun injectAssist(lm: LocationManager) {
        Thread {
            runCatching {
                lm.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_xtra_injection", null)
                lm.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_time_injection", null)
            }
        }.start()
    }

    @SuppressLint("MissingPermission", "NewApi")
    private fun registerGnss(lm: LocationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var used = 0
                for (i in 0 until status.satelliteCount) {
                    if (status.usedInFix(i)) used++
                }
                satellites = used
            }

            override fun onStopped() {
                satellites = 0
            }

            override fun onStarted() = Unit
        }
        runCatching { lm.registerGnssStatusCallback(callback, handler) }
            .onSuccess { gnssCallback = callback }
            .onFailure { Log.w(TAG, "GnssStatus: ${it.message}") }
    }

    @SuppressLint("MissingPermission", "NewApi")
    private fun unregisterGnss() {
        val callback = gnssCallback as? GnssStatus.Callback ?: return
        runCatching { manager?.unregisterGnssStatusCallback(callback) }
        gnssCallback = null
    }

    private fun registerProviderReceiver() {
        if (providerReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (!running) return
                handler.post {
                    if (!running) return@post
                    registerProviders()
                    manager?.let { injectAssist(it) }
                }
            }
        }
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            providerReceiver = receiver
        }.onFailure { Log.w(TAG, "receiver proveedores: ${it.message}") }
    }

    private fun unregisterProviderReceiver() {
        val receiver = providerReceiver ?: return
        runCatching { context.unregisterReceiver(receiver) }
        providerReceiver = null
    }

    override fun onLocationChanged(location: Location) {
        if (!running) return
        ingest(location, fromCache = false)
    }

    override fun onProviderEnabled(provider: String) {
        handler.post { if (running) registerProviders() }
    }

    override fun onProviderDisabled(provider: String) {
        handler.post { if (running) registerProviders() }
    }

    @Suppress("DEPRECATION")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    private fun ingest(location: Location, fromCache: Boolean) {
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        // Para un fix en vivo la edad se mide desde que llega, no con el reloj
        // del estéreo (puede estar mal ajustado y marcar horas de diferencia).
        val wallAge = if (fromCache && location.time > 0L) {
            (nowWall - location.time).coerceAtLeast(0L)
        } else {
            0L
        }
        if (fromCache && wallAge > 15 * 60_000L) return
        if (fromCache && location.time > 0L && lastWallMs > 0L &&
            location.time < lastWallMs - 1_000L
        ) return

        // Con un fix GPS reciente, la red solo mete ruido: se ignora hasta que
        // el GPS lleve más de 10 s sin responder.
        if (!fromCache && location.provider == LocationManager.NETWORK_PROVIDER &&
            lastSource == LocationManager.GPS_PROVIDER &&
            nowElapsed - lastElapsedMs < 10_000L
        ) return

        val accuracy = if (location.hasAccuracy()) location.accuracy else 999f
        val previous = last
        val dtSec = ((nowElapsed - lastElapsedMs).coerceAtLeast(0L) / 1000.0)
            .coerceAtLeast(0.25)

        var distanceM = 0.0
        if (previous != null) {
            val out = FloatArray(1)
            Location.distanceBetween(
                previous.latitude,
                previous.longitude,
                location.latitude,
                location.longitude,
                out
            )
            distanceM = out[0].toDouble()
            val impliedKmh = distanceM / dtSec * 3.6
            if (!fromCache && impliedKmh > MAX_JUMP_KMH && accuracy > 20f) {
                Log.w(TAG, "salto rechazado: %.0f km/h, acc=%.0f m".format(impliedKmh, accuracy))
                return
            }
        }

        // Fix repetido (mismo timestamp y misma posición): el chip está
        // entregando la misma muestra otra vez. No se renueva la edad; así
        // arranca la estimación. Si la posición cambió, sí es un fix nuevo.
        if (!fromCache && location.time > 0L && location.time == lastWallMs &&
            distanceM < 0.5
        ) return

        // GPS "pegado": reporta velocidad pero la posición no cambia. Tras 3
        // muestras se deja de mover el punto y la extrapolación toma el control.
        if (!fromCache && previous != null && location.hasSpeed() &&
            location.speed > 2.2f && distanceM < 1.0 && dtSec < 6.0
        ) {
            stuckCount++
            if (stuckCount >= 3) {
                lastSpeedKmh = (location.speed * 3.6f).coerceIn(0f, 260f)
                return
            }
        } else if (distanceM >= 1.0 || !location.hasSpeed() || location.speed <= 2.2f) {
            stuckCount = 0
        }

        if (!fromCache && location.hasAccuracy() && accuracy > MAX_ACCURACY_M &&
            previous != null && nowElapsed - lastElapsedMs < 15_000L
        ) {
            Log.w(TAG, "fix impreciso rechazado: %.0f m".format(accuracy))
            return
        }

        var rawKmh = when {
            location.hasSpeed() && location.speed.isFinite() && location.speed > 0.2f ->
                location.speed * 3.6f
            distanceM > 1.0 && dtSec > 0.4 ->
                (distanceM / dtSec * 3.6).toFloat()
            else -> lastSpeedKmh
        }.coerceIn(0f, 260f)

        // Un último fix conocido de hace rato no sirve para estimar movimiento.
        if (fromCache || wallAge > 3_000L) rawKmh = 0f

        val bearingFromFix = (location.hasBearing() && rawKmh > 3f) ||
            (distanceM > 2.0 && dtSec > 0.4)
        var rawBearing = when {
            location.hasBearing() && rawKmh > 3f -> location.bearing
            distanceM > 2.0 && dtSec > 0.4 -> bearingBetween(previous, location)
            else -> lastBearingDeg
        }
        rawBearing = ((rawBearing % 360f) + 360f) % 360f
        if (bearingFromFix) lastBearingValid = true

        val alpha = when {
            fromCache -> 0.25f
            dtSec < 0.8 -> 0.45f
            dtSec < 2.0 -> 0.60f
            else -> 0.75f
        }
        lastSpeedKmh += (rawKmh - lastSpeedKmh) * alpha
        if (lastSpeedKmh < 1.5f && rawKmh < 1.5f) lastSpeedKmh = 0f
        lastBearingDeg = circularLerp(lastBearingDeg, rawBearing, alpha)

        lastAccuracyM = accuracy
        lastSource = location.provider ?: "gps"
        last = location
        lastWallMs = if (location.time > 0L) location.time else nowWall
        lastElapsedMs = nowElapsed - wallAge.coerceAtMost(30_000L)

        emit(
            latitude = location.latitude,
            longitude = location.longitude,
            speedKmh = lastSpeedKmh,
            bearing = lastBearingDeg,
            accuracy = accuracy,
            age = wallAge,
            extrapolated = wallAge > FRESH_MS,
            stale = wallAge > FRESH_MS,
            lost = false
        )
    }

    private fun emitState() {
        val fix = last
        val now = SystemClock.elapsedRealtime()
        val age = if (fix != null) {
            (now - lastElapsedMs).coerceAtLeast(0L)
        } else {
            Long.MAX_VALUE
        }

        // Si el chip lleva un minuto sin dar nada, se re-solicita el GPS y se
        // reinyecta asistencia. No cambia la posición ni inventa datos.
        if (age > 60_000L && now - lastRecoveryAtMs > 60_000L) {
            lastRecoveryAtMs = now
            Log.w(TAG, "sin fix > 60 s; re-solicitando GPS")
            registerProviders()
            manager?.let { injectAssist(it) }
        }

        if (fix == null) {
            if (now - lastNoFixAtMs > 1_000L) {
                lastNoFixAtMs = now
                onNoFix?.invoke()
            }
            return
        }
        when {
            age <= FRESH_MS -> emit(
                latitude = fix.latitude,
                longitude = fix.longitude,
                speedKmh = lastSpeedKmh,
                bearing = lastBearingDeg,
                accuracy = lastAccuracyM,
                age = age,
                extrapolated = false,
                stale = false,
                lost = false
            )

            age <= EXTRAPOLATE_MS -> {
                val t = age / 1000.0
                val horizon = EXTRAPOLATE_MS / 1000.0
                val factor = (1.0 - t / horizon).coerceIn(0.0, 1.0)
                val predictedKmh = (lastSpeedKmh * factor).toFloat()
                // Integral de v(t) = v0 * (1 - t/T): distancia recorrida.
                // Sin rumbo válido no se inventa una dirección: se mantiene
                // la última posición y solo decae la velocidad.
                val distanceM = if (lastBearingValid) {
                    lastSpeedKmh / 3.6 * (t - t * t / (2.0 * horizon))
                } else {
                    0.0
                }
                val (lat, lon) = destination(
                    fix.latitude,
                    fix.longitude,
                    lastBearingDeg,
                    distanceM
                )
                emit(
                    latitude = lat,
                    longitude = lon,
                    speedKmh = predictedKmh,
                    bearing = lastBearingDeg,
                    accuracy = lastAccuracyM + (t * 2.5).toFloat(),
                    age = age,
                    extrapolated = true,
                    stale = true,
                    lost = false
                )
            }

            else -> emit(
                latitude = fix.latitude,
                longitude = fix.longitude,
                speedKmh = 0f,
                bearing = lastBearingDeg,
                accuracy = (lastAccuracyM + 200f).coerceAtMost(999f),
                age = age.coerceAtLeast(LOST_MS),
                extrapolated = true,
                stale = true,
                lost = true
            )
        }
    }

    private fun emit(
        latitude: Double,
        longitude: Double,
        speedKmh: Float,
        bearing: Float,
        accuracy: Float,
        age: Long,
        extrapolated: Boolean,
        stale: Boolean,
        lost: Boolean
    ) {
        val fix = GpsFix(
            latitude = latitude,
            longitude = longitude,
            speedKmh = speedKmh.coerceIn(0f, 260f),
            bearingDeg = ((bearing % 360f) + 360f) % 360f,
            accuracyM = accuracy.coerceIn(0f, 999f),
            ageMs = age,
            extrapolated = extrapolated,
            stale = stale,
            lost = lost,
            satellites = satellites,
            source = if (extrapolated) "predicted" else lastSource,
            timestampMs = lastWallMs
        )
        onFix?.invoke(fix)
    }

    private fun bearingBetween(from: Location?, to: Location): Float {
        if (from == null) return lastBearingDeg
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val deg = Math.toDegrees(atan2(y, x)).toFloat()
        return ((deg % 360f) + 360f) % 360f
    }

    private fun destination(
        latitude: Double,
        longitude: Double,
        bearingDeg: Float,
        distanceM: Double
    ): Pair<Double, Double> {
        if (distanceM <= 0.0) return latitude to longitude
        val lat1 = Math.toRadians(latitude)
        val lon1 = Math.toRadians(longitude)
        val bearing = Math.toRadians(bearingDeg.toDouble())
        val angular = distanceM / EARTH_RADIUS_M
        val lat2 = asin(
            sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing)
        )
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angular) * cos(lat1),
            cos(angular) - sin(lat1) * sin(lat2)
        )
        return Math.toDegrees(lat2) to Math.toDegrees(lon2)
    }

    private fun circularLerp(from: Float, to: Float, alpha: Float): Float {
        if (alpha <= 0f) return from
        if (alpha >= 1f) return to
        val delta = ((to - from + 540f) % 360f) - 180f
        val out = from + delta * alpha
        return ((out % 360f) + 360f) % 360f
    }
}
