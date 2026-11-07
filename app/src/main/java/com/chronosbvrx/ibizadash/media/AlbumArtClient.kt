package com.chronosbvrx.ibizadash.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import com.chronosbvrx.ibizadash.Prefs
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Album art for A2DP tracks. The vendor Bluetooth stack only transfers text
 * (title/artist/album), so the cover is resolved online by artist+album and
 * cached on disk. Uses the public iTunes Search API (no key, no account).
 *
 * Network and decoding run on a single background thread; callbacks are posted
 * to the main thread. A newer request invalidates an older one.
 */
object AlbumArtClient {

    private const val TAG = "AlbumArt"
    private const val MAX_MEMORY_BYTES = 12 * 1024 * 1024
    private const val COVER_SIZE = 600

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val memory = object : LruCache<String, Bitmap>(MAX_MEMORY_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val currentKey = AtomicReference<String?>(null)
    private val misses = HashSet<String>()

    fun load(context: Context, track: NowPlaying, onLoaded: (Bitmap?) -> Unit) {
        if (!Prefs.onlineCovers(context)) return
        if (track.artwork != null) return
        val key = keyFor(track)
        if (key.isBlank() || misses.contains(key)) return

        memory.get(key)?.let {
            onLoaded(it)
            return
        }

        val file = File(cacheDir(context), "$key.jpg")
        if (file.exists()) {
            executor.execute {
                val bitmap = decode(file)
                if (bitmap != null) {
                    memory.put(key, bitmap)
                    main.post { onLoaded(bitmap) }
                } else {
                    file.delete()
                    fetch(context, track, key, file, onLoaded)
                }
            }
            return
        }

        currentKey.set(key)
        executor.execute { fetch(context, track, key, file, onLoaded) }
    }

    private fun fetch(
        context: Context,
        track: NowPlaying,
        key: String,
        file: File,
        onLoaded: (Bitmap?) -> Unit
    ) {
        if (currentKey.get() != key) return
        val url = searchCoverUrl(track)
        if (url == null) {
            misses.add(key)
            Log.i(TAG, "no cover for '${track.artist}' / '${track.album.ifBlank { track.title }}'")
            return
        }
        val bitmap = download(url)
        if (bitmap == null) {
            Log.w(TAG, "download failed: $url")
            return
        }
        memory.put(key, bitmap)
        runCatching {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
        }
        Log.i(TAG, "cover ok ${bitmap.width}x${bitmap.height} for '$key'")
        if (currentKey.get() == key) {
            main.post { onLoaded(bitmap) }
        }
    }

    private fun searchCoverUrl(track: NowPlaying): String? {
        val album = albumOf(track)
        val term = listOf(track.artist, album)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (term.isBlank()) return null

        queryCover(term, "song")?.let { return it }
        return queryCover(term, "album")
    }

    private fun albumOf(track: NowPlaying): String {
        val album = track.album.trim()
        return if (album.isBlank() ||
            album.equals("unavailable", ignoreCase = true) ||
            album.equals("unknown", ignoreCase = true)
        ) {
            track.title
        } else {
            album
        }
    }

    private fun queryCover(term: String, entity: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val encoded = URLEncoder.encode(term, "UTF-8")
            val url = URL(
                "https://itunes.apple.com/search?term=$encoded&entity=$entity" +
                    "&limit=1&country=MX"
            )
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.setRequestProperty("User-Agent", "IbizaDash/1.0")
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val results = JSONObject(body).optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            val artwork = results.getJSONObject(0).optString("artworkUrl100")
            if (artwork.isBlank()) return null
            artwork.replace("100x100bb", "${COVER_SIZE}x${COVER_SIZE}bb")
        } catch (e: Exception) {
            Log.w(TAG, "search: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun download(url: String): Bitmap? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "IbizaDash/1.0")
            if (connection.responseCode !in 200..299) return null
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeStream(connection.inputStream, null, options)
        } catch (e: Exception) {
            Log.w(TAG, "download: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun decode(file: File): Bitmap? {
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    private fun keyFor(track: NowPlaying): String {
        val raw = listOf(track.artist, albumOf(track))
            .joinToString("|")
            .lowercase()
            .trim()
        if (raw.isBlank() || raw == "|") return ""
        return Integer.toHexString(raw.hashCode())
    }

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, "covers").apply { mkdirs() }
}
