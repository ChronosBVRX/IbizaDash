package com.chronosbvrx.ibizadash.map

import android.content.Context
import android.graphics.Color
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.modules.TileWriter
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Capa de teselas con acceso a su caché en memoria. MapTileBridge la usa para
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

    private var currentStyle: MapStyle? = null
    private val refOverlays =
        java.util.WeakHashMap<MapView, CacheAwareTilesOverlay>()

    /**
     * Presupuesto de RAM del mapa. El estéreo tiene 2 GB y el Cortex-A7 no
     * aguanta el caché por defecto de osmdroid (40 teselas + overshoot).
     * 16 + 2 mantiene el encuadre de 1280×720 sin crecer de más.
     */
    fun configureRamCaps() {
        try {
            val cfg = Configuration.getInstance()
            cfg.setCacheMapTileCount(16)
            cfg.setCacheMapTileOvershoot(2)
            cfg.setTileFileSystemCacheMaxBytes(320L * 1024L * 1024L)
            cfg.setTileFileSystemCacheTrimBytes(280L * 1024L * 1024L)
        } catch (_: Exception) {
        }
    }

    fun configure(
        context: Context,
        map: MapView,
        style: MapStyle = MapStyles.current(context)
    ): CacheAwareTilesOverlay? {
        configureRamCaps()
        currentStyle = style
        // En Android 10+ osmdroid usa por defecto una caché SQL e ignora el
        // árbol de teselas. El módulo offline y el teléfono escriben archivos,
        // así que se fuerza el proveedor de sistema de archivos.
        installBaseProvider(context, map, style)
        map.setUseDataConnection(false)
        map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(false)
        map.setTilesScaledToDpi(true)
        map.setMapOrientation(0f)
        map.controller.setZoom(DEFAULT_ZOOM)
        map.controller.setCenter(MORELIA)

        // Sin cuadrícula de "cargando": si falta una tesela se ve la tarjeta
        // del panel, no un tablero gris que parece un fallo de la app.
        clearLoadingGrid(map)

        installRefLayer(context, map, style)

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
        return refOverlays[map]
    }

    /**
     * Cambia el estilo activo sin tocar la ruta, los pines ni el puck.
     * Devuelve la capa de etiquetas (o null si el estilo las trae incrustadas).
     */
    fun applyStyle(context: Context, map: MapView, style: MapStyle): CacheAwareTilesOverlay? {
        currentStyle = style
        installBaseProvider(context, map, style)
        clearLoadingGrid(map)
        installRefLayer(context, map, style)
        map.setUseDataConnection(false)
        map.invalidate()
        return refOverlays[map]
    }

    private fun installBaseProvider(context: Context, map: MapView, style: MapStyle) {
        val source = StyledTileSource(style, StyledTileSource.Layer.BASE)
        val provider = MapTileProviderBasic(context, source, TileWriter())
        map.setTileProvider(provider)
        map.setTileSource(source)
        map.setUseDataConnection(false)
    }

    private fun installRefLayer(context: Context, map: MapView, style: MapStyle) {
        refOverlays.remove(map)?.let { old ->
            try {
                old.onDetach(map)
            } catch (_: Exception) {
            }
            try {
                map.overlays.remove(old)
            } catch (_: Exception) {
            }
        }
        if (!style.hasRef) return
        try {
            val source = StyledTileSource(style, StyledTileSource.Layer.REF)
            val provider = MapTileProviderBasic(context, source, TileWriter())
            val overlay = CacheAwareTilesOverlay(provider, context)
            overlay.setUseDataConnection(false)
            overlay.setLoadingBackgroundColor(Color.TRANSPARENT)
            overlay.setLoadingLineColor(Color.TRANSPARENT)
            // Debajo del copyright, la ruta y el puck; encima de la base.
            map.overlays.add(0, overlay)
            refOverlays[map] = overlay
        } catch (_: Exception) {
        }
    }

    private fun clearLoadingGrid(map: MapView) {
        try {
            map.overlayManager.tilesOverlay.setLoadingBackgroundColor(Color.TRANSPARENT)
            map.overlayManager.tilesOverlay.setLoadingLineColor(Color.TRANSPARENT)
        } catch (_: Exception) {
        }
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

    fun activeStyle(): MapStyle? = currentStyle

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
