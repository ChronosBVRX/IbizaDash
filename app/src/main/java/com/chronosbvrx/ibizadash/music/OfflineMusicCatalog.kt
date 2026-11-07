package com.chronosbvrx.ibizadash.music

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

/**
 * Lector del catálogo offline generado por `MusicCatalog/build_offline_catalog.py`.
 *
 * El launcher sólo necesita llamar:
 *
 *   OfflineMusicCatalog.initialize(context)
 *   val match = OfflineMusicCatalog.lookup(artist, title)
 *   OfflineMusicCatalog.loadCover(match) { bitmap -> ... }
 *
 * Ruta por defecto (sin permisos, API 27):
 *   /sdcard/Android/data/com.chronosbvrx.ibizadash/files/music_catalog/
 *
 * No está conectado a la UI todavía; es el módulo receptor listo para usarse.
 */
object OfflineMusicCatalog {

    private const val TAG = "OfflineMusicCatalog"
    private const val DB_NAME = "catalog.db"
    private const val COVERS_DIR = "covers"
    private const val SCORE_THRESHOLD = 0.50f

    @Volatile
    private var database: SQLiteDatabase? = null

    @Volatile
    private var coversDir: File? = null

    @Volatile
    private var ready = false

    private val mainHandler = Handler(Looper.getMainLooper())

    var catalogDir: File? = null
        private set

    val isReady: Boolean
        get() = ready

