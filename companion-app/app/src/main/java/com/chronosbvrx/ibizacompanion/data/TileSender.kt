package com.chronosbvrx.ibizacompanion.data

import android.content.Context
import android.util.Base64
import android.util.Log
import com.chronosbvrx.ibizacompanion.AudioCoexistence
import com.chronosbvrx.ibizacompanion.BridgeLink
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.tan

/**
 * Envía al estéreo las teselas Esri Dark del corredor de una ruta.
 *
 * Fuente de cada tesela, en orden:
 *  1. Pack offline del teléfono (`offline-tiles/{z}/{x}/{y}.png`).
 *  2. Caché de osmdroid del teléfono.
 *  3. Descarga online (Esri World Dark Gray).
 *
 * Por el tamaño (cientos de KB) sólo tiene sentido sobre Wi-Fi; si el enlace
 * Wi-Fi se cae, la transferencia se detiene en lugar de saturar Bluetooth.
 */
object TileSender {
    private const val TAG = "TileSender"
    private const val MAX_TILES = 420
    private const val MAX_BYTES = 8L * 1024L * 1024L

    private const val LAYER_BASE = "base"
    private const val LAYER_REF = "ref"

    /** Peticiones del viewport: pequeñas, para no ahogar el enlace. */
    private const val VIEWPORT_MAX_BASE = 36
    private const val VIEWPORT_MAX_REF = 20
    private const val VIEWPORT_MAX_BYTES_BASE = 2L * 1024L * 1024L
    private const val VIEWPORT_MAX_BYTES_REF = 1_200_000L

    /** Evita que dos TILE_REQ seguidos se pisen en el socket. */
    private val viewportBusy = AtomicBoolean(false)

