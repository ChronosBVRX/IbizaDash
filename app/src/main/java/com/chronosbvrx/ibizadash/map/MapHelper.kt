package com.chronosbvrx.ibizadash.map

import android.content.Context
import android.graphics.Color
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.modules.TileWriter
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Mapa oscuro gratuito de Esri (World Dark Gray Canvas).
 * No requiere API key. Formato de tile: /tile/{z}/{y}/{x}
 */
class EsriDarkTileSource : OnlineTileSourceBase(
    "EsriDark",
    0,
    19,
    256,
    "",
    arrayOf(
        "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/"
    )
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return getBaseUrl() + z + "/" + y + "/" + x
    }

    override fun getCopyrightNotice(): String =
        "Esri, HERE, Garmin, © OpenStreetMap contributors"
}

/**
 * Capa de referencia de Esri: nombres de calles, colonias y POIs principales
 * en PNG transparente. Se dibuja ENCIMA del mapa base y DEBAJO de la ruta.
 * El teléfono es quien descarga estas teselas y las manda como `layer:"ref"`.
 */
class EsriDarkRefTileSource : OnlineTileSourceBase(
    "EsriDarkRef",
    0,
    19,
    256,
    "",
    arrayOf(
        "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile/"
    )
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return getBaseUrl() + z + "/" + y + "/" + x
    }

    override fun getCopyrightNotice(): String = ""
}

/**
 * TilesOverlay con acceso a su caché en memoria. MapTileBridge lo usa para
 * forzar la relectura del disco cuando el teléfono acaba de mandar teselas.
 */
class CacheAwareTilesOverlay(
    private val provider: MapTileProviderBase,
    context: Context
) : TilesOverlay(provider, context) {
    fun clearTileCache() {
        try {
            provider.clearTileCache()
        } catch (_: Exception) {
        }
    }
}

object MapHelper {
    val MORELIA = GeoPoint(19.7060, -101.1950)
    val DEFAULT_ZOOM = 16.0

    fun configure(context: Context, map: MapView) {
        val source = EsriDarkTileSource()
        // En Android 10+ osmdroid usa por defecto una caché SQL e ignora el
        // árbol de teselas. El módulo offline y el teléfono escriben archivos,
        // así que se fuerza el proveedor de sistema de archivos.
        val provider = MapTileProviderBasic(
            context,
            source,
            TileWriter()
        )
        map.setTileProvider(provider)
        map.setTileSource(source)
        // El estéreo no descarga mapas: usa la caché y las teselas que el
        // teléfono envía por Wi-Fi Direct (TILE_BATCH).
        map.setUseDataConnection(false)
        map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(false)
        map.setTilesScaledToDpi(true)
        map.setMapOrientation(0f)
        map.controller.setZoom(DEFAULT_ZOOM)
        map.controller.setCenter(MORELIA)

        // Sin cuadrícula de "cargando": si falta una tesela se ve la tarjeta
        // del panel, no un tablero gris que parece un fallo de la app.
        try {
            map.overlayManager.tilesOverlay.setLoadingBackgroundColor(Color.TRANSPARENT)
            map.overlayManager.tilesOverlay.setLoadingLineColor(Color.TRANSPARENT)
        } catch (_: Exception) {
        }

        // Etiquetas ligeras encima del base. Comparten caché de disco por
        // nombre de fuente (osmdroid/tiles/EsriDarkRef). La ruta y el puck se
        // añaden después, así que quedan por encima.
        try {
            val refSource = EsriDarkRefTileSource()
            val refProvider = MapTileProviderBasic(context, refSource, TileWriter())
            val refOverlay = CacheAwareTilesOverlay(refProvider, context)
            refOverlay.setUseDataConnection(false)
            refOverlay.setLoadingBackgroundColor(Color.TRANSPARENT)
            refOverlay.setLoadingLineColor(Color.TRANSPARENT)
            map.overlays.add(refOverlay)
        } catch (_: Exception) {
        }

        map.overlays.add(
            CopyrightOverlay(context).apply {
                setAlignBottom(true)
                setAlignRight(false)
                setOffset(12, 110)
                setTextColor(Color.parseColor("#8A94A6"))
                setTextSize(10)
                setCopyrightNotice("Esri, HERE, Garmin, © OpenStreetMap contributors")
            }
        )
    }

    fun addLocation(context: Context, map: MapView): MyLocationNewOverlay {
        val overlay = MyLocationNewOverlay(GpsMyLocationProvider(context), map)
        overlay.enableMyLocation()
        overlay.enableFollowLocation()
        map.overlays.add(overlay)
        return overlay
    }

    /** Vacía la caché en memoria del base y de la capa de etiquetas. */
    fun clearTileCaches(map: MapView) {
        try {
            map.tileProvider?.clearTileCache()
        } catch (_: Exception) {
        }
        try {
            map.overlays.filterIsInstance<CacheAwareTilesOverlay>()
                .forEach { it.clearTileCache() }
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------
    // Teselas del viewport (TILE_REQ)
    // ------------------------------------------------------------------

    fun lonToTileX(lon: Double, zoom: Int): Int {
        val n = 1 shl zoom
        return (((lon + 180.0) / 360.0) * n).toInt().coerceIn(0, n - 1)
    }

    fun latToTileY(lat: Double, zoom: Int): Int {
        val n = 1 shl zoom
        val rad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        val y = ((1.0 - kotlin.math.ln(kotlin.math.tan(rad) + 1.0 / kotlin.math.cos(rad)) /
            Math.PI) / 2.0 * n).toInt()
        return y.coerceIn(0, n - 1)
    }

    // Standard Google encoded polyline decoder (no external deps).
    fun decodePolyline(encoded: String): List<GeoPoint> {
        val poly = ArrayList<GeoPoint>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            poly.add(GeoPoint(lat / 1E5, lng / 1E5))
        }
        return poly
    }

    // Tiny test polyline around Morelia for the DEBUG_ROUTE_SET broadcast without payload.
    fun encodeTestPolyline(): String {
        // Hand-crafted minimal encoded polyline (Morelia area). Decodes to ~3 points.
        return "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
    }
}
