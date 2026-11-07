package com.chronosbvrx.ibizadash.nav

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Mapa renderizado por el teléfono (Fase 2).
 *
 * El teléfono captura su WebView Leaflet, la comprime en JPEG y manda
 * `MAP_FRAME`. El estéreo solo decodifica la imagen y la pinta en un ImageView
 * encima del MapView; osmdroid queda oculto y sin carga de teselas.
 */
object RemoteMapBridge {
    private const val TAG = "RemoteMap"
    private const val TIMEOUT_MS = 5000L

    @Volatile
    var active: Boolean = false
        private set

    @Volatile
    private var lastFrameMs = 0L

    @Volatile
    private var onFrame: ((Bitmap) -> Unit)? = null

    @Volatile
    private var onStop: (() -> Unit)? = null

    private val decoding = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val decoder = Executors.newSingleThreadExecutor { r ->
        Thread(r, "RemoteMapDecode").apply { isDaemon = true }
    }

    private var watchdog: Thread? = null

    fun attach(
        onFrame: (Bitmap) -> Unit,
        onStop: () -> Unit
    ) {
        this.onFrame = onFrame
        this.onStop = onStop
        active = false
        lastFrameMs = 0L
        if (watchdog == null) {
            watchdog = Thread {
                while (true) {
                    try {
                        Thread.sleep(2000L)
                        if (!active) continue
                        if (SystemClock.elapsedRealtime() - lastFrameMs <= TIMEOUT_MS) continue
                        Log.w(TAG, "sin frames del teléfono; vuelve el mapa nativo")
                        stop()
                    } catch (_: InterruptedException) {
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "watchdog: ${e.message}")
                    }
                }
            }.apply {
                name = "RemoteMapWatch"
                isDaemon = true
                start()
            }
        }
    }

    fun detach() {
        onFrame = null
        onStop = null
        active = false
        lastFrameMs = 0L
    }

    /** Llamado desde el hilo lector del socket. */
    fun onFrame(obj: JSONObject) {
        if (onFrame == null) return
        val b64 = obj.optString("jpegBase64", "")
        if (b64.isBlank()) return
        if (!active) Log.i(TAG, "primer frame del teléfono")
        active = true
        lastFrameMs = SystemClock.elapsedRealtime()
        if (!decoding.compareAndSet(false, true)) return
        decoder.execute {
            var bmp: Bitmap? = null
            try {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            } catch (e: Exception) {
                Log.w(TAG, "decode: ${e.message}")
            } finally {
                decoding.set(false)
            }
            val frame = bmp
            if (frame != null) {
                mainHandler.post {
                    if (active) onFrame?.invoke(frame)
                }
            }
        }
    }

    fun stop() {
        if (!active) return
        active = false
        mainHandler.post { onStop?.invoke() }
    }
}
