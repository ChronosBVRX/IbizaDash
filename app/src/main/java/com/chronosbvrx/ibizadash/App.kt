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

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("osmdroid", MODE_PRIVATE)
        Configuration.getInstance().load(this, prefs)
        Configuration.getInstance().userAgentValue = packageName

        val base = File(getExternalFilesDir(null), "osmdroid")
        base.mkdirs()
        val tiles = File(base, "tiles")
        tiles.mkdirs()
        Configuration.getInstance().osmdroidBasePath = base
        Configuration.getInstance().osmdroidTileCache = tiles

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
}
