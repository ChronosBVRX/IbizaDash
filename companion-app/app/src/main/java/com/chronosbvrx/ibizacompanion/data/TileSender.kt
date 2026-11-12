package com.chronosbvrx.ibizacompanion.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import android.util.Log
import com.chronosbvrx.ibizacompanion.AudioCoexistence
import com.chronosbvrx.ibizacompanion.BridgeLink
import com.chronosbvrx.ibizacompanion.map.MapStyleCatalog
import com.chronosbvrx.ibizacompanion.map.PhoneMapStyle
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.tan

/**
 * EnvÃ­a al estÃ©reo las teselas del estilo elegido.
 *
 * Fuente de cada tesela, en orden:
 *  1. Pack offline del telÃ©fono (`offline-tiles/{z}/{x}/{y}.png`, solo Esri).
 *  2. CachÃ© de osmdroid del telÃ©fono (`osmdroid/tiles/<style>/{z}/{x}/{y}.tile`).
 *  3. Descarga online del proveedor del estilo.
 *
 * Por el tamaÃ±o (cientos de KB) sÃ³lo tiene sentido sobre Wi-Fi; si el enlace
 * Wi-Fi se cae, la transferencia se detiene en lugar de saturar Bluetooth.
 * El ritmo lo decide [AudioCoexistence]: 250 KB/s sin mÃºsica, 60 KB/s con
 * mÃºsica (para no ahogar el A2DP del chip de 2,4 GHz).
 */
object TileSender {
    private const val TAG = "TileSender"
    private const val MAX_TILES = 420
    private const val MAX_BYTES = 8L * 1024L * 1024L

    private const val LAYER_BASE = "base"
    private const val LAYER_REF = "ref"

    /** Peticiones del viewport: pequeÃ±as, para no ahogar el enlace. */
    private const val VIEWPORT_MAX_BASE = 36
    private const val VIEWPORT_MAX_REF = 20
    private const val VIEWPORT_MAX_BYTES_BASE = 2L * 1024L * 1024L
    private const val VIEWPORT_MAX_BYTES_REF = 1_200_000L

    /** Evita que dos TILE_REQ seguidos se pisen en el socket. */
    private val viewportBusy = AtomicBoolean(false)

