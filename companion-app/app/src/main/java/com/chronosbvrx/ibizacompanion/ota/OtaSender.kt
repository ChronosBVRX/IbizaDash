package com.chronosbvrx.ibizacompanion.ota

import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import android.util.Log
import com.chronosbvrx.ibizacompanion.BridgeLink
import com.chronosbvrx.ibizacompanion.core.state.CompanionState
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * OTA: el teléfono consulta el último release de GitHub, descarga
 * `IbizaDash.apk` y lo envía por Wi-Fi Direct. El estéreo verifica tamaño,
 * SHA-256, paquete y firma antes de instalarlo con PackageInstaller.
 *
 * El estéreo nunca usa Internet: el APK entra desde el teléfono.
 */
object OtaSender {
    private const val TAG = "OtaSender"
    private const val CHUNK_BYTES = 32 * 1024
    private const val CACHE_APK = "ota-latest.apk"
    private const val CACHE_META = "ota-latest.json"
    private const val STEREO_PACKAGE = "com.chronosbvrx.ibizadash"
    private const val MAX_CACHE_AGE_MS = 6 * 60 * 60 * 1000L
    private const val DOWNLOAD_URL =
        "https://github.com/ChronosBVRX/IbizaDash/releases/latest/download/IbizaDash.apk"

    data class LatestApk(
        val versionCode: Int,
        val versionName: String,
        val size: Long,
        val sha256: String,
        val file: File
    )

    private val sending = AtomicBoolean(false)
    private val checking = AtomicBoolean(false)

    private val metaLatch = AtomicReference<CountDownLatch?>(null)
    private val metaResult = AtomicReference<JSONObject?>(null)
    private val chunkLatch = AtomicReference<CountDownLatch?>(null)
    private val chunkResult = AtomicReference<JSONObject?>(null)
    private val endLatch = AtomicReference<CountDownLatch?>(null)
    private val endResult = AtomicReference<JSONObject?>(null)

    /** Respuestas del estéreo al protocolo OTA (las reenvía MediaBridgeService). */
    fun onReply(obj: JSONObject) {
        when (obj.optString("type")) {
            "APK_META_ACK", "OTA_ALREADY_NEWER", "OTA_ERROR" -> {
                metaResult.set(obj)
                metaLatch.get()?.countDown()
            }
            "APK_CHUNK_ACK", "APK_CHUNK_NACK" -> {
                chunkResult.set(obj)
                chunkLatch.get()?.countDown()
            }
            "APK_END_OK" -> {
                endResult.set(obj)
                endLatch.get()?.countDown()
            }
        }
    }

    private fun setStatus(status: String, progress: Float = -1f, isSending: Boolean? = null) {
        CompanionState.update {
            it.copy(
                otaStatus = status,
                otaProgress = if (progress >= 0f) progress else it.otaProgress,
                otaSending = isSending ?: it.otaSending
            )
        }
    }

