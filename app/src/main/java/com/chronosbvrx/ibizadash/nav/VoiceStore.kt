package com.chronosbvrx.ibizadash.nav

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Caché local de clips de voz para la navegación.
 *
 * El estéreo no tiene TTS ni Internet: el teléfono sintetiza cada frase, la
 * envía por Wi-Fi Direct y aquí se guarda por clave (hash del texto). Así una
 * frase se descarga una vez y queda disponible sin conexión.
 */
object VoiceStore {
    private const val DIR = "voice"

    fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    /** Clave estable a partir del texto de la frase. */
    fun keyFor(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(text.trim().lowercase().toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    fun fileFor(context: Context, key: String): File = File(dir(context), "$key.wav")

    fun has(context: Context, key: String): Boolean {
        val f = fileFor(context, key)
        return f.isFile && f.length() > 100
    }

    /** Guarda un WAV de forma atómica. Devuelve true si quedó utilizable. */
    fun save(context: Context, key: String, bytes: ByteArray): Boolean {
        if (key.isBlank() || bytes.size < 100) return false
        return try {
            val target = fileFor(context, key)
            val tmp = File(target.parentFile, "$key.wav.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                target.writeBytes(bytes)
                tmp.delete()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun count(context: Context): Int =
        dir(context).listFiles()?.count { it.isFile && it.name.endsWith(".wav") } ?: 0

    fun totalBytes(context: Context): Long =
        dir(context).listFiles()?.filter { it.isFile && it.name.endsWith(".wav") }
            ?.sumOf { it.length() } ?: 0L

    /** Borra los clips que no se usan (mantenimiento manual desde Ajustes). */
    fun clear(context: Context): Int {
        var removed = 0
        dir(context).listFiles()?.forEach { f ->
            if (f.isFile && f.delete()) removed++
        }
        return removed
    }
}
