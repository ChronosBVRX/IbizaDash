package com.chronosbvrx.ibizadash

import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import android.util.Log
import com.chronosbvrx.ibizadash.media.BluetoothMediaReceiver
import com.chronosbvrx.ibizadash.music.OfflineMusicCatalog
import org.osmdroid.config.Configuration
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        val prefs = getSharedPreferences("osmdroid", MODE_PRIVATE)
        Configuration.getInstance().load(this, prefs)
        Configuration.getInstance().userAgentValue = packageName

        val base = File(getExternalFilesDir(null), "osmdroid")
        base.mkdirs()
        val tiles = File(base, "tiles")
        tiles.mkdirs()
        Configuration.getInstance().osmdroidBasePath = base
        Configuration.getInstance().osmdroidTileCache = tiles

        // Caché en RAM estricta para el A7: 16 teselas + 2 de overshoot.
        com.chronosbvrx.ibizadash.map.MapHelper.configureRamCaps()

        // osmdroid 6.1 guarda la caché como "<z>/<x>/<y>.tile". Los módulos y
        // el teléfono enviaban ".png", así que el mapa no las encontraba.
        migrateTileExtensions(tiles)

        // Pre-set the Bluetooth pairing PIN used by this head unit. The stock
        // pairing service reads persist.service.bt.pincode and does not show a
        // PIN dialog, so ELM327 adapters (1234/0000) fail unless we set it.
        try {
            val pin = Prefs.obdPin(this)
            Runtime.getRuntime()
                .exec(arrayOf("setprop", "persist.service.bt.pincode", pin))
                .waitFor()
            Log.i("App", "Bluetooth PIN preparado: $pin")
        } catch (e: Exception) {
            Log.w("App", "No se pudo preparar el PIN Bluetooth: ${e.message}")
        }

        BluetoothMediaReceiver.register(this)

        OfflineMusicCatalog.initialize(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                NotificationListenerService.requestRebind(
                    ComponentName(this, com.chronosbvrx.ibizadash.media.MediaListenerService::class.java)
                )
            } catch (_: Exception) {
            }
            try {
                NotificationListenerService.requestRebind(
                    ComponentName(this, com.chronosbvrx.ibizadash.nav.NavNotificationListenerService::class.java)
                )
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Deja la última excepción no capturada en
     * files/crash_last.txt (interno y externo de la app) para poder
     * diagnosticar reinicios del launcher sin depender de logcat.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stack = StringWriter().also { writer ->
                    throwable.printStackTrace(PrintWriter(writer))
                }.toString()
                val version = try {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
                } catch (_: Exception) {
                    "?"
                }
                val text = buildString {
                    append("IbizaDash crash\n")
                    append("version: ").append(version).append('\n')
                    append("time: ").append(
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    ).append('\n')
                    append("thread: ").append(thread.name).append("\n\n")
                    append(stack)
                }
                for (dir in listOfNotNull(getExternalFilesDir(null), filesDir)) {
                    try {
                        File(dir, "crash_last.txt").writeText(text)
                    } catch (_: Exception) {
                    }
                }
                Log.e("CrashLogger", text)
            } catch (_: Exception) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    private fun migrateTileExtensions(tiles: File) {
        val prefs = getSharedPreferences("osmdroid_migration", MODE_PRIVATE)
        if (prefs.getBoolean("png_to_tile_v1", false)) return
        var renamed = 0
        try {
            // Base y etiquetas usan la misma caché de osmdroid (una carpeta
            // por nombre de fuente).
            for (source in arrayOf("EsriDark", "EsriDarkRef")) {
                val root = File(tiles, source)
                if (!root.exists()) continue
                root.walkTopDown()
                    .filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
                    .forEach { png ->
                        val tile = File(png.parentFile, png.nameWithoutExtension + ".tile")
                        try {
                            if (!tile.exists() && !png.renameTo(tile)) {
                                png.copyTo(tile, overwrite = true)
                                png.delete()
                            } else if (tile.exists()) {
                                png.delete()
                            }
                            renamed++
                        } catch (_: Exception) {
                        }
                    }
            }
        } catch (e: Exception) {
            Log.w("App", "migración de teselas: ${e.message}")
        }
        prefs.edit().putBoolean("png_to_tile_v1", true).apply()
        Log.i("App", "teselas migradas a .tile: $renamed")
    }
}
