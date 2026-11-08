package com.chronosbvrx.ibizadash.ota

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.FileInputStream

/**
 * Instala un APK recibido por OTA.
 *
 * Vía principal: PackageInstaller (sesión). En API 27 una app no privilegiada
 * no instala en silencio: el sistema muestra su diálogo de confirmación y el
 * usuario toca "Instalar" una vez. Si la sesión falla (p. ej. falta el permiso
 * de "orígenes desconocidos"), se cae al intent ACTION_VIEW con FileProvider,
 * que pide lo mismo por el camino clásico.
 */
object OtaInstaller {
    private const val TAG = "OtaInstaller"
    const val ACTION_STATUS = "com.chronosbvrx.ibizadash.OTA_INSTALL_STATUS"

    fun install(context: Context, pending: OtaReceiver.PendingUpdate) {
        if (!pending.file.exists()) {
            Toast.makeText(context, "El APK de la actualización no está", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            installViaSession(context, pending)
        } catch (e: SecurityException) {
            Log.w(TAG, "sesión sin permiso, fallback a intent: ${e.message}")
            installViaIntent(context, pending)
        } catch (e: Exception) {
            Log.w(TAG, "sesión falló, fallback a intent: ${e.message}")
            installViaIntent(context, pending)
        }
    }

    private fun installViaSession(context: Context, pending: OtaReceiver.PendingUpdate) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            FileInputStream(pending.file).use { input ->
                session.openWrite("ibizadash", 0, pending.file.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val statusIntent = Intent(ACTION_STATUS).apply {
                setPackage(context.packageName)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE
                } else {
                    0
                }
            val statusPending = PendingIntent.getBroadcast(
                context, sessionId, statusIntent, flags
            )
            session.commit(statusPending.intentSender)
            Log.i(TAG, "sesión $sessionId enviada a instalar v${pending.versionName}")
        } finally {
            try {
                session.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun installViaIntent(context: Context, pending: OtaReceiver.PendingUpdate) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pending.file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "intent install falló: ${e.message}")
            Toast.makeText(
                context,
                "No se pudo iniciar la instalación: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

/** Recibe el resultado de la sesión de PackageInstaller. */
class OtaInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != OtaInstaller.ACTION_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
        Log.i("OtaInstall", "status=$status $message")
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                OtaReceiver.clearPending(context)
                Toast.makeText(
                    context,
                    "IbizaDash actualizado. Reinicia la app si no arranca sola.",
                    Toast.LENGTH_LONG
                ).show()
            }
            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                Toast.makeText(
                    context,
                    "La actualización falló: $message",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
