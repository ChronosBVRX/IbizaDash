package com.chronosbvrx.ibizacompanion.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
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
    private const val INTERVAL_MS = 500L
    private const val TARGET_W = 1024
    private const val TARGET_H = 720

    private val mainHandler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "MapFrameTx").apply { isDaemon = true }
    }
    private val busy = AtomicBoolean(false)

    @Volatile
    private var webView: WebView? = null

    @Volatile
    private var running = false

    private var seq = 0

    private val capture = Runnable { captureFrame() }

    fun start(view: WebView) {
        if (running && webView === view) return
        webView = view
        running = true
        busy.set(false)
        mainHandler.removeCallbacks(capture)
        mainHandler.post(capture)
        Log.i(TAG, "proyección de mapa iniciada")
    }

    fun stop() {
        if (!running) return
        running = false
        webView = null
        mainHandler.removeCallbacks(capture)
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
                frame.compress(Bitmap.CompressFormat.JPEG, 80, out)
                frame.recycle()
                if (!running || !BridgeLink.isWifiActive()) return@execute
                val bytes = out.toByteArray()
                val json = JSONObject()
                    .put("type", "MAP_FRAME")
                    .put("w", TARGET_W)
                    .put("h", TARGET_H)
                    .put("seq", ++seq)
                    .put("jpegBase64", Base64.encodeToString(bytes, Base64.NO_WRAP))
                val ok = BridgeLink.send(json)
                if (seq <= 3 || seq % 20 == 0) {
                    Log.i(TAG, "frame seq=$seq bytes=${bytes.size} ok=$ok")
                }
            } catch (e: Exception) {
                Log.w(TAG, "send: ${e.message}")
            } finally {
                busy.set(false)
            }
        }
        schedule()
    }

    private fun schedule() {
        mainHandler.removeCallbacks(capture)
        if (running) mainHandler.postDelayed(capture, INTERVAL_MS)
    }
}
