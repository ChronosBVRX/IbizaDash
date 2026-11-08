package com.chronosbvrx.ibizacompanion.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.chronosbvrx.ibizacompanion.core.state.CompanionState
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Autoactualización de IbizaCompanion (la app del teléfono).
 *
 * Descarga `IbizaCompanion.apk` del último release de GitHub, comprueba que el
 * paquete y la versión sean los esperados y, cuando hay una más nueva, deja el
 * APK listo para instalar. Android exige una confirmación del usuario, así que
 * la app avisa y abre el instalador del sistema con un toque.
 *
 * Android no permite instalar en silencio a una app normal; el flujo es:
 * detectar → descargar en segundo plano → avisar → usuario confirma.
 */
object CompanionUpdater {
    private const val TAG = "CompanionUpdater"

    const val ACTION_INSTALL_STATUS = "com.chronosbvrx.ibizacompanion.INSTALL_STATUS"

    private const val APK_NAME = "self-update.apk"
    private const val META_NAME = "self-update.json"
    private const val MAX_CACHE_AGE_MS = 6 * 60 * 60 * 1000L
    private const val DOWNLOAD_URL =
        "https://github.com/ChronosBVRX/IbizaDash/releases/latest/download/IbizaCompanion.apk"

    /** Avisa cuando una actualización quedó descargada y verificada. */
    @Volatile
    var onReady: ((versionName: String) -> Unit)? = null

    private val checking = AtomicBoolean(false)

    data class Latest(
        val versionCode: Int,
        val versionName: String,
        val file: File
    )

