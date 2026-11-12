package com.chronosbvrx.ibizacompanion.map

import android.content.Context
import com.chronosbvrx.ibizacompanion.data.CompanionPrefs

/**
 * Catálogo de estilos raster que el teléfono puede descargar y enviar al
 * estéreo. Los `id` viajan por el puente TCP (`style` en `TILE_REQ` y
 * `TILE_BATCH`) y coinciden con `MapStyles` de IbizaDash.
 *
 * La carpeta local (`folder`) imita la de osmdroid del estéreo
 * (`osmdroid/tiles/<folder>/{z}/{x}/{y}.tile`) para poder leer la caché ya
 * descargada antes de pedir la tesela a la red.
 */
data class PhoneMapStyle(
    val id: String,
    val label: String,
    val folder: String,
    val urlTemplate: String,
    val refUrlTemplate: String? = null,
    /** PNG opaco: el estéreo lo decodifica en RGB_565. */
    val opaqueBase: Boolean = true,
    val attribution: String = ""
) {
    val supportsRef: Boolean get() = refUrlTemplate != null

    fun url(layer: String, z: Int, x: Int, y: Int): String {
        val template = if (layer == "ref") refUrlTemplate ?: urlTemplate else urlTemplate
        return template
            .replace("{z}", z.toString())
            .replace("{x}", x.toString())
            .replace("{y}", y.toString())
    }
}

object MapStyleCatalog {
    private const val ESRI_BASE =
        "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}"
    private const val ESRI_REF =
        "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile/{z}/{y}/{x}"

    val ESRI_DARK = PhoneMapStyle(
        id = "esri_dark",
        label = "Esri Dark",
        folder = "EsriDark",
        urlTemplate = ESRI_BASE,
        refUrlTemplate = ESRI_REF,
        attribution = "Esri, HERE, Garmin, © OpenStreetMap"
    )

    val CARTO_DARK = PhoneMapStyle(
        id = "carto_dark",
        label = "Slate Dark",
        folder = "SlateDark",
        // Carto exige API key desde 2025 (marca de agua en las teselas).
        // Misma base oscura de Esri con paleta azul pizarra en el estéreo.
        urlTemplate = ESRI_BASE,
        refUrlTemplate = ESRI_REF,
        attribution = "Esri, HERE, Garmin, © OpenStreetMap"
    )

    /**
     * Misma base oscura de Esri que `esri_dark`, pero el estéreo le aplica la
     * paleta teal de Waze. La caché va en su propia carpeta para poder
     * alternar estilos sin mezclar filtros.
     */
    val WAZE_NIGHT = PhoneMapStyle(
        id = "waze_night",
        label = "Waze Night",
        folder = "WazeNight",
        urlTemplate = ESRI_BASE,
        refUrlTemplate = ESRI_REF,
        attribution = "Esri, HERE, Garmin, © OpenStreetMap"
    )

    val ALL: List<PhoneMapStyle> = listOf(ESRI_DARK, CARTO_DARK, WAZE_NIGHT)

    private val byId = ALL.associateBy { it.id }

    fun byId(id: String?): PhoneMapStyle =
        if (id.isNullOrBlank()) ESRI_DARK else byId[id] ?: ESRI_DARK

    fun current(context: Context): PhoneMapStyle =
        byId(CompanionPrefs.mapStyle(context))

    fun select(context: Context, style: PhoneMapStyle) {
        CompanionPrefs.setMapStyle(context, style.id)
    }
}