    /**
     * Cola de un solo hilo: base y etiquetas del mismo encuadre se mandan en
     * orden, sin dos ráfagas simultáneas compitiendo con el A2DP.
     */
    private val viewportQueue = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "TileViewport").apply { isDaemon = true }
    }

    @Volatile
    private var lastViewportKey = ""

    @Volatile
    private var lastViewportAtMs = 0L
    // El enlace se comparte con el audio A2DP. El ritmo lo decide
    // AudioCoexistence (60 KB/s con música, 250 KB/s en silencio).

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(14, TimeUnit.SECONDS)
        .build()

    data class Result(
        val sent: Int,
        val total: Int,
        val failed: Int,
        val bytes: Long
    )

    private data class TileKey(val zoom: Int, val x: Int, val y: Int)

    /**
     * Descarga/selecciona y envía las teselas. Bloqueante: llamar desde IO.
     * [onProgress] recibe (procesadas, total).
     */
    fun sendRouteTiles(
        context: Context,
        encodedPolyline: String,
        onProgress: (sent: Int, total: Int) -> Unit = { _, _ -> }
    ): Result {
        val points = decodePolyline(encodedPolyline)
        if (points.size < 2) return Result(0, 0, 0, 0)
        if (!BridgeLink.isWifiActive()) {
            Log.w(TAG, "sin Wi-Fi activo; no se envían teselas")
            return Result(0, 0, 0, 0)
        }

        val tiles = corridorTiles(points)
        val total = tiles.size
        var sent = 0
        var failed = 0
        var bytes = 0L

        for ((index, tile) in tiles.withIndex()) {
            if (!BridgeLink.isWifiActive()) {
                Log.w(TAG, "Wi-Fi perdido tras $sent teselas")
                break
            }
            // Con música sonando no se descarga online (riega más Wi-Fi y
            // corta el A2DP): solo se envían teselas locales; las faltantes
            // se completan en la próxima ruta sin música.
            val musicPlaying = AudioCoexistence.isMusicPlaying(context)
            val png = readLocalTile(context, tile, LAYER_BASE)
                ?: if (musicPlaying) null else downloadTile(tile, LAYER_BASE)
            if (png == null || png.size < 40) {
                failed++
                onProgress(index + 1, total)
                continue
            }
            val msg = JSONObject()
                .put("type", "TILE_BATCH")
                .put("layer", LAYER_BASE)
                .put("zoom", tile.zoom)
                .put("x", tile.x)
                .put("y", tile.y)
                .put("pngBase64", Base64.encodeToString(png, Base64.NO_WRAP))
            if (BridgeLink.send(msg)) {
                sent++
                bytes += png.size
            } else {
                failed++
            }
            onProgress(index + 1, total)
            if (bytes > MAX_BYTES) {
                Log.i(TAG, "tope de bytes alcanzado ($bytes)")
                break
            }
            // Ritmo suave: deja aire al audio Bluetooth aunque el enlace sea 2,4 GHz.
            pace(context, png.size)
        }

        try {
            BridgeLink.send(
                JSONObject()
                    .put("type", "TILE_STATS")
                    .put("sent", sent)
                    .put("total", total)
            )
        } catch (_: Exception) {
        }
        Log.i(TAG, "teselas enviadas=$sent/$total fallidas=$failed bytes=$bytes")
        return Result(sent, total, failed, bytes)
    }

    /**
     * Teselas del encuadre que está viendo el estéreo (TILE_REQ).
     *
     * Se piden al vuelo, con tope de cantidad y de bytes, y solo para el
     * zoom actual. `ref` es la capa de nombres de calles (PNG transparente).
     * Bloqueante: llamar desde IO.
     */
    fun sendViewportTiles(
        context: Context,
        zoom: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        layer: String,
        onProgress: (sent: Int, total: Int) -> Unit = { _, _ -> }
    ): Result {
        if (!BridgeLink.isWifiActive()) return Result(0, 0, 0, 0)
        if (zoom !in 13..18) return Result(0, 0, 0, 0)
        val isRef = layer == LAYER_REF
        val cap = if (isRef) VIEWPORT_MAX_REF else VIEWPORT_MAX_BASE
        val byteCap = if (isRef) VIEWPORT_MAX_BYTES_REF else VIEWPORT_MAX_BYTES_BASE
        val tiles = bboxTiles(zoom, x0, y0, x1, y1, cap)
        if (tiles.isEmpty()) return Result(0, 0, 0, 0)
        if (!viewportBusy.compareAndSet(false, true)) {
            Log.i(TAG, "viewport $layer ocupado; se descarta este pedido")
            return Result(0, 0, 0, 0)
        }
        var sent = 0
        var failed = 0
        var bytes = 0L
        try {
            for ((index, tile) in tiles.withIndex()) {
                if (!BridgeLink.isWifiActive()) break
                val musicPlaying = AudioCoexistence.isMusicPlaying(context)
                val png = readLocalTile(context, tile, layer)
                    ?: if (musicPlaying) null else downloadTile(tile, layer)
                if (png == null || png.size < 40) {
                    failed++
                    onProgress(index + 1, tiles.size)
                    continue
                }
                val msg = JSONObject()
                    .put("type", "TILE_BATCH")
                    .put("layer", layer)
                    .put("zoom", tile.zoom)
                    .put("x", tile.x)
                    .put("y", tile.y)
                    .put("pngBase64", Base64.encodeToString(png, Base64.NO_WRAP))
                if (BridgeLink.send(msg)) {
                    sent++
                    bytes += png.size
                } else {
                    failed++
                }
                onProgress(index + 1, tiles.size)
                if (bytes > byteCap) break
                pace(context, png.size)
            }
        } finally {
            viewportBusy.set(false)
        }
        Log.i(TAG, "viewport $layer z$zoom ${tiles.size} teselas: ok=$sent fallidas=$failed bytes=$bytes")
        return Result(sent, tiles.size, failed, bytes)
    }

    /**
     * Encola un TILE_REQ sin bloquear el lector del socket. Descarta pedidos
     * idénticos repetidos (el estéreo reintenta cada pocos segundos).
     */
    fun enqueueViewportTiles(
        context: Context,
        zoom: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        layer: String
    ) {
        val key = "$layer:$zoom:$x0:$y0:$x1:$y1"
        val now = System.currentTimeMillis()
        if (key == lastViewportKey && now - lastViewportAtMs < 15_000L) return
        lastViewportKey = key
        lastViewportAtMs = now
        val app = context.applicationContext
        viewportQueue.execute {
            try {
                sendViewportTiles(app, zoom, x0, y0, x1, y1, layer)
            } catch (e: Exception) {
                Log.w(TAG, "viewport: ${e.message}")
            }
        }
    }

    /** Caja del viewport → teselas, ordenadas desde el centro hacia afuera. */
    private fun bboxTiles(
        zoom: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        max: Int
    ): List<TileKey> {
        val ax0 = clampTile(minOf(x0, x1), zoom)
        val ax1 = clampTile(maxOf(x0, x1), zoom)
        val ay0 = clampTile(minOf(y0, y1), zoom)
        val ay1 = clampTile(maxOf(y0, y1), zoom)
        val cx = (ax0 + ax1) / 2.0
        val cy = (ay0 + ay1) / 2.0
        val out = ArrayList<TileKey>((ax1 - ax0 + 1) * (ay1 - ay0 + 1))
        for (x in ax0..ax1) {
            for (y in ay0..ay1) {
                out.add(TileKey(zoom, x, y))
            }
        }
        if (out.size > max) {
            out.sortBy {
                val dx = it.x - cx
                val dy = it.y - cy
                dx * dx + dy * dy
            }
            return out.subList(0, max)
        }
        return out
    }

    /** Duerme lo justo para el ritmo permitido (máx. 400 ms con música). */
    private fun pace(context: Context, bytes: Int) {
        val ms = AudioCoexistence.paceMs(context, bytes.toLong())
        if (ms <= 0L) return
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
        }
    }

    // ------------------------------------------------------------------
    // Construcción del corredor
    // ------------------------------------------------------------------

    private fun corridorTiles(points: List<Pair<Double, Double>>): List<TileKey> {
        val overview = LinkedHashSet<TileKey>()
        val centers = LinkedHashSet<TileKey>()
        val street = LinkedHashSet<TileKey>()
        val ring = LinkedHashSet<TileKey>()

        val step = max(1, points.size / 350)
        var i = 0
        while (i < points.size) {
            val (lat, lon) = points[i]
            overview.add(TileKey(14, lonToTileX(lon, 14), latToTileY(lat, 14)))
            val x = lonToTileX(lon, 15)
            val y = latToTileY(lat, 15)
            centers.add(TileKey(15, x, y))
            // El estéreo pinta su mapa local a z16; sin estas teselas la
            // calle se vería vacía fuera del paquete offline.
            street.add(TileKey(16, lonToTileX(lon, 16), latToTileY(lat, 16)))
            if (ring.size < MAX_TILES) {
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        ring.add(TileKey(15, clampTile(x + dx, 15), clampTile(y + dy, 15)))
                    }
                }
            }
            i += step
        }

        val out = LinkedHashSet<TileKey>()
        out.addAll(overview)
        out.addAll(centers)
        out.addAll(street)
        out.addAll(ring)
        return out.take(MAX_TILES)
    }

    private fun clampTile(value: Int, zoom: Int): Int {
        val max = (1 shl zoom) - 1
        return value.coerceIn(0, max)
    }

    private fun lonToTileX(lon: Double, zoom: Int): Int {
        val n = 1 shl zoom
        return (((lon + 180.0) / 360.0) * n).toInt().coerceIn(0, n - 1)
    }

    private fun latToTileY(lat: Double, zoom: Int): Int {
        val n = 1 shl zoom
        val rad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        val y = ((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * n).toInt()
        return y.coerceIn(0, n - 1)
    }

    // ------------------------------------------------------------------
    // Origen de las teselas
    // ------------------------------------------------------------------

    private fun readLocalTile(context: Context, tile: TileKey, layer: String): ByteArray? {
        val ext = context.getExternalFilesDir(null)
        val cacheDir = if (layer == LAYER_REF) "EsriDarkRef" else "EsriDark"
        val candidates = ArrayList<File>(6)
        // El pack offline solo trae el mapa base.
        if (layer != LAYER_REF) {
            if (ext != null) {
                candidates.add(File(ext, "offline-tiles/${tile.zoom}/${tile.x}/${tile.y}.png"))
            }
            candidates.add(
                File(context.filesDir, "offline-tiles/${tile.zoom}/${tile.x}/${tile.y}.png")
            )
        }
        // osmdroid 6 guarda ".tile"; los packs antiguos, ".png".
        val roots = ArrayList<File>(2)
        if (ext != null) roots.add(File(ext, "osmdroid/tiles/$cacheDir"))
        roots.add(File(context.filesDir, "osmdroid/tiles/$cacheDir"))
        for (root in roots) {
            candidates.add(File(root, "${tile.zoom}/${tile.x}/${tile.y}.tile"))
            candidates.add(File(root, "${tile.zoom}/${tile.x}/${tile.y}.png"))
        }
        for (file in candidates) {
            if (file.isFile && file.length() > 40) {
                return try {
                    file.readBytes()
                } catch (_: Exception) {
                    null
                }
            }
        }
        return null
    }

    /**
     * Esri usa /tile/{z}/{y}/{x}; el nombre local de osmdroid es {z}/{x}/{y}.tile.
     * La capa `ref` son las etiquetas (nombres de calles y POIs).
     */
    private fun downloadTile(tile: TileKey, layer: String): ByteArray? {
        val service = if (layer == LAYER_REF) {
            "Canvas/World_Dark_Gray_Reference"
        } else {
            "Canvas/World_Dark_Gray_Base"
        }
        val url = "https://server.arcgisonline.com/ArcGIS/rest/services/" +
            "$service/MapServer/tile/" +
            "${tile.zoom}/${tile.y}/${tile.x}"
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "IbizaCompanion/1.0")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "tile HTTP ${response.code} $url")
                    null
                } else {
                    response.body?.bytes()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "tile download: ${e.message}")
            null
        }
    }

    // ------------------------------------------------------------------
    // Polilínea (formato Google/OSRM)
    // ------------------------------------------------------------------

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