    /**
     * Cola de un solo hilo: base y etiquetas del mismo encuadre se mandan en
     * orden, sin dos rÃ¡fagas simultÃ¡neas compitiendo con el A2DP.
     */
    private val viewportQueue = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "TileViewport").apply { isDaemon = true }
    }

    @Volatile
    private var lastViewportKey = ""

    @Volatile
    private var lastViewportAtMs = 0L

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
     * Corredor de una ruta. Por defecto z14 (visiÃ³n general), z15 (centros y
     * anillo) y z16 (calle). `extraZooms` permite pedir z15â€“z17 desde el botÃ³n
     * "Descargar zona" sin duplicar cÃ³digo.
     */
    fun sendRouteTiles(
        context: Context,
        encodedPolyline: String,
        styleId: String = MapStyleCatalog.current(context).id,
        zooms: IntArray = intArrayOf(14, 15, 16),
        onProgress: (sent: Int, total: Int) -> Unit = { _, _ -> }
    ): Result {
        val style = MapStyleCatalog.byId(styleId.ifBlank { MapStyleCatalog.current(context).id })
        val points = decodePolyline(encodedPolyline)
        if (points.size < 2) return Result(0, 0, 0, 0)
        if (!BridgeLink.isWifiActive()) {
            Log.w(TAG, "sin Wi-Fi activo; no se envÃ­an teselas")
            return Result(0, 0, 0, 0)
        }
        val tiles = corridorTiles(points, zooms)
        val result = transfer(context, tiles, style, LAYER_BASE, MAX_BYTES, onProgress)
        try {
            BridgeLink.send(
                JSONObject()
                    .put("type", "TILE_STATS")
                    .put("style", style.id)
                    .put("sent", result.sent)
                    .put("total", result.total)
            )
        } catch (_: Exception) {
        }
        Log.i(TAG, "ruta ${style.id}: enviadas=${result.sent}/${result.total} bytes=${result.bytes}")
        return result
    }

    /**
     * Zona alrededor de la ubicaciÃ³n actual (o de un punto) en z15â€“z17.
     * Pensada para el botÃ³n "Descargar zona": deja el mapa listo sin ruta.
     */
    fun sendAreaTiles(
        context: Context,
        lat: Double,
        lon: Double,
        styleId: String = MapStyleCatalog.current(context).id,
        radiusKm: Double = 2.5,
        zooms: IntArray = intArrayOf(15, 16, 17),
        onProgress: (sent: Int, total: Int) -> Unit = { _, _ -> }
    ): Result {
        val style = MapStyleCatalog.byId(styleId.ifBlank { MapStyleCatalog.current(context).id })
        if (!BridgeLink.isWifiActive()) return Result(0, 0, 0, 0)
        val tiles = LinkedHashSet<TileKey>()
        val dLat = radiusKm / 111.0
        val dLon = radiusKm / (111.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.2))
        for (z in zooms) {
            if (z !in 13..18) continue
            val x0 = lonToTileX(lon - dLon, z)
            val x1 = lonToTileX(lon + dLon, z)
            val y0 = latToTileY(lat + dLat, z)
            val y1 = latToTileY(lat - dLat, z)
            tiles.addAll(bboxTiles(z, x0, y0, x1, y1, MAX_TILES))
        }
        if (tiles.isEmpty()) return Result(0, 0, 0, 0)
        val result = transfer(
            context,
            tiles.toList().take(MAX_TILES),
            style,
            LAYER_BASE,
            MAX_BYTES,
            onProgress
        )
        try {
            BridgeLink.send(
                JSONObject()
                    .put("type", "TILE_STATS")
                    .put("style", style.id)
                    .put("sent", result.sent)
                    .put("total", result.total)
            )
        } catch (_: Exception) {
        }
        Log.i(TAG, "zona ${style.id}: enviadas=${result.sent}/${result.total} bytes=${result.bytes}")
        return result
    }

    /**
     * Teselas del encuadre que estÃ¡ viendo el estÃ©reo (TILE_REQ).
     *
     * Se piden al vuelo, con tope de cantidad y de bytes, y solo para el
     * zoom actual. `ref` es la capa de nombres de calles (solo Esri).
     */
    fun sendViewportTiles(
        context: Context,
        zoom: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        layer: String,
        styleId: String,
        onProgress: (sent: Int, total: Int) -> Unit = { _, _ -> }
    ): Result {
        val style = MapStyleCatalog.byId(styleId.ifBlank { MapStyleCatalog.current(context).id })
        if (!BridgeLink.isWifiActive()) return Result(0, 0, 0, 0)
        if (zoom !in 13..18) return Result(0, 0, 0, 0)
        val isRef = layer == LAYER_REF
        if (isRef && !style.supportsRef) return Result(0, 0, 0, 0)
        val cap = if (isRef) VIEWPORT_MAX_REF else VIEWPORT_MAX_BASE
        val byteCap = if (isRef) VIEWPORT_MAX_BYTES_REF else VIEWPORT_MAX_BYTES_BASE
        val tiles = bboxTiles(zoom, x0, y0, x1, y1, cap)
        if (tiles.isEmpty()) return Result(0, 0, 0, 0)
        if (!viewportBusy.compareAndSet(false, true)) {
            Log.i(TAG, "viewport $layer ocupado; se descarta este pedido")
            return Result(0, 0, 0, 0)
        }
        try {
            return transfer(context, tiles, style, layer, byteCap, onProgress)
        } finally {
            viewportBusy.set(false)
        }
    }

    /**
     * Encola un TILE_REQ sin bloquear el lector del socket. Descarta pedidos
     * idÃ©nticos repetidos (el estÃ©reo reintenta cada pocos segundos).
     */
    fun enqueueViewportTiles(
        context: Context,
        zoom: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        layer: String,
        styleId: String
    ) {
        val style = MapStyleCatalog.byId(styleId.ifBlank { MapStyleCatalog.current(context).id })
        // Un estilo sin capa `ref` no debe gastar enlace en etiquetas.
        if (layer == LAYER_REF && !style.supportsRef) return
        val key = "${style.id}:$layer:$zoom:$x0:$y0:$x1:$y1"
        val now = System.currentTimeMillis()
        if (key == lastViewportKey && now - lastViewportAtMs < 15_000L) return
        lastViewportKey = key
        lastViewportAtMs = now
        val app = context.applicationContext
        viewportQueue.execute {
            try {
                sendViewportTiles(app, zoom, x0, y0, x1, y1, layer, style.id)
            } catch (e: Exception) {
                Log.w(TAG, "viewport: ${e.message}")
            }
        }
    }

    // ------------------------------------------------------------------
    // Transferencia
    // ------------------------------------------------------------------

    private fun transfer(
        context: Context,
        tiles: List<TileKey>,
        style: PhoneMapStyle,
        layer: String,
        byteCap: Long,
        onProgress: (sent: Int, total: Int) -> Unit
    ): Result {
        var sent = 0
        var failed = 0
        var bytes = 0L
        val total = tiles.size
        for ((index, tile) in tiles.withIndex()) {
            if (!BridgeLink.isWifiActive()) {
                Log.w(TAG, "Wi-Fi perdido tras $sent teselas")
                break
            }
            // Con mÃºsica sonando no se descarga online (riega mÃ¡s Wi-Fi y
            // corta el A2DP): solo se envÃ­an teselas locales.
            val musicPlaying = AudioCoexistence.isMusicPlaying(context)
            val raw = readLocalTile(context, tile, style, layer)
                ?: if (musicPlaying) null else downloadTile(tile, style, layer)
            val png = if (raw != null) optimizePng(raw, style) else null
            if (png == null || png.size < 40) {
                failed++
                onProgress(index + 1, total)
                continue
            }
            val msg = JSONObject()
                .put("type", "TILE_BATCH")
                .put("layer", layer)
                .put("style", style.id)
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
            if (bytes > byteCap) break
            // Ritmo suave: deja aire al audio Bluetooth aunque el enlace sea 2,4 GHz.
            pace(context, png.size)
        }
        return Result(sent, total, failed, bytes)
    }

    private fun pace(context: Context, bytes: Int) {
        val ms = AudioCoexistence.paceMs(context, bytes.toLong())
        if (ms <= 0L) return
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
        }
    }

    /**
     * Quita el canal alfa cuando la tesela base es opaca. Los PNG de los
     * proveedores suelen ser RGBA aunque no usen transparencia; re-codificar
     * a RGB reduce el payload sin tocar la nitidez (no se usa JPEG, que
     * ensuciarÃ­a el texto de las calles).
     *
     * Solo se intenta con archivos grandes para no gastar CPU en cada tesela.
     */
    private fun optimizePng(bytes: ByteArray, style: PhoneMapStyle): ByteArray {
        if (!style.opaqueBase || bytes.size < 24_000) return bytes
        if (bytes.size < 26) return bytes
        // Firma PNG + color type del IHDR (offset 25): 6 = RGBA.
        if ((bytes[0].toInt() and 0xFF) != 0x89 ||
            (bytes[1].toInt() and 0xFF) != 0x50
        ) return bytes
        val colorType = bytes[25].toInt() and 0xFF
        if (colorType != 6) return bytes
        var bmp: Bitmap? = null
        var rgb: Bitmap? = null
        return try {
            bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
            // Muestreo de alfa: no hace falta recorrer 65 536 pÃ­xeles.
            val stepX = (bmp.width / 16).coerceAtLeast(1)
            val stepY = (bmp.height / 16).coerceAtLeast(1)
            var y = 0
            while (y < bmp.height) {
                var x = 0
                while (x < bmp.width) {
                    if (Color.alpha(bmp.getPixel(x, y)) < 250) return bytes
                    x += stepX
                }
                y += stepY
            }
            rgb = bmp.copy(Bitmap.Config.RGB_565, false) ?: return bytes
            val out = ByteArrayOutputStream()
            rgb.compress(Bitmap.CompressFormat.PNG, 100, out)
            val result = out.toByteArray()
            if (result.size in 1 until bytes.size) result else bytes
        } catch (_: Exception) {
            bytes
        } finally {
            try {
                bmp?.recycle()
            } catch (_: Exception) {
            }
            try {
                rgb?.recycle()
            } catch (_: Exception) {
            }
        }
    }

    // ------------------------------------------------------------------
    // ConstrucciÃ³n del corredor
    // ------------------------------------------------------------------

    private fun corridorTiles(points: List<Pair<Double, Double>>, zooms: IntArray): List<TileKey> {
        val wanted = zooms.toSet()
        val overview = LinkedHashSet<TileKey>()
        val centers = LinkedHashSet<TileKey>()
        val street = LinkedHashSet<TileKey>()
        val street17 = LinkedHashSet<TileKey>()
        val ring = LinkedHashSet<TileKey>()

        val step = max(1, points.size / 350)
        val streetStep = max(1, points.size / 90)
        var i = 0
        while (i < points.size) {
            val (lat, lon) = points[i]
            if (14 in wanted) {
                overview.add(TileKey(14, lonToTileX(lon, 14), latToTileY(lat, 14)))
            }
            val x15 = lonToTileX(lon, 15)
            val y15 = latToTileY(lat, 15)
            if (15 in wanted) {
                centers.add(TileKey(15, x15, y15))
                if (ring.size < MAX_TILES) {
                    for (dx in -1..1) {
                        for (dy in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            ring.add(TileKey(15, clampTile(x15 + dx, 15), clampTile(y15 + dy, 15)))
                        }
                    }
                }
            }
            if (16 in wanted) {
                street.add(TileKey(16, lonToTileX(lon, 16), latToTileY(lat, 16)))
            }
            i += step
        }
        if (17 in wanted) {
            var j = 0
            while (j < points.size) {
                val (lat, lon) = points[j]
                street17.add(TileKey(17, lonToTileX(lon, 17), latToTileY(lat, 17)))
                j += streetStep
            }
        }

        val out = LinkedHashSet<TileKey>()
        out.addAll(overview)
        out.addAll(centers)
        out.addAll(street)
        out.addAll(street17)
        out.addAll(ring)
        return out.take(MAX_TILES)
    }

    /** Caja del viewport â†’ teselas, ordenadas desde el centro hacia afuera. */
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

    private fun readLocalTile(
        context: Context,
        tile: TileKey,
        style: PhoneMapStyle,
        layer: String
    ): ByteArray? {
        val ext = context.getExternalFilesDir(null)
        val candidates = ArrayList<File>(6)
        // El pack offline solo trae el mapa base de Esri.
        if (layer != LAYER_REF && style.id == MapStyleCatalog.ESRI_DARK.id) {
            if (ext != null) {
                candidates.add(File(ext, "offline-tiles/${tile.zoom}/${tile.x}/${tile.y}.png"))
            }
            candidates.add(
                File(context.filesDir, "offline-tiles/${tile.zoom}/${tile.x}/${tile.y}.png")
            )
        }
        // osmdroid 6 guarda ".tile"; los packs antiguos, ".png".
        val folder = if (layer == LAYER_REF) "${style.folder}Ref" else style.folder
        val roots = ArrayList<File>(2)
        if (ext != null) roots.add(File(ext, "osmdroid/tiles/$folder"))
        roots.add(File(context.filesDir, "osmdroid/tiles/$folder"))
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

    private fun downloadTile(
        tile: TileKey,
        style: PhoneMapStyle,
        layer: String
    ): ByteArray? {
        val url = style.url(layer, tile.zoom, tile.x, tile.y)
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
    // PolilÃ­nea (formato Google/OSRM)
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