    /**
     * Consulta GitHub y actualiza el estado de la UI. Debe llamarse desde un
     * hilo de fondo. [force] ignora la caché de 6 horas.
     */
    fun refresh(context: Context, force: Boolean = false) {
        if (!checking.compareAndSet(false, true)) return
        try {
            CompanionState.update { it.copy(otaChecking = true) }
            setStatus("Buscando la última versión en GitHub…")
            val latest = latestApk(context, force)
            if (latest == null) {
                setStatus("No se pudo consultar GitHub; revisa la conexión del teléfono")
                CompanionState.update {
                    it.copy(otaLatestVersionCode = 0, otaLatestVersionName = "")
                }
                return
            }
            val stereoVc = CompanionState.state.value.stereoVersionCode
            CompanionState.update {
                it.copy(
                    otaLatestVersionCode = latest.versionCode,
                    otaLatestVersionName = latest.versionName
                )
            }
            setStatus(
                when {
                    stereoVc == 0 ->
                        "Disponible v${latest.versionName} · conecta el estéreo para actualizar"
                    latest.versionCode > stereoVc ->
                        "Disponible v${latest.versionName} · el estéreo tiene v$stereoVc"
                    else -> "El estéreo ya está al día"
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "refresh: ${e.message}")
            setStatus("Error consultando GitHub: ${e.message}")
        } finally {
            CompanionState.update { it.copy(otaChecking = false) }
            checking.set(false)
        }
    }

    /**
     * Envía la actualización. Debe llamarse desde un hilo de fondo.
     * Devuelve true si el estéreo confirmó la recepción completa.
     */
    fun sendUpdate(context: Context): Boolean {
        if (!sending.compareAndSet(false, true)) return false
        try {
            if (!BridgeLink.isWifiActive()) {
                setStatus("La OTA necesita Wi-Fi Direct activo", 0f, false)
                return false
            }
            val latest = latestApk(context, force = false) ?: run {
                setStatus("No se pudo descargar el APK del estéreo desde GitHub", 0f, false)
                return false
            }
            val stereoVc = CompanionState.state.value.stereoVersionCode
            if (stereoVc > 0 && latest.versionCode <= stereoVc) {
                setStatus("El estéreo ya tiene v$stereoVc o una más nueva", 1f, false)
                return false
            }

            val bytes = latest.file.readBytes()
            val sha256 = sha256Hex(bytes)
            val totalChunks = (bytes.size + CHUNK_BYTES - 1) / CHUNK_BYTES
            setStatus("Enviando v${latest.versionName} al estéreo…", 0f, true)

            // 1) META + espera de ACK.
            metaResult.set(null)
            metaLatch.set(CountDownLatch(1))
            val metaOk = BridgeLink.send(
                JSONObject()
                    .put("type", "APK_META")
                    .put("versionCode", latest.versionCode)
                    .put("versionName", latest.versionName)
                    .put("size", bytes.size.toLong())
                    .put("sha256", sha256)
                    .put("totalChunks", totalChunks)
            )
            if (!metaOk || !metaLatch.get()!!.await(15, TimeUnit.SECONDS)) {
                setStatus("El estéreo no respondió a la OTA", 0f, false)
                return false
            }
            val metaReply = metaResult.get()
            if (metaReply?.optString("type") == "OTA_ALREADY_NEWER") {
                setStatus("El estéreo ya tiene esa versión o una más nueva", 1f, false)
                return false
            }
            if (metaReply?.optString("type") != "APK_META_ACK") {
                setStatus(
                    "El estéreo rechazó la OTA: ${metaReply?.optString("reason", "?")}",
                    0f, false
                )
                return false
            }

            // 2) Chunks con ACK por chunk (reintento una vez ante NACK/timeout).
            var index = 0
            while (index < totalChunks) {
                val start = index * CHUNK_BYTES
                val end = minOf(start + CHUNK_BYTES, bytes.size)
                val chunk = bytes.copyOfRange(start, end)
                val b64 = Base64.encodeToString(chunk, Base64.NO_WRAP)
                var attempts = 0
                var acked = false
                while (attempts < 2 && !acked) {
                    attempts++
                    chunkResult.set(null)
                    chunkLatch.set(CountDownLatch(1))
                    val sent = BridgeLink.send(
                        JSONObject()
                            .put("type", "APK_CHUNK")
                            .put("index", index)
                            .put("data", b64)
                    )
                    if (!sent) break
                    val got = chunkLatch.get()!!.await(15, TimeUnit.SECONDS)
                    val reply = chunkResult.get()
                    acked = got && reply?.optString("type") == "APK_CHUNK_ACK" &&
                        reply.optInt("index", -1) == index
                    if (!acked) Log.w(TAG, "chunk $index intento $attempts sin ACK")
                }
                if (!acked) {
                    setStatus("La transferencia se cortó en el chunk $index", 0f, false)
                    return false
                }
                index++
                setStatus(
                    "Enviando v${latest.versionName} al estéreo…",
                    index.toFloat() / totalChunks
                )
            }

            // 3) END + espera de confirmación.
            endResult.set(null)
            endLatch.set(CountDownLatch(1))
            if (!BridgeLink.send(JSONObject().put("type", "APK_END"))) {
                setStatus("No se pudo cerrar la transferencia", 0f, false)
                return false
            }
            if (!endLatch.get()!!.await(20, TimeUnit.SECONDS)) {
                setStatus("El estéreo no confirmó la recepción", 0f, false)
                return false
            }
            val endReply = endResult.get()
            if (endReply?.optString("type") != "APK_END_OK") {
                setStatus(
                    "El estéreo rechazó el APK: ${endReply?.optString("reason", "?")}",
                    0f, false
                )
                return false
            }
            setStatus(
                "v${latest.versionName} recibida en el estéreo · instálala desde Ajustes rápidos",
                1f, false
            )
            Log.i(TAG, "OTA enviada: v${latest.versionName} (${latest.versionCode})")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "sendUpdate: ${e.message}")
            setStatus("Error enviando la OTA: ${e.message}", 0f, false)
            return false
        } finally {
            sending.set(false)
        }
    }

    // ------------------------------------------------------------------
    // Descarga desde GitHub
    // ------------------------------------------------------------------

    private fun cacheApk(context: Context): File = File(context.cacheDir, CACHE_APK)
    private fun cacheMeta(context: Context): File = File(context.cacheDir, CACHE_META)

