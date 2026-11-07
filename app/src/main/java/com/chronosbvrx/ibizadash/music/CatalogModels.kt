package com.chronosbvrx.ibizadash.music

/** Coincidencia devuelta por [OfflineMusicCatalog.lookup]. */
data class CatalogMatch(
    val trackId: Long,
    val albumId: Long,
    val artistClean: String,
    val titleClean: String,
    val albumClean: String,
    val coverFile: String,
    val score: Float,
)

/** Resumen leído de `catalog_meta`; útil para Ajustes/diagnóstico. */
data class CatalogStats(
    val albums: Int,
    val tracks: Int,
    val covers: Int,
    val generatedAt: String,
    val schemaVersion: Int,
)
