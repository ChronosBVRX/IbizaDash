package com.chronosbvrx.ibizacompanion.modules

import android.content.Context
import android.util.Log
import com.chronosbvrx.ibizacompanion.data.CompanionPrefs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Catálogo de módulos publicados en el release de GitHub.
 *
 * `modules.json` describe cada paquete (id, tipo, versión, tamaño, sha256 y
 * URL). La app lo descarga, y al instalar envía el paquete al estéreo por
 * Wi-Fi Direct.
 */
object ModuleCatalog {
    private const val TAG = "Modules"
    private const val DEFAULT_URL =
        "https://github.com/ChronosBVRX/IbizaDash/releases/latest/download/modules.json"

    data class ModuleInfo(
        val id: String,
        val name: String,
        val description: String,
        val type: String,
        val version: Int,
        val sizeBytes: Long,
        val sha256: String,
        val url: String
    )

    fun manifestUrl(context: Context): String =
        CompanionPrefs.moduleManifestUrl(context).ifBlank { DEFAULT_URL }

    /** Descarga y parsea el catálogo. null si no se pudo leer. */
    fun fetch(context: Context): List<ModuleInfo>? {
        var connection: HttpURLConnection? = null
        val url = manifestUrl(context)
        Log.i(TAG, "catálogo: $url")
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "IbizaCompanion")
            }
            val code = connection.responseCode
            if (code == 404) return emptyList()
            if (code !in 200..299) {
                Log.w(TAG, "modules.json HTTP $code")
                return null
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            parse(text)
        } catch (e: Exception) {
            Log.w(TAG, "fetch: ${e.message}")
            null
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun parse(text: String): List<ModuleInfo> {
        val root = JSONObject(text)
        val arr = root.optJSONArray("modules") ?: return emptyList()
        val out = ArrayList<ModuleInfo>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id", "")
            val url = o.optString("url", "")
            if (id.isBlank() || url.isBlank()) continue
            out.add(
                ModuleInfo(
                    id = id,
                    name = o.optString("name", id),
                    description = o.optString("description", ""),
                    type = o.optString("type", "raw"),
                    version = o.optInt("version", 1),
                    sizeBytes = o.optLong("sizeBytes", 0L),
                    sha256 = o.optString("sha256", ""),
                    url = url
                )
            )
        }
        return out
    }

    fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> "—"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
    }
}
