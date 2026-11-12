package com.chronosbvrx.ibizadash.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.Drawable
import com.chronosbvrx.ibizadash.Prefs
import org.osmdroid.tileprovider.ReusableBitmapDrawable
import org.osmdroid.tileprovider.tilesource.BitmapTileSourceBase
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Catálogo de estilos raster del estéreo.
 *
 * Cada estilo tiene su propia carpeta de caché (el nombre de la fuente de
 * osmdroid) para que los mapas descargados de un estilo no pisen los de otro.
 * El id (`esri_dark`, `carto_dark`, `waze_night`) es el que viaja por el puente
 * TCP en `TILE_REQ` / `TILE_BATCH`.
 */
enum class TileUrlOrder { ZYX, ZXY }

data class MapStyle(
    val id: String,
    val label: String,
    val shortLabel: String,
    /** Carpeta de osmdroid y nombre de la caché en disco. */
    val folder: String,
    val baseUrl: String,
    val order: TileUrlOrder = TileUrlOrder.ZXY,
    /** Segunda capa con nombres de calles (solo Esri la necesita). */
    val refUrl: String? = null,
    /** Las teselas base son opacas: se decodifican en RGB_565. */
    val opaqueBase: Boolean = true,
    val maxZoom: Int = 19,
    /** Filtro fuera de NAV (sutil). */
    val baseFilter: FloatArray? = null,
    /** Filtro en NAV (paleta de navegación). */
    val navFilter: FloatArray? = null
) {
    val hasRef: Boolean get() = refUrl != null
}

object MapStyles {

    /**
     * Matriz del modo NAV para Esri: contraste + tinte azul petróleo. Es la
     * misma que usaba MainActivity antes de mover el catálogo aquí.
     */
    private val ESRI_NAV = floatArrayOf(
        1.24f * 0.92f, 0f, 0f, 0f, -34.6f,
        0f, 1.24f, 0f, 0f, -28.6f,
        0f, 0f, 1.24f * 1.12f, 0f, -18.6f,
        0f, 0f, 0f, 1f, 0f
    )

    /** Slate Dark: azul pizarra sobre la base oscura de Esri. */
    private val CARTO_BASE = floatArrayOf(
        1.10f, 0f, 0f, 0f, 2f,
        0f, 1.12f, 0f, 0f, 4f,
        0f, 0f, 1.22f, 0f, 10f,
        0f, 0f, 0f, 1f, 0f
    )
    private val CARTO_NAV = floatArrayOf(
        1.20f, 0f, 0f, 0f, -14f,
        0f, 1.22f, 0f, 0f, -12f,
        0f, 0f, 1.34f, 0f, 4f,
        0f, 0f, 0f, 1f, 0f
    )

    /**
     * Waze Night: Esri Dark sin etiquetas + paleta verde/cian.
     * Carto dejó de servir teselas gratis (marca de agua "API KEY REQUIRED"),
     * así que los tres estilos usan la fuente Esri gratuita y se diferencian
     * por paleta y por la capa de etiquetas.
     */
    private val WAZE_BASE = floatArrayOf(
        1.00f, 0f, 0f, 0f, 2f,
        0f, 1.10f, 0f, 0f, 4f,
        0f, 0f, 1.18f, 0f, 10f,
        0f, 0f, 0f, 1f, 0f
    )
    private val WAZE_NAV = floatArrayOf(
        1.10f, 0f, 0f, 0f, -8f,
        0f, 1.24f, 0f, 0f, -4f,
        0f, 0f, 1.32f, 0f, 12f,
        0f, 0f, 0f, 1f, 0f
    )

    private const val ESRI_REF_URL =
        "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile/"

    private const val ESRI_DARK_URL =
        "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/"

    val ESRI_DARK = MapStyle(
        id = "esri_dark",
        label = "Esri Dark Gray",
        shortLabel = "Esri Dark",
        folder = "EsriDark",
        baseUrl = ESRI_DARK_URL,
        order = TileUrlOrder.ZYX,
        refUrl = ESRI_REF_URL,
        opaqueBase = true,
        // Esri no tiene teselas z17 en muchas zonas de México: devuelve
        // "Map data not yet available". z16 es el máximo fiable.
        maxZoom = 16,
        navFilter = ESRI_NAV
    )

    val CARTO_DARK = MapStyle(
        id = "carto_dark",
        label = "Slate Dark (azul pizarra)",
        shortLabel = "Slate Dark",
        folder = "SlateDark",
        // Carto exige API key desde 2025 (marca de agua en las teselas).
        // Se usa la misma base oscura de Esri, con etiquetas `ref` y una
        // paleta azul pizarra más clara para diferenciarla de Esri Dark.
        baseUrl = ESRI_DARK_URL,
        order = TileUrlOrder.ZYX,
        refUrl = ESRI_REF_URL,
        opaqueBase = true,
        maxZoom = 16,
        baseFilter = CARTO_BASE,
        navFilter = CARTO_NAV
    )

