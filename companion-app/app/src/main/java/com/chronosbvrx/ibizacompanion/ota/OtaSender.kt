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
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * OTA v1: envía el APK del estéreo (incluido en assets/ibizadash.apk) por el
 * puente Wi-Fi Direct. El estéreo lo verifica (tamaño + sha256) y lo instala
 * con PackageInstaller cuando el usuario lo confirma en su pantalla.
 *
 * Solo por Wi-Fi: 1.4 MB por Bluetooth RFCOMM tardaría minutos y comparte
 * radio con el audio A2DP.
 */
object OtaSender {
    private const val TAG = "OtaSender"
    private const val ASSET_PATH = "ibizadash.apk"
    private const val CHUNK_BYTES = 32 * 1024

    data class BundledApk(
        val versionCode: Int,
        val versionName: String,
        val size: Long,
        val file: File
    )

    @Volatile
    private var sending = false

    private val metaLatch = AtomicReference<CountDownLatch?>(null)
    private val metaResult = AtomicReference<JSONObject?>(null)
    private val chunkLatch = AtomicReference<CountDownLatch?>(null)
    private val chunkResult = AtomicReference<JSONObject?>(null)
    private val endLatch = AtomicReference<CountDownLatch?>(null)
    private val endResult = AtomicReference<JSONObject?>(null)

    /** Lee el APK incluido en assets y su versión. null si no está incluido. */
    fun bundledInfo(context: Context): BundledApk? {
        return try {
            val cacheFile = File(context.cacheDir, "ota-bundled.apk")
            context.assets.open(ASSET_PATH).use { input ->
                FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
            }
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageArchiveInfo(
                cacheFile.absolutePath,
                PackageManager.GET_META_DATA
            ) ?: return null
            BundledApk(
                versionCode = info.versionCode,
                versionName = info.versionName ?: "?",
                size = cacheFile.length(),
                file = cacheFile
            )
        } catch (e: Exception) {
            Log.w(TAG, "bundledInfo: ${e.message}")
            null
        }
    }

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
     * Envía la actualización. Debe llamarse desde un hilo de fondo.
     * Devuelve true si el estéreo confirmó la recepción completa.
     */
    fun sendUpdate(context: Context): Boolean {
        if (sending) return false
        if (!BridgeLink.isWifiActive()) {
            setStatus("La OTA necesita Wi-Fi Direct activo", 0f, false)
            return false
        }
        val bundled = bundledInfo(context) ?: run {
            setStatus("Esta app no incluye el APK del estéreo", 0f, false)
            return false
        }
        sending = true
        setStatus("Enviando v${bundled.versionName} al estéreo…", 0f, true)
        try {
            val bytes = bundled.file.readBytes()
            val sha256 = sha256Hex(bytes)
            val totalChunks = (bytes.size + CHUNK_BYTES - 1) / CHUNK_BYTES

            // 1) META + espera de ACK.
            metaResult.set(null)
            metaLatch.set(CountDownLatch(1))
            val metaOk = BridgeLink.send(
                JSONObject()
                    .put("type", "APK_META")
                    .put("versionCode", bundled.versionCode)
                    .put("versionName", bundled.versionName)
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
                    "Enviando v${bundled.versionName} al estéreo…",
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
                "v${bundled.versionName} recibida en el estéreo · instálala desde Ajustes rápidos",
                1f, false
            )
            Log.i(TAG, "OTA enviada: v${bundled.versionName} (${bundled.versionCode})")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "sendUpdate: ${e.message}")
            setStatus("Error enviando la OTA: ${e.message}", 0f, false)
            return false
        } finally {
            sending = false
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
