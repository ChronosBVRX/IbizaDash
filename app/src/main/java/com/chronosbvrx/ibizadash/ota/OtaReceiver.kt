package com.chronosbvrx.ibizadash.ota

import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * OTA v1: el teléfono envía el APK del estéreo por el puente Wi-Fi Direct
 * (el mismo canal TCP que las rutas y las portadas) y el estéreo lo instala
 * con PackageInstaller. No hace falta ADB para actualizar.
 *
 * Protocolo (JSON por línea, igual que el resto del puente):
 *  - APK_META  {versionCode, versionName, size, sha256, totalChunks}
 *  - APK_CHUNK {index, data}  (base64, ~32 KB por chunk, en orden)
 *  - APK_END   {}             (verifica tamaño + sha256 y deja el APK listo)
 *
 * El APK queda en files/ota/ibizadash.apk con su meta en files/ota/meta.json.
 * La instalación la dispara el usuario desde Ajustes rápidos → Actualización
 * del sistema (o la compañera avisa y el usuario toca instalar).
 */
object OtaReceiver {
    private const val TAG = "OtaReceiver"
    private const val DIR_NAME = "ota"
    private const val PART_NAME = "ibizadash.apk.part"
    private const val APK_NAME = "ibizadash.apk"
    private const val META_NAME = "meta.json"

    data class PendingUpdate(
        val versionCode: Int,
        val versionName: String,
        val size: Long,
        val sha256: String,
        val file: File
    )

    /** Se invoca en el hilo de UI cuando una actualización queda lista. */
    @Volatile
    var onReady: ((PendingUpdate) -> Unit)? = null

    private fun otaDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    private fun partFile(context: Context): File = File(otaDir(context), PART_NAME)
    private fun apkFile(context: Context): File = File(otaDir(context), APK_NAME)
    private fun metaFile(context: Context): File = File(otaDir(context), META_NAME)