    private fun installedVersionCode(context: Context): Int = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode
    } catch (_: Exception) {
        0
    }

    fun installedVersionName(context: Context): String = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }

    private fun apkFile(context: Context): File = File(context.cacheDir, APK_NAME)
    private fun metaFile(context: Context): File = File(context.cacheDir, META_NAME)

    /**
     * Consulta GitHub y deja la actualización lista si hay una más nueva.
     * Debe llamarse desde un hilo de fondo.
     */
    fun checkForUpdate(context: Context, force: Boolean = false) {
        if (!checking.compareAndSet(false, true)) return
        try {
            val installed = installedVersionCode(context)
            val cached = readCached(context)
            val fresh = cached != null && !force &&
                System.currentTimeMillis() - readCheckedAt(context) < MAX_CACHE_AGE_MS

            val latest = if (fresh) cached else download(context, cached)
            if (latest == null) {
                setStatus("No se pudo consultar la actualización; revisa Internet")
                publishState(installed, null)
                return
            }
            publishState(installed, latest)
            if (latest.versionCode > installed) {
                Log.i(TAG, "actualización lista: v${latest.versionName} (${latest.versionCode})")
                try {
                    onReady?.invoke(latest.versionName)
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "checkForUpdate: ${e.message}")
            setStatus("Error buscando actualización: ${e.message}")
        } finally {
            checking.set(false)
        }
    }

    private fun publishState(installed: Int, latest: Latest?) {
        val available = latest != null && latest.versionCode > installed
        CompanionState.update {
            it.copy(
                selfUpdateAvailable = available,
                selfUpdateVersionCode = latest?.versionCode ?: 0,
                selfUpdateVersionName = latest?.versionName ?: "",
                selfUpdateStatus = when {
                    available -> "v${latest!!.versionName} lista para instalar"
                    latest != null -> "Al día (v${installedVersionNameOr(installed)})"
                    else -> it.selfUpdateStatus
                }
            )
        }
    }

    private fun installedVersionNameOr(code: Int): String =
        if (code > 0) "v$code" else "?"

    private fun setStatus(status: String) {
        CompanionState.update { it.copy(selfUpdateStatus = status) }
    }

    /** APK descargado y verificado, si es más nuevo que la versión instalada. */
    fun pendingUpdate(context: Context): Latest? {
        val cached = readCached(context) ?: return null
        return cached.takeIf { it.versionCode > installedVersionCode(context) }
    }

    /** ¿El sistema permite instalar APKs desde esta app? */
    fun canInstall(context: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    } catch (_: Exception) {
        false
    }

    fun openInstallPermissionSettings(context: Context) {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.w(TAG, "openInstallPermissionSettings: ${e.message}")
        }
    }

    /**
     * Abre el instalador del sistema con el APK pendiente. Debe llamarse con
     * un Context de Activity (para el diálogo del sistema).
     */
    fun install(context: Context): Boolean {
        val pending = pendingUpdate(context)
        if (pending == null) {
            setStatus("No hay actualización descargada")
            return false
        }
        CompanionState.update { it.copy(selfUpdating = true) }
        return try {
            installViaSession(context, pending.file)
            true
        } catch (e: Exception) {
            Log.w(TAG, "sesión falló, se intenta por intent: ${e.message}")
            installViaIntent(context, pending.file)
            true
        }
    }

    /**
     * Respaldo cuando el sistema no entrega los extras de la sesión:
     * abre el instalador clásico con el APK ya verificado.
     */
    fun fallbackInstall(context: Context) {
        val pending = pendingUpdate(context) ?: return
        installViaIntent(context, pending.file)
    }

    private fun installViaSession(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            java.io.FileInputStream(file).use { input ->
                session.openWrite("ibizacompanion", 0, file.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val statusIntent = Intent(ACTION_INSTALL_STATUS).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // PackageInstaller escribe EXTRA_STATUS en este intent.
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
            val statusPending = PendingIntent.getBroadcast(
                context, sessionId, statusIntent, flags
            )
            session.commit(statusPending.intentSender)
            Log.i(TAG, "sesión $sessionId enviada a instalar")
        } finally {
            try {
                session.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun installViaIntent(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "installViaIntent: ${e.message}")
            CompanionState.update {
                it.copy(selfUpdating = false, selfUpdateStatus = "No se pudo abrir el instalador")
            }
        }
    }

    // ------------------------------------------------------------------
    // Descarga y caché
    // ------------------------------------------------------------------

    private fun download(context: Context, cached: Latest?): Latest? {
        var connection: HttpURLConnection? = null
        val tmp = File(context.cacheDir, "$APK_NAME.part")
        return try {
            setStatus("Buscando actualización de IbizaCompanion…")
            connection = URL(DOWNLOAD_URL).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("User-Agent", "IbizaCompanion-Updater")
            val etag = readMetaString(context, "etag")
            val lastModified = readMetaString(context, "lastModified")
            if (!etag.isNullOrBlank()) connection.setRequestProperty("If-None-Match", etag)
            if (!lastModified.isNullOrBlank()) {
                connection.setRequestProperty("If-Modified-Since", lastModified)
            }
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED && cached != null) {
                writeMeta(context, cached, etag, lastModified)
                return cached
            }
            if (code !in 200..299) {
                Log.w(TAG, "GitHub respondió HTTP $code")
                return cached
            }

            setStatus("Descargando actualización…")
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
            if (info == null || info.packageName != context.packageName) {
                Log.w(TAG, "el APK descargado no es IbizaCompanion")
                tmp.delete()
                return cached
            }
            val apk = apkFile(context)
            if (apk.exists()) apk.delete()
            if (!tmp.renameTo(apk)) {
                tmp.copyTo(apk, overwrite = true)
                tmp.delete()
            }
            val latest = Latest(
                versionCode = info.versionCode,
                versionName = info.versionName ?: "?",
                file = apk
            )
            writeMeta(context, latest, connection.getHeaderField("ETag"), connection.getHeaderField("Last-Modified"))
            Log.i(TAG, "APK de GitHub: v${latest.versionName} (${latest.versionCode}) ${apk.length()}B")
            latest
        } catch (e: Exception) {
            Log.w(TAG, "download: ${e.message}")
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

    private fun readCached(context: Context): Latest? {
        val apk = apkFile(context)
        val meta = metaFile(context)
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
            if (sha.isNotBlank() && sha256Hex(apk) != sha.lowercase()) {
                apk.delete()
                meta.delete()
                return null
            }
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.GET_META_DATA
            )
            if (info == null || info.packageName != context.packageName) {
                apk.delete()
                meta.delete()
                return null
            }
            Latest(versionCode, versionName, apk)
        } catch (_: Exception) {
            null
        }
    }

    private fun readCheckedAt(context: Context): Long = try {
        val meta = metaFile(context)
        if (meta.exists()) JSONObject(meta.readText()).optLong("checkedAt", 0L) else 0L
    } catch (_: Exception) {
        0L
    }

    private fun readMetaString(context: Context, key: String): String? = try {
        val meta = metaFile(context)
        if (!meta.exists()) null
        else JSONObject(meta.readText()).optString(key, "").takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private fun writeMeta(context: Context, latest: Latest, etag: String?, lastModified: String?) {
        try {
            metaFile(context).writeText(
                JSONObject()
                    .put("versionCode", latest.versionCode)
                    .put("versionName", latest.versionName)
                    .put("size", latest.file.length())
                    .put("sha256", sha256Hex(latest.file))
                    .put("etag", etag ?: "")
                    .put("lastModified", lastModified ?: "")
                    .put("checkedAt", System.currentTimeMillis())
                    .toString()
            )
        } catch (e: Exception) {
            Log.w(TAG, "writeMeta: ${e.message}")
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

/** Recibe el resultado de la instalación de la propia app. */
class CompanionInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CompanionUpdater.ACTION_INSTALL_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
        Log.i("CompanionInstall", "status=$status msg=$message")
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
                }
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirmIntent)
                    } catch (e: Exception) {
                        Log.w("CompanionInstall", "confirm: ${e.message}")
                    }
                } else {
                    CompanionUpdater.fallbackInstall(context)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                CompanionStateHolder.success()
            }
            else -> CompanionStateHolder.failure(message)
        }
    }
}

/** Actualiza el estado de la UI desde el receiver sin exponer detalles internos. */
private object CompanionStateHolder {
    fun success() {
        CompanionState.update {
            it.copy(
                selfUpdating = false,
                selfUpdateAvailable = false,
                selfUpdateStatus = "Actualización instalada"
            )
        }
    }

    fun failure(message: String) {
        CompanionState.update {
            it.copy(
                selfUpdating = false,
                selfUpdateStatus = if (message.isBlank()) "La instalación falló" else "Falló: $message"
            )
        }
    }
}