    /**
     * Devuelve el último APK, usando la caché si sigue vigente. Debe llamarse
     * desde un hilo de fondo.
     */
    private fun latestApk(context: Context, force: Boolean): LatestApk? {
        val apk = cacheApk(context)
        val meta = cacheMeta(context)
        val cached = readCached(context, apk, meta)
        if (!force && cached != null &&
            System.currentTimeMillis() - readCheckedAt(meta) < MAX_CACHE_AGE_MS
        ) {
            return cached
        }

        var connection: HttpURLConnection? = null
        val tmp = File(context.cacheDir, "$CACHE_APK.part")
        return try {
            connection = URL(DOWNLOAD_URL).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("User-Agent", "IbizaCompanion-OTA")
            val cachedEtag = readMetaString(meta, "etag")
            val cachedModified = readMetaString(meta, "lastModified")
            if (!force) {
                if (!cachedEtag.isNullOrBlank()) {
                    connection.setRequestProperty("If-None-Match", cachedEtag)
                }
                if (!cachedModified.isNullOrBlank()) {
                    connection.setRequestProperty("If-Modified-Since", cachedModified)
                }
            }
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED && cached != null) {
                writeMeta(
                    meta,
                    cached.versionCode,
                    cached.versionName,
                    cached.size,
                    cached.sha256,
                    cachedEtag,
                    cachedModified
                )
                return cached
            }
            if (code !in 200..299) {
                Log.w(TAG, "GitHub respondió HTTP $code")
                return cached
            }

            tmp.delete()
            connection.inputStream.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            }
            if (!tmp.exists() || tmp.length() <= 0L) {
                Log.w(TAG, "descarga vacía")
                return cached
            }
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageArchiveInfo(
                tmp.absolutePath,
                PackageManager.GET_META_DATA
            )
            if (info == null || info.packageName != STEREO_PACKAGE) {
                Log.w(TAG, "el APK descargado no es IbizaDash")
                tmp.delete()
                return cached
            }
            val versionCode = info.versionCode
            val versionName = info.versionName ?: "?"
            if (apk.exists()) apk.delete()
            if (!tmp.renameTo(apk)) {
                tmp.copyTo(apk, overwrite = true)
                tmp.delete()
            }
            val sha = sha256Hex(apk.readBytes())
            val latest = LatestApk(
                versionCode = versionCode,
                versionName = versionName,
                size = apk.length(),
                sha256 = sha,
                file = apk
            )
            writeMeta(
                meta,
                versionCode,
                versionName,
                latest.size,
                sha,
                connection.getHeaderField("ETag"),
                connection.getHeaderField("Last-Modified")
            )
            Log.i(TAG, "APK de GitHub: v$versionName ($versionCode) ${latest.size}B")
            latest
        } catch (e: Exception) {
            Log.w(TAG, "descarga: ${e.message}")
            cached
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
            try {
                if (tmp.exists()) tmp.delete()
            } catch (_: Exception) {
            }
        }
    }

    private fun readCached(context: Context, apk: File, meta: File): LatestApk? {
        if (!apk.exists() || !meta.exists()) return null
        return try {
            val obj = JSONObject(meta.readText())
            val versionCode = obj.optInt("versionCode", 0)
            val versionName = obj.optString("versionName", "?")
            val size = obj.optLong("size", 0L)
            val sha = obj.optString("sha256", "")
            if (versionCode <= 0 || size <= 0L || apk.length() != size) {
                apk.delete()
                meta.delete()
                return null
            }
            if (sha.isNotBlank() && sha256Hex(apk.readBytes()) != sha.lowercase()) {
                apk.delete()
                meta.delete()
                return null
            }
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.GET_META_DATA
            )
            if (info == null || info.packageName != STEREO_PACKAGE) {
                apk.delete()
                meta.delete()
                return null
            }
            LatestApk(versionCode, versionName, size, sha, apk)
        } catch (_: Exception) {
            null
        }
    }

    private fun readCheckedAt(meta: File): Long =
        try {
            if (meta.exists()) JSONObject(meta.readText()).optLong("checkedAt", 0L) else 0L
        } catch (_: Exception) {
            0L
        }

    private fun readMetaString(meta: File, key: String): String? =
        try {
            if (!meta.exists()) {
                null
            } else {
                JSONObject(meta.readText()).optString(key, "").takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }

    private fun writeMeta(
        meta: File,
        versionCode: Int,
        versionName: String,
        size: Long,
        sha256: String,
        etag: String?,
        lastModified: String?
    ) {
        try {
            meta.writeText(
                JSONObject()
                    .put("versionCode", versionCode)
                    .put("versionName", versionName)
                    .put("size", size)
                    .put("sha256", sha256)
                    .put("etag", etag ?: "")
                    .put("lastModified", lastModified ?: "")
                    .put("checkedAt", System.currentTimeMillis())
                    .toString()
            )
        } catch (e: Exception) {
            Log.w(TAG, "writeMeta: ${e.message}")
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
