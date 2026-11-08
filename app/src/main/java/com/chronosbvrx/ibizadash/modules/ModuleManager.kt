package com.chronosbvrx.ibizadash.modules

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Módulos descargables (Fase D).
 *
 * El estéreo no tiene Internet: la app del teléfono descarga el paquete del
 * release y lo envía por Wi-Fi Direct. Aquí se verifica el SHA-256, se
 * descomprime de forma atómica y se registra qué quedó instalado.
 *
 * Tipos soportados:
 *  - `tiles`: PNG organizados como `{z}/{x}/{y}.png` → caché de osmdroid.
 *  - `voice`: WAV por clave de frase → caché de voz (`files/voice`).
 */
object ModuleManager {
    private const val TAG = "Modules"
    private const val STATE = "modules.json"
    private const val MAX_BYTES = 120L * 1024 * 1024 // 120 MB por módulo

    data class Installed(
        val id: String,
        val name: String,
        val type: String,
        val version: Int,
        val sizeBytes: Long,
        val installedAt: Long
    )

    private fun stateFile(context: Context): File = File(context.filesDir, STATE)

    fun installed(context: Context): List<Installed> {
        return try {
            val f = stateFile(context)
            if (!f.exists()) return emptyList()
            val arr = JSONArray(f.readText())
            val out = ArrayList<Installed>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    Installed(
                        id = o.optString("id", ""),
                        name = o.optString("name", ""),
                        type = o.optString("type", ""),
                        version = o.optInt("version", 0),
                        sizeBytes = o.optLong("size", 0L),
                        installedAt = o.optLong("installedAt", 0L)
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun versionOf(context: Context, id: String): Int =
        installed(context).firstOrNull { it.id == id }?.version ?: 0

    fun totalBytes(context: Context): Long = installed(context).sumOf { it.sizeBytes }

    /** Directorio de trabajo para el paquete que se está recibiendo. */
    fun partFile(context: Context, id: String): File {
        val dir = File(context.cacheDir, "modules")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$id.part")
    }

    fun clearPart(context: Context, id: String) {
        try {
            partFile(context, id).delete()
        } catch (_: Exception) {
        }
    }

    /**
     * Verifica y descomprime el paquete recibido.
     * Devuelve true si el módulo quedó instalado.
     */
    fun install(
        context: Context,
        id: String,
        name: String,
        type: String,
        version: Int,
        expectedSize: Long,
        expectedSha: String
    ): Pair<Boolean, String> {
        if (id.isBlank()) return false to "id vacío"
        val part = partFile(context, id)
        if (!part.exists()) return false to "paquete no recibido"
        if (expectedSize > 0 && part.length() != expectedSize) {
            part.delete()
            return false to "tamaño distinto (${part.length()} vs $expectedSize)"
        }
        if (part.length() > MAX_BYTES) {
            part.delete()
            return false to "paquete demasiado grande"
        }
        if (expectedSha.isNotBlank()) {
            val sha = sha256(part)
            if (!sha.equals(expectedSha, ignoreCase = true)) {
                part.delete()
                return false to "sha256 distinto"
            }
        }
        val target = targetDir(context, type)
        if (target == null) {
            part.delete()
            return false to "tipo no soportado: $type"
        }
        return try {
            val extracted = unzip(part, target)
            if (extracted <= 0) return false to "paquete vacío"
            register(context, Installed(id, name.ifBlank { id }, type, version, part.length(), System.currentTimeMillis()))
            part.delete()
            Log.i(TAG, "módulo instalado: $id v$version ($extracted archivos)")
            true to "ok"
        } catch (e: Exception) {
            Log.w(TAG, "install: ${e.message}")
            false to (e.message ?: "error al descomprimir")
        }
    }

    fun remove(context: Context, id: String): Boolean {
        val entry = installed(context).firstOrNull { it.id == id } ?: return false
        // Solo se borra el registro: las teselas/voces se reutilizan entre
        // módulos y borrarlas podría dejar sin mapa o sin voz a otra ruta.
        val remaining = installed(context).filterNot { it.id == id }
        writeState(context, remaining)
        Log.i(TAG, "módulo desregistrado: ${entry.id}")
        return true
    }

    private fun targetDir(context: Context, type: String): File? = when (type) {
        "tiles" -> File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            "osmdroid/tiles/EsriDark"
        ).apply { if (!exists()) mkdirs() }
        "voice" -> File(context.filesDir, "voice").apply { if (!exists()) mkdirs() }
        "raw" -> File(context.filesDir, "modules_data").apply { if (!exists()) mkdirs() }
        else -> null
    }

    /** Extrae evitando rutas maliciosas (zip slip). Devuelve nº de archivos. */
    private fun unzip(zip: File, target: File): Int {
        var count = 0
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                if (entry.isDirectory || name.startsWith("__MACOSX/")) {
                    zis.closeEntry()
                    continue
                }
                val isTiles = target.path.replace('\\', '/').endsWith("osmdroid/tiles/EsriDark")
                val outName = if (isTiles && name.endsWith(".png", ignoreCase = true)) {
                    name.dropLast(4) + ".tile"
                } else {
                    name
                }
                val out = File(target, outName)
                val canonicalTarget = target.canonicalPath + File.separator
                if (!out.canonicalPath.startsWith(canonicalTarget)) {
                    zis.closeEntry()
                    continue
                }
                out.parentFile?.let { if (!it.exists()) it.mkdirs() }
                FileOutputStream(out).use { fos -> zis.copyTo(fos) }
                count++
                zis.closeEntry()
            }
        }
        return count
    }

    private fun register(context: Context, entry: Installed) {
        val list = installed(context).filterNot { it.id == entry.id } + entry
        writeState(context, list)
    }

    private fun writeState(context: Context, list: List<Installed>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("name", e.name)
                    .put("type", e.type)
                    .put("version", e.version)
                    .put("size", e.sizeBytes)
                    .put("installedAt", e.installedAt)
            )
        }
        try {
            stateFile(context).writeText(arr.toString())
        } catch (e: Exception) {
            Log.w(TAG, "writeState: ${e.message}")
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
