package com.chronosbvrx.ibizacompanion.modules

import android.content.Context
import android.util.Base64
import android.util.Log
import com.chronosbvrx.ibizacompanion.BridgeLink
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
 * Descarga un módulo y lo envía al estéreo por Wi-Fi Direct.
 *
 * Reutiliza el patrón del OTA: META → chunks → END, con verificación de
 * tamaño y SHA-256 en el estéreo antes de instalar.
 */
object ModuleSender {
    private const val TAG = "Modules"
    private const val CHUNK = 32 * 1024
    private const val SEND_WAIT_S = 40L

    data class Progress(
        val id: String,
        val received: Long,
        val total: Long,
        val status: String,
        val done: Boolean = false,
        val error: Boolean = false
    )

    @Volatile
    var onProgress: ((Progress) -> Unit)? = null

    private val sending = AtomicBoolean(false)
    private val metaLatch = AtomicReference<CountDownLatch?>(null)
    private val metaReply = AtomicReference<JSONObject?>(null)
    private val endLatch = AtomicReference<CountDownLatch?>(null)
    private val endReply = AtomicReference<JSONObject?>(null)

    /** Respuestas del estéreo (las reenvía MediaBridgeService). */
    fun onReply(obj: JSONObject) {
        when (obj.optString("type")) {
            "MODULE_META_ACK" -> {
                metaReply.set(obj)
                metaLatch.get()?.countDown()
            }
            "MODULE_END_OK", "MODULE_ERROR" -> {
                endReply.set(obj)
                endLatch.get()?.countDown()
            }
        }
    }

    private fun report(id: String, received: Long, total: Long, status: String, done: Boolean = false, error: Boolean = false) {
        try {
            onProgress?.invoke(Progress(id, received, total, status, done, error))
        } catch (_: Exception) {
        }
    }

    /** Hilo de fondo. Devuelve true si el estéreo confirmó la instalación. */
    fun send(context: Context, module: ModuleCatalog.ModuleInfo): Boolean {
        Log.i(TAG, "send ${module.id} v${module.version} connected=${BridgeLink.isConnected()}")
        if (!sending.compareAndSet(false, true)) {
            Log.w(TAG, "send: ya había un envío en curso")
            return false
        }
        try {
            if (!BridgeLink.isConnected()) {
                report(module.id, 0, module.sizeBytes, "Conecta el estéreo por Wi-Fi Direct", error = true)
                return false
            }
            val apk = download(context, module)
            if (apk == null) {
                report(module.id, 0, module.sizeBytes, "No se pudo descargar el módulo", error = true)
                return false
            }
            val size = apk.length()
            // 1) META
            metaReply.set(null)
            val metaLatchLocal = CountDownLatch(1)
            metaLatch.set(metaLatchLocal)
            val sent = BridgeLink.send(
                JSONObject()
                    .put("type", "MODULE_META")
                    .put("id", module.id)
                    .put("name", module.name)
                    // Ojo: "type" ya es el tipo de mensaje; el del módulo va aparte.
                    .put("moduleType", module.type)
                    .put("version", module.version)
                    .put("size", size)
                    .put("sha256", module.sha256)
            )
            if (!sent || !metaLatchLocal.await(15, TimeUnit.SECONDS)) {
                report(module.id, 0, size, "El estéreo no respondió", error = true)
                return false
            }
            if (metaReply.get()?.optString("type") != "MODULE_META_ACK") {
                report(module.id, 0, size, "El estéreo rechazó el módulo", error = true)
                return false
            }

            // 2) Chunks
            var index = 0
            var offset = 0L
            apk.inputStream().use { input ->
                val buffer = ByteArray(CHUNK)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    val chunk = buffer.copyOf(read)
                    val b64 = Base64.encodeToString(chunk, Base64.NO_WRAP)
                    val ok = BridgeLink.send(
                        JSONObject()
                            .put("type", "MODULE_CHUNK")
                            .put("id", module.id)
                            .put("index", index)
                            .put("data", b64)
                    )
                    if (!ok) {
                        report(module.id, offset, size, "Se cortó el envío", error = true)
                        return false
                    }
                    offset += read
                    index++
                    if (index % 4 == 0 || offset >= size) {
                        report(module.id, offset, size, "Enviando ${ModuleCatalog.formatSize(offset)} de ${ModuleCatalog.formatSize(size)}")
                    }
                }
            }

            // 3) END y verificación en el estéreo
            endReply.set(null)
            val endLatchLocal = CountDownLatch(1)
            endLatch.set(endLatchLocal)
            if (!BridgeLink.send(JSONObject().put("type", "MODULE_END").put("id", module.id))) {
                report(module.id, offset, size, "No se pudo cerrar el envío", error = true)
                return false
            }
            if (!endLatchLocal.await(SEND_WAIT_S, TimeUnit.SECONDS)) {
                report(module.id, offset, size, "El estéreo no confirmó la instalación", error = true)
                return false
            }
            val reply = endReply.get()
            if (reply?.optString("type") == "MODULE_END_OK") {
                report(module.id, size, size, "Instalado en el estéreo", done = true)
                Log.i(TAG, "módulo ${module.id} instalado")
                return true
            }
            val reason = reply?.optString("reason", "error").orEmpty()
            report(module.id, offset, size, "Rechazado: $reason", error = true)
            return false
        } catch (e: Exception) {
            Log.w(TAG, "send: ${e.message}")
            report(module.id, 0, module.sizeBytes, "Error: ${e.message}", error = true)
            return false
        } finally {
            sending.set(false)
        }
    }

    private fun download(context: Context, module: ModuleCatalog.ModuleInfo): File? {
        val dir = File(context.cacheDir, "modules").apply { if (!exists()) mkdirs() }
        val target = File(dir, "${module.id}.zip")
        if (target.exists() && module.sizeBytes > 0 && target.length() == module.sizeBytes) {
            if (module.sha256.isBlank() || sha256(target).equals(module.sha256, true)) return target
        }
        val tmp = File(dir, "${module.id}.zip.part")
        var connection: HttpURLConnection? = null
        return try {
            report(module.id, 0, module.sizeBytes, "Descargando módulo…")
            connection = (URL(module.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "IbizaCompanion")
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "descarga HTTP $code")
                return null
            }
            tmp.delete()
            connection.inputStream.use { input ->
                FileOutputStream(tmp).use { out -> input.copyTo(out) }
            }
            if (!tmp.exists() || tmp.length() <= 0) return null
            if (module.sha256.isNotBlank() && !sha256(tmp).equals(module.sha256, true)) {
                Log.w(TAG, "sha256 del módulo no coincide")
                tmp.delete()
                return null
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            target
        } catch (e: Exception) {
            Log.w(TAG, "download: ${e.message}")
            null
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