    val WAZE_NIGHT = MapStyle(
        id = "waze_night",
        label = "Waze Night (teal)",
        shortLabel = "Waze",
        folder = "WazeNight",
        baseUrl = ESRI_DARK_URL,
        order = TileUrlOrder.ZYX,
        refUrl = ESRI_REF_URL,
        opaqueBase = true,
        maxZoom = 16,
        baseFilter = WAZE_BASE,
        navFilter = WAZE_NAV
    )

    val ALL: List<MapStyle> = listOf(ESRI_DARK, CARTO_DARK, WAZE_NIGHT)

    private val byId = ALL.associateBy { it.id }
    private val byFolder = ALL.associateBy { it.folder }
    private val filterCache = ConcurrentHashMap<String, ColorMatrixColorFilter>()

    fun byId(id: String?): MapStyle =
        if (id.isNullOrBlank()) ESRI_DARK else byId[id] ?: ESRI_DARK

    /** Compatibilidad con `TILE_BATCH` viejos que no mandan `style`. */
    fun byFolder(folder: String?): MapStyle =
        if (folder.isNullOrBlank()) ESRI_DARK else byFolder[folder] ?: ESRI_DARK

    fun current(context: Context): MapStyle = byId(Prefs.mapStyle(context))

    fun select(context: Context, style: MapStyle) {
        Prefs.setMapStyle(context, style.id)
    }

    /** Filtro de color cacheado por estilo + modo. Sin alocación por frame. */
    fun colorFilter(style: MapStyle, nav: Boolean): ColorMatrixColorFilter? {
        val matrix = if (nav) style.navFilter ?: style.baseFilter else style.baseFilter
        if (matrix == null) return null
        val key = style.id + if (nav) ":nav" else ":base"
        return filterCache.getOrPut(key) { ColorMatrixColorFilter(ColorMatrix(matrix)) }
    }

    fun cacheFolder(context: Context, style: MapStyle): File {
        val base = File(context.getExternalFilesDir(null) ?: context.filesDir, "osmdroid/tiles")
        return File(base, style.folder)
    }

    data class CacheStats(val tiles: Int, val bytes: Long)

    /** Recorre la carpeta del estilo. Llamar desde IO: puede haber miles de archivos. */
    fun cacheStats(context: Context, style: MapStyle): CacheStats {
        val root = cacheFolder(context, style)
        if (!root.exists()) return CacheStats(0, 0L)
        var tiles = 0
        var bytes = 0L
        try {
            root.walkTopDown().forEach { file ->
                if (file.isFile &&
                    (file.extension.equals("tile", true) || file.extension.equals("png", true))
                ) {
                    tiles++
                    bytes += file.length()
                }
            }
        } catch (_: Exception) {
        }
        return CacheStats(tiles, bytes)
    }
}

/**
 * TileSource de un estilo. El `name` es el de la carpeta de caché, así que
 * Esri conserva la caché ya descargada (`EsriDark`) y los estilos nuevos
 * arrancan con su propia carpeta.
 */
class StyledTileSource(
    val style: MapStyle,
    val layer: Layer
) : OnlineTileSourceBase(
    if (layer == Layer.REF) "${style.folder}Ref" else style.folder,
    0,
    style.maxZoom,
    256,
    "",
    arrayOf(if (layer == Layer.REF) style.refUrl ?: style.baseUrl else style.baseUrl)
) {

    enum class Layer { BASE, REF }

    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return when (style.order) {
            TileUrlOrder.ZYX -> getBaseUrl() + z + "/" + y + "/" + x
            TileUrlOrder.ZXY -> getBaseUrl() + z + "/" + x + "/" + y + ".png"
        }
    }

    /**
     * Teselas base opacas en RGB_565: 128 KB en vez de 256 KB por bitmap.
     * La capa `ref` conserva ARGB_8888 porque usa transparencia.
     */
    override fun getDrawable(aFile: String): Drawable? {
        if (layer == Layer.REF || !style.opaqueBase) return super.getDrawable(aFile)
        return try {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
                inScaled = false
            }
            val bitmap = BitmapFactory.decodeFile(aFile, options)
                ?: throw BitmapTileSourceBase.LowMemoryException("No se pudo decodificar $aFile")
            ReusableBitmapDrawable(bitmap)
        } catch (e: OutOfMemoryError) {
            throw BitmapTileSourceBase.LowMemoryException(e)
        }
    }

    override fun getCopyrightNotice(): String =
        "Esri, HERE, Garmin, © OpenStreetMap contributors"
}