    /**
     * Abre `catalog.db` en modo sólo lectura.
     * @param directory carpeta que contiene `catalog.db` y `covers/`; si es null
     * se usa el almacenamiento externo propio de la app.
     */
    @Synchronized
    fun initialize(context: Context, directory: File? = null): Boolean {
        close()
        val candidates = if (directory != null) {
            listOf(directory)
        } else {
            listOf(
                File(context.getExternalFilesDir(null) ?: context.filesDir, "music_catalog"),
                File("/sdcard", "IbizaDashCatalog"),
                File("/sdcard", "MusicCatalog"),
                File("/storage/emulated/0", "IbizaDashCatalog"),
            )
        }
        val dir = candidates.firstOrNull { File(it, DB_NAME).isFile } ?: candidates.first()
        val dbFile = File(dir, DB_NAME)
        if (!dbFile.isFile) {
            Log.w(TAG, "No existe ${dbFile.absolutePath}")
            return false
        }
        return try {
            val opened = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            val hasTracks = opened.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name='tracks' LIMIT 1",
                null,
            ).use { it.moveToFirst() }
            if (!hasTracks) {
                opened.close()
                Log.w(TAG, "La base no tiene tabla tracks")
                return false
            }
            database = opened
            coversDir = File(dir, COVERS_DIR)
            catalogDir = dir
            ready = true
            Log.i(TAG, "Catálogo abierto: ${dbFile.absolutePath}")
            true
        } catch (error: Throwable) {
            Log.e(TAG, "No se pudo abrir ${dbFile.absolutePath}", error)
            false
        }
    }

    /**
     * Busca la carátula para lo que manda AVRCP.
     * Es seguro llamarlo desde un hilo de fondo; no descarga nada.
     */
    fun lookup(artist: String?, title: String?): CatalogMatch? {
        if (!ready) return null
        var rawTitle = title.orEmpty().trim()
        if (rawTitle.isEmpty()) return null
        var rawArtist = artist.orEmpty().trim()
        if (isUnknownArtist(rawArtist) || rawArtist.equals(rawTitle, ignoreCase = true)) {
            rawArtist = ""
        }

        var artistClean = TextNormalizer.artist(rawArtist)
        var titleClean = TextNormalizer.title(rawTitle)
        if (titleClean.isEmpty()) return null

        // AVRCP a veces manda "Artista - Título" en el campo Title.
        if (artistClean.isEmpty()) {
            val separator = rawTitle.indexOf(" - ")
            if (separator > 0) {
                val left = TextNormalizer.artist(rawTitle.substring(0, separator))
                val right = TextNormalizer.title(rawTitle.substring(separator + 3))
                if (left.isNotEmpty() && right.isNotEmpty()) {
                    val splitMatch = findBest(left, right)
                    if (splitMatch != null) return splitMatch
                }
            }
        }

        val best = findBest(artistClean, titleClean)
        if (best != null) return best

        // Último recurso: el título pudo venir con ruido distinto al del catálogo.
        if (titleClean.length >= 6) {
            return searchContains(titleClean, artistClean)
        }
        return null
    }

    /** Devuelve el archivo de portada o null si el match no tiene carátula. */
    fun coverFile(match: CatalogMatch?): File? {
        if (match == null) return null
        val dir = coversDir ?: return null
        val file = File(dir, match.coverFile)
        return if (file.isFile) file else null
    }

    /** Carga la portada en un hilo de fondo y entrega el Bitmap en el hilo principal. */
    fun loadCover(match: CatalogMatch?, callback: (Bitmap?) -> Unit) {
        val file = coverFile(match)
        if (file == null) {
            mainHandler.post { callback(null) }
            return
        }
        CoverCache.load(file, callback)
    }

    fun clearCoverCache() {
        CoverCache.clear()
    }

    fun stats(): CatalogStats? {
        val db = database ?: return null
        return try {
            val values = mutableMapOf<String, String>()
            db.rawQuery("SELECT key, value FROM catalog_meta", null).use { cursor ->
                while (cursor.moveToNext()) {
                    values[cursor.getString(0)] = cursor.getString(1)
                }
            }
            CatalogStats(
                albums = values["album_count"]?.toIntOrNull() ?: 0,
                tracks = values["track_count"]?.toIntOrNull() ?: 0,
                covers = values["cover_count"]?.toIntOrNull() ?: 0,
                generatedAt = values["generated_at"].orEmpty(),
                schemaVersion = values["schema_version"]?.toIntOrNull() ?: 0,
            )
        } catch (error: Throwable) {
            Log.w(TAG, "No se pudo leer catalog_meta: ${error.message}")
            null
        }
    }

    @Synchronized
    fun close() {
        ready = false
        database?.close()
        database = null
        coversDir = null
        catalogDir = null
    }

    // -----------------------------------------------------------------------
    // Búsqueda y puntuación
    // -----------------------------------------------------------------------

    private fun findBest(artistClean: String, titleClean: String): CatalogMatch? {
        val exactArtistAndTitle = query(
            """
            SELECT t.track_id, t.album_id, t.artist_clean, t.title_clean,
                   a.album_clean, a.cover_file
            FROM tracks t JOIN albums a ON a.album_id = t.album_id
            WHERE t.artist_clean = ? AND t.title_clean = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(artistClean, titleClean),
        ).firstOrNull()
        if (exactArtistAndTitle != null) {
            return exactArtistAndTitle.copy(score = 1.0f)
        }

        val exactTitle = query(
            """
            SELECT t.track_id, t.album_id, t.artist_clean, t.title_clean,
                   a.album_clean, a.cover_file
            FROM tracks t JOIN albums a ON a.album_id = t.album_id
            WHERE t.title_clean = ?
            LIMIT 25
            """.trimIndent(),
            arrayOf(titleClean),
        )
        bestFrom(exactTitle, artistClean, base = 0.55f)?.let { return it }

        if (titleClean.length >= 4) {
            val prefix = query(
                """
                SELECT t.track_id, t.album_id, t.artist_clean, t.title_clean,
                       a.album_clean, a.cover_file
                FROM tracks t JOIN albums a ON a.album_id = t.album_id
                WHERE t.title_clean LIKE ? ESCAPE '\'
                LIMIT 25
                """.trimIndent(),
                arrayOf("${escapeLike(titleClean)}%"),
            )
            bestFrom(prefix, artistClean, base = 0.50f)?.let { return it }
        }
        return null
    }

    private fun searchContains(titleClean: String, artistClean: String): CatalogMatch? {
        val candidates = query(
            """
            SELECT t.track_id, t.album_id, t.artist_clean, t.title_clean,
                   a.album_clean, a.cover_file
            FROM tracks t JOIN albums a ON a.album_id = t.album_id
            WHERE t.title_clean LIKE ? ESCAPE '\'
            LIMIT 40
            """.trimIndent(),
            arrayOf("%${escapeLike(titleClean)}%"),
        )
        return bestFrom(candidates, artistClean, base = 0.45f)
    }

    private fun bestFrom(
        candidates: List<CatalogMatch>,
        artistClean: String,
        base: Float,
    ): CatalogMatch? {
        var best: CatalogMatch? = null
        var bestScore = 0f
        for (candidate in candidates) {
            val artistScore = if (artistClean.isEmpty()) {
                0f
            } else {
                artistSimilarity(artistClean, candidate.artistClean)
            }
            // Con artista presente, no aceptar una portada de otro artista
            // sólo porque el título coincide.
            if (artistClean.isNotEmpty() && artistScore <= 0f) continue
            val score = (base + (1f - base) * artistScore).coerceIn(0f, 1f)
            if (score > bestScore) {
                bestScore = score
                best = candidate.copy(score = score)
            }
        }
        return if (best != null && bestScore >= SCORE_THRESHOLD) best else null
    }

    private fun artistSimilarity(left: String, right: String): Float {
        if (left == right) return 1f
        if (left.isEmpty() || right.isEmpty()) return 0f
        if (left.contains(right) || right.contains(left)) return 0.85f
        val leftTokens = left.split(' ').filter { it.isNotBlank() }.toSet()
        val rightTokens = right.split(' ').filter { it.isNotBlank() }.toSet()
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0f
        val common = leftTokens.intersect(rightTokens).size
        val union = leftTokens.union(rightTokens).size
        return (common.toFloat() / union.toFloat()) * 0.8f
    }

    private fun query(sql: String, args: Array<String>): List<CatalogMatch> {
        val db = database ?: return emptyList()
        return try {
            db.rawQuery(sql, args).use { cursor -> readRows(cursor) }
        } catch (error: Throwable) {
            Log.w(TAG, "Consulta falló: ${error.message}")
            emptyList()
        }
    }

    private fun readRows(cursor: Cursor): List<CatalogMatch> {
        val rows = ArrayList<CatalogMatch>(cursor.count.coerceAtMost(40))
        while (cursor.moveToNext()) {
            val albumId = cursor.getLong(1)
            if (albumId <= 0L) continue
            rows.add(
                CatalogMatch(
                    trackId = cursor.getLong(0),
                    albumId = albumId,
                    artistClean = cursor.getString(2).orEmpty(),
                    titleClean = cursor.getString(3).orEmpty(),
                    albumClean = cursor.getString(4).orEmpty(),
                    coverFile = cursor.getString(5).orEmpty(),
                    score = 0f,
                )
            )
        }
        return rows
    }

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun isUnknownArtist(value: String): Boolean {
        val clean = value.trim().lowercase()
        return clean.isEmpty() ||
            clean == "unknown" ||
            clean == "unknown artist" ||
            clean == "desconocido" ||
            clean == "artista desconocido" ||
            clean == "<unknown>" ||
            clean == "various artists" ||
            clean == "artist" ||
            clean == "null"
    }
}
