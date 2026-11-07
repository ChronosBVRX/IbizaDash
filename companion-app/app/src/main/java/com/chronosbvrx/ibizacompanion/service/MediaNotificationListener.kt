package com.chronosbvrx.ibizacompanion.service

import android.app.Notification
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.chronosbvrx.ibizacompanion.BridgeLink
import org.json.JSONObject

class MediaNotificationListener : NotificationListenerService() {
    companion object {
        private const val TAG = "NotifyFwd"
        private const val MIN_INTERVAL_MS = 2500L
        private val MEDIA_PACKAGES = setOf(
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.google.android.youtube",
            "com.amazon.mp3",
            "deezer.android.app",
            "com.apple.android.music",
            "com.soundcloud.android"
        )
    }

    private var lastForwardMs = 0L

    /** La red no puede ir en el hilo principal (NetworkOnMainThread). */
    private val sender = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "NotifyTx").apply { isDaemon = true }
    }

    // La presencia de este servicio (activado en Ajustes → Acceso a
    // notificaciones) es necesaria para MediaSessionManager y, además,
    // reenvía avisos al estéreo (Fase 1).
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val sbn = sbn ?: return
        val n = sbn.notification ?: return
        if (sbn.packageName == packageName) return
        if (MEDIA_PACKAGES.contains(sbn.packageName)) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val category = n.category.orEmpty()
        if (category == Notification.CATEGORY_TRANSPORT ||
            category == Notification.CATEGORY_PROGRESS ||
            category == Notification.CATEGORY_SYSTEM ||
            category == Notification.CATEGORY_SERVICE
        ) {
            return
        }
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastForwardMs < MIN_INTERVAL_MS) return
        lastForwardMs = now
        val app = try {
            val info = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            sbn.packageName
        }
        try {
            val payload = JSONObject()
                .put("type", "NOTIFY")
                .put("app", app)
                .put("title", title)
                .put("text", text)
                .put("category", category)
            sender.execute {
                try {
                    val ok = BridgeLink.send(payload)
                    Log.i(TAG, "notify $app → $ok")
                } catch (e: Exception) {
                    Log.w(TAG, "send: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "send: ${e.message}")
        }
    }
}
