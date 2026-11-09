package com.chronosbvrx.ibizacompanion.data

import android.content.Context
import android.util.Base64
import android.util.Log
import com.chronosbvrx.ibizacompanion.BridgeLink
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
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
    // El enlace se comparte con el audio A2DP: se envía a un ritmo suave.
    private const val TARGET_BYTES_PER_SEC = 400_000L

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
            val png = readLocalTile(context, tile) ?: downloadTile(tile)
            if (png == null || png.size < 40) {
                failed++
                onProgress(index + 1, total)
                continue
            }
            val msg = JSONObject()
                .put("type", "TILE_BATCH")
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
            pace(png.size)
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

    /** Duerme lo justo para no pasar de [TARGET_BYTES_PER_SEC] (máx. 150 ms). */
    private fun pace(bytes: Int) {
        val ms = (bytes * 1000L / TARGET_BYTES_PER_SEC).coerceIn(0L, 150L)
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

    private fun readLocalTile(context: Context, tile: TileKey): ByteArray? {
        val ext = context.getExternalFilesDir(null)
        val candidates = ArrayList<File>(3)
        if (ext != null) {
            candidates.add(File(ext, "offline-tiles/${tile.zoom}/${tile.x}/${tile.y}.png"))
            candidates.add(File(ext, "osmdroid/tiles/EsriDark/${tile.zoom}/${tile.x}/${tile.y}.png"))
        }
        candidates.add(File(context.filesDir, "offline-tiles/${tile.zoom}/${tile.x}/${tile.y}.png"))
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
     * Esri usa /tile/{z}/{y}/{x}; el nombre local de osmdroid es {z}/{x}/{y}.png.
     */
    private fun downloadTile(tile: TileKey): ByteArray? {
        val url = "https://server.arcgisonline.com/ArcGIS/rest/services/" +
            "Canvas/World_Dark_Gray_Base/MapServer/tile/" +
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
