package com.chronosbvrx.ibizacompanion.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import com.chronosbvrx.ibizacompanion.BridgeLink
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Renderizado remoto del mapa (Fase 2).
 *
 * Captura la WebView Leaflet visible, recorta una ventana con la proporción
 * del panel del estéreo (1024×720), la comprime en JPEG y la envía como
 * `MAP_FRAME`. El estéreo sólo decodifica la imagen: osmdroid deja de dibujar.
 */
object MapFrameSender {
    private const val TAG = "MapFrame"
    private const val INTERVAL_SLOW_MS = 600L // ~1,7 fps aparcado / despacio
    private const val INTERVAL_FAST_MS = 400L // 2,5 fps por encima de 30 km/h
    private const val FAST_SPEED_KMH = 30f
    private const val TARGET_W = 1024
    private const val TARGET_H = 720
    // WebP q65 pesa bastante menos que JPEG q80 con el mismo aspecto en un mapa.
    private const val FRAME_QUALITY = 65
    private const val SLOW_SEND_MS = 120L
    private const val MAX_THROTTLE_MS = 1_800L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "MapFrameTx").apply { isDaemon = true }
    }
    private val busy = AtomicBoolean(false)

    @Volatile
    private var webView: WebView? = null

    @Volatile
    private var running = false

    /**
     * Si el enlace tarda en tragarse un frame (el TCP se llena), se espacian
     * los envíos. Es la válvula que impide que el mapa ahogue el audio A2DP
     * en estéreos que solo tienen 2,4 GHz.
     */
    @Volatile
    private var throttleMs = 0L

    /** Último frame enviado: si el mapa no cambió, no se gasta enlace. */
    @Volatile
    private var lastSent: ByteArray? = null

    /** Momento del último envío real (para no dejar sin frames al estéreo). */
    @Volatile
    private var lastSentAt = 0L

    /**
     * El estéreo puede pedir que se detenga la proyección: en modo navegación
     * dibuja su propio mapa a 60 fps y el vídeo por Wi-Fi solo gasta ancho de
     * banda.
     */
    @Volatile
    private var suppressed = false

    fun setSuppressed(value: Boolean) {
        if (suppressed == value) return
        suppressed = value
        if (value) {
            stop()
        } else {
            Log.i(TAG, "proyección habilitada por el estéreo")
        }
    }

    /** Velocidad actual del coche (la alimenta PhoneNavController). */
    @Volatile
    private var speedKmh = 0f

    private var seq = 0

    private val capture = Runnable { captureFrame() }

    /** FPS adaptativo: ~1,7 fps parado/despacio, 2,5 fps en marcha. */
    fun setSpeed(speed: Float) {
        speedKmh = speed
    }

    private fun currentIntervalMs(): Long =
        (if (speedKmh > FAST_SPEED_KMH) INTERVAL_FAST_MS else INTERVAL_SLOW_MS) + throttleMs

    fun start(view: WebView) {
        if (suppressed) {
            Log.i(TAG, "proyección suprimida por el estéreo (modo navegación)")
            return
        }
        if (running && webView === view) return
        webView = view
        running = true
        busy.set(false)
        lastSent = null
        lastSentAt = 0L
        throttleMs = 0L
        mainHandler.removeCallbacks(capture)
        mainHandler.post(capture)
        // La proyección vive bajo el servicio en primer plano: wake lock
        // parcial + notificación visible mientras se transmite el mapa.
        com.chronosbvrx.ibizacompanion.service.MediaBridgeService.setMapStreaming(true)
        Log.i(TAG, "proyección de mapa iniciada")
    }

    fun stop() {
        if (!running) return
        running = false
        webView = null
        lastSent = null
        lastSentAt = 0L
        throttleMs = 0L
        mainHandler.removeCallbacks(capture)
        com.chronosbvrx.ibizacompanion.service.MediaBridgeService.setMapStreaming(false)
        // El envío va al hilo de red: stop() se llama desde la UI.
        io.execute {
            try {
                BridgeLink.send(JSONObject().put("type", "MAP_FRAME_STOP"))
            } catch (_: Exception) {
            }
        }
        Log.i(TAG, "proyección de mapa detenida")
    }

    private fun captureFrame() {
        if (!running) return
        if (!BridgeLink.isWifiActive()) {
            schedule()
            return
        }
        val view = webView
        if (view == null || view.width <= 0 || view.height <= 0) {
            schedule()
            return
        }
        if (!busy.compareAndSet(false, true)) {
            schedule()
            return
        }
        val frame = try {
            val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(full)
            view.draw(canvas)

            val aspect = TARGET_W.toFloat() / TARGET_H.toFloat()
            var cropW = view.width
            var cropH = (cropW / aspect).toInt()
            if (cropH > view.height) {
                cropH = view.height
                cropW = (cropH * aspect).toInt()
            }
            val x = ((view.width - cropW) / 2).coerceAtLeast(0)
            val y = ((view.height - cropH) / 2).coerceAtLeast(0)
            val cropped = Bitmap.createBitmap(full, x, y, cropW, cropH)
            full.recycle()
            val scaled = Bitmap.createScaledBitmap(cropped, TARGET_W, TARGET_H, true)
            if (scaled !== cropped) cropped.recycle()
            scaled
        } catch (e: Exception) {
            Log.w(TAG, "capture: ${e.message}")
            null
        }
        if (frame == null) {
            busy.set(false)
            schedule()
            return
        }
        io.execute {
            try {
                val out = ByteArrayOutputStream()
                @Suppress("DEPRECATION")
                frame.compress(Bitmap.CompressFormat.WEBP, FRAME_QUALITY, out)
                frame.recycle()
                if (!running || !BridgeLink.isWifiActive()) return@execute
                val bytes = out.toByteArray()
                // Mapa quieto (aparcado, semáforo): no repetir el mismo frame.
                // Se reenvía cada 2 s para que el estéreo no pierda la proyección.
                val now = SystemClock.elapsedRealtime()
                if (bytes.contentEquals(lastSent) && now - lastSentAt < 2_000L) {
                    return@execute
                }
                val json = JSONObject()
                    .put("type", "MAP_FRAME")
                    .put("w", TARGET_W)
                    .put("h", TARGET_H)
                    .put("seq", ++seq)
                    .put("jpegBase64", Base64.encodeToString(bytes, Base64.NO_WRAP))
                val t0 = SystemClock.elapsedRealtime()
                val ok = BridgeLink.send(json)
                adaptThrottle(SystemClock.elapsedRealtime() - t0)
                if (ok) {
                    lastSent = bytes
                    lastSentAt = SystemClock.elapsedRealtime()
                }
                if (seq <= 3 || seq % 20 == 0) {
                    Log.i(TAG, "frame seq=$seq bytes=${bytes.size} ok=$ok thr=${throttleMs}ms")
                }
            } catch (e: Exception) {
                Log.w(TAG, "send: ${e.message}")
            } finally {
                busy.set(false)
            }
        }
        schedule()
    }

    /** Si el enlace se llena, se espacian los frames; si vuelve a ir fino, se recupera. */
    private fun adaptThrottle(elapsedMs: Long) {
        throttleMs = when {
            elapsedMs > 250 -> (throttleMs + 300).coerceAtMost(MAX_THROTTLE_MS)
            elapsedMs > SLOW_SEND_MS -> (throttleMs + 150).coerceAtMost(MAX_THROTTLE_MS)
            throttleMs > 0 -> (throttleMs - 80).coerceAtLeast(0L)
            else -> 0L
        }
    }

    private fun schedule() {
        mainHandler.removeCallbacks(capture)
        if (running) mainHandler.postDelayed(capture, currentIntervalMs())
    }
}