    fun installedVersionCode(context: Context): Int {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        } catch (_: Exception) {
            0
        }
    }

    fun installedVersionName(context: Context): String {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
    }

    /** Devuelve la actualización pendiente si existe, está verificada y es más nueva. */
    fun pendingUpdate(context: Context): PendingUpdate? {
        return try {
            val meta = metaFile(context)
            val apk = apkFile(context)
            if (!meta.exists() || !apk.exists()) return null
            val obj = JSONObject(meta.readText())
            val pending = PendingUpdate(
                versionCode = obj.optInt("versionCode", 0),
                versionName = obj.optString("versionName", "?"),
                size = obj.optLong("size", 0L),
                sha256 = obj.optString("sha256", ""),
                file = apk
            )
            if (pending.versionCode <= installedVersionCode(context)) {
                Log.i(TAG, "OTA obsoleta v${pending.versionCode}; se borra")
                apk.delete()
                meta.delete()
                return null
            }
            if (pending.size > 0 && apk.length() != pending.size) return null
            if (pending.sha256.isNotBlank() && sha256Hex(apk) != pending.sha256.lowercase()) {
                Log.w(TAG, "OTA con sha256 distinto; se borra")
                apk.delete()
                meta.delete()
                return null
            }
            pending
        } catch (e: Exception) {
            Log.w(TAG, "pendingUpdate: ${e.message}")
            null
        }
    }

    fun clearPending(context: Context) {
        try {
            apkFile(context).delete()
            metaFile(context).delete()
            partFile(context).delete()
        } catch (_: Exception) {
        }
    }

    /**
     * Procesa un mensaje OTA. Devuelve la respuesta JSON para el teléfono
     * (ACK/NACK) o null si el mensaje no es OTA.
     */
    fun handle(context: Context, obj: JSONObject): JSONObject? {
        return try {
            when (obj.optString("type")) {
                "APK_META" -> onMeta(context, obj)
                "APK_CHUNK" -> onChunk(context, obj)
                "APK_END" -> onEnd(context, obj)
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "handle: ${e.message}")
            JSONObject().put("type", "OTA_ERROR").put("reason", e.message ?: "?")
        }
    }

    private fun onMeta(context: Context, obj: JSONObject): JSONObject {
        val versionCode = obj.optInt("versionCode", 0)
        val versionName = obj.optString("versionName", "?")
        val size = obj.optLong("size", 0L)
        val sha256 = obj.optString("sha256", "")
        val totalChunks = obj.optInt("totalChunks", 0)
        if (versionCode <= 0 || size <= 0L || totalChunks <= 0) {
            return JSONObject().put("type", "OTA_ERROR").put("reason", "meta inválida")
        }
        if (versionCode <= installedVersionCode(context)) {
            return JSONObject()
                .put("type", "OTA_ALREADY_NEWER")
                .put("installed", installedVersionCode(context))
        }
        clearPending(context)
        metaFile(context).writeText(
            JSONObject()
                .put("versionCode", versionCode)
                .put("versionName", versionName)
                .put("size", size)
                .put("sha256", sha256)
                .put("totalChunks", totalChunks)
                .put("receivedChunks", 0)
                .toString()
        )
        partFile(context).writeBytes(ByteArray(0))
        Log.i(TAG, "APK_META v$versionName ($versionCode) ${size}B $totalChunks chunks")
        return JSONObject()
            .put("type", "APK_META_ACK")
            .put("versionCode", versionCode)
    }

    private fun onChunk(context: Context, obj: JSONObject): JSONObject {
        val meta = metaFile(context)
        if (!meta.exists()) {
            return JSONObject().put("type", "OTA_ERROR").put("reason", "sin APK_META")
        }
        val metaObj = JSONObject(meta.readText())
        val expected = metaObj.optInt("receivedChunks", 0)
        val index = obj.optInt("index", -1)
        if (index != expected) {
            return JSONObject()
                .put("type", "APK_CHUNK_NACK")
                .put("expected", expected)
        }
        val b64 = obj.optString("data", "")
        if (b64.isBlank()) {
            return JSONObject().put("type", "OTA_ERROR").put("reason", "chunk vacío")
        }
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        FileOutputStream(partFile(context), true).use { it.write(bytes) }
        metaObj.put("receivedChunks", expected + 1)
        meta.writeText(metaObj.toString())
        return JSONObject()
            .put("type", "APK_CHUNK_ACK")
            .put("index", index)
    }

    private fun onEnd(context: Context, obj: JSONObject): JSONObject {
        val meta = metaFile(context)
        val part = partFile(context)
        if (!meta.exists() || !part.exists()) {
            return JSONObject().put("type", "OTA_ERROR").put("reason", "sin transferencia")
        }
        val metaObj = JSONObject(meta.readText())
        val size = metaObj.optLong("size", 0L)
        val sha256 = metaObj.optString("sha256", "")
        val totalChunks = metaObj.optInt("totalChunks", 0)
        val received = metaObj.optInt("receivedChunks", 0)
        if (received != totalChunks || part.length() != size) {
            Log.w(TAG, "APK_END incompleto: $received/$totalChunks ${part.length()}/$size")
            return JSONObject().put("type", "OTA_ERROR").put("reason", "transferencia incompleta")
        }
        if (sha256.isNotBlank() && sha256Hex(part) != sha256.lowercase()) {
            Log.w(TAG, "APK_END sha256 distinto")
            clearPending(context)
            return JSONObject().put("type", "OTA_ERROR").put("reason", "sha256 distinto")
        }
        val archive = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(
                part.absolutePath,
                PackageManager.GET_SIGNATURES
            )
        } catch (_: Exception) {
            null
        }
        if (archive == null || archive.packageName != context.packageName) {
            Log.w(TAG, "APK_END: paquete ajeno")
            clearPending(context)
            return JSONObject().put("type", "OTA_ERROR").put("reason", "paquete ajeno")
        }
        if (archive.versionCode != metaObj.optInt("versionCode", 0)) {
            Log.w(TAG, "APK_END: versionCode no coincide")
            clearPending(context)
            return JSONObject().put("type", "OTA_ERROR").put("reason", "versión distinta")
        }
        if (!sameSignature(context, archive)) {
            Log.w(TAG, "APK_END: firma distinta; OTA rechazada")
            clearPending(context)
            return JSONObject().put("type", "OTA_ERROR").put("reason", "firma distinta")
        }
        val apk = apkFile(context)
        if (apk.exists()) apk.delete()
        if (!part.renameTo(apk)) {
            part.copyTo(apk, overwrite = true)
            part.delete()
        }
        val pending = PendingUpdate(
            versionCode = metaObj.optInt("versionCode", 0),
            versionName = metaObj.optString("versionName", "?"),
            size = size,
            sha256 = sha256,
            file = apk
        )
        Log.i(TAG, "OTA lista: v${pending.versionName} (${pending.versionCode})")
        try {
            onReady?.invoke(pending)
        } catch (_: Exception) {
        }
        return JSONObject()
            .put("type", "APK_END_OK")
            .put("versionCode", pending.versionCode)
            .put("versionName", pending.versionName)
    }

    /** Comprueba paquete y firma antes de abrir la sesión de instalación. */
    fun isValidApk(context: Context, file: File): Boolean {
        return try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.GET_SIGNATURES
            ) ?: return false
            info.packageName == context.packageName && sameSignature(context, info)
        } catch (_: Exception) {
            false
        }
    }

    /** Evita instalar un APK firmado con otra llave (Android lo rechazaría). */
    private fun sameSignature(
        context: Context,
        archive: android.content.pm.PackageInfo
    ): Boolean {
        return try {
            @Suppress("DEPRECATION")
            val current = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            val incoming = archive.signatures ?: return false
            val installed = current.signatures ?: return false
            incoming.size == installed.size && incoming.indices.all {
                incoming[it].toByteArray().contentEquals(installed[it].toByteArray())
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun sha256Hex(file: File): String {
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
