package com.chronosbvrx.ibizadash.music

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import java.io.File
import java.util.concurrent.Executors

/**
 * Caché de carátulas pensada para el Cortex-A7 del estéreo:
 * - 300x300 en RGB_565 (≈176 KB por portada).
 * - 8 MB de caché LRU (≈45 portadas).
 * - Decodificación en un sólo hilo de fondo.
 */
object CoverCache {

    private const val TAG = "CoverCache"
    private const val MAX_CACHE_KB = 8 * 1024

    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "MusicCover").apply { isDaemon = true }
    }

    private val main = Handler(Looper.getMainLooper())

    fun load(file: File, callback: (Bitmap?) -> Unit) {
        val key = file.absolutePath
        val cached = cache.get(key)
        if (cached != null && !cached.isRecycled) {
            main.post { callback(cached) }
            return
        }
        executor.execute {
            val bitmap = decode(file)
            if (bitmap != null) cache.put(key, bitmap)
            main.post { callback(bitmap) }
        }
    }

    fun clear() {
        cache.evictAll()
    }

    private fun decode(file: File): Bitmap? {
        if (!file.isFile || file.length() <= 0L) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / sample > 600 || bounds.outHeight / sample > 600) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
                inSampleSize = sample
                inScaled = false
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (error: Throwable) {
            Log.w(TAG, "No se pudo decodificar ${file.name}: ${error.message}")
            null
        }
    }
}
