package com.chronosbvrx.ibizadash.media

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MediaListenerService : NotificationListenerService() {

    private lateinit var manager: MediaSessionManager
    private val component by lazy { ComponentName(this, MediaListenerService::class.java) }
    private val controllers = mutableMapOf<MediaController, MediaController.Callback>()
    private val artCache = mutableMapOf<String, Bitmap>()
    private var listenerAdded = false

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list ->
            updateSessions(list)
        }

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (!listenerAdded) {
            try {
                manager.addOnActiveSessionsChangedListener(sessionsListener, component)
                listenerAdded = true
            } catch (e: Exception) {
                Log.w(TAG, "addOnActiveSessionsChangedListener: ${e.message}")
            }
        }
        refresh()
    }

    override fun onListenerDisconnected() {
        if (listenerAdded) {
            try {
                manager.removeOnActiveSessionsChangedListener(sessionsListener)
            } catch (_: Exception) {
            }
            listenerAdded = false
        }
        unregisterCallbacks()
        MediaRepository.update(null, null)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        refresh()
        if (controllers.isNotEmpty()) return
        if (!isMediaNotification(notification, sbn.packageName)) return

        @Suppress("DEPRECATION")
        val token = notification.extras
            .getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        if (token != null) {
            try {
                val controller = MediaController(this, token)
                registerController(controller)
                publish()
                return
            } catch (e: Exception) {
                Log.w(TAG, "MediaController(token): ${e.message}")
            }
        }

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
        if (title.isNullOrBlank()) return

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            ?: ""
        @Suppress("DEPRECATION")
        val artwork = extras.getParcelable<Bitmap>(Notification.EXTRA_LARGE_ICON_BIG)
            ?: extras.getParcelable<Bitmap>(Notification.EXTRA_LARGE_ICON)

        val playing = notification.actions?.any { action ->
            val label = action.title?.toString()?.lowercase().orEmpty()
            label.contains("pause") || label.contains("pausar") || label.contains("pausa")
        } == true

        MediaRepository.update(
            NowPlaying(
                title = title,
                artist = text,
                album = "",
                artwork = artwork,
                isPlaying = playing,
                packageName = sbn.packageName,
                isBluetooth = isBluetooth(sbn.packageName)
            ),
            null
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refresh()
    }

    override fun onDestroy() {
        if (listenerAdded) {
            try {
                manager.removeOnActiveSessionsChangedListener(sessionsListener)
            } catch (_: Exception) {
            }
        }
        unregisterCallbacks()
        super.onDestroy()
    }

    private fun refresh() {
        try {
            updateSessions(manager.getActiveSessions(component))
        } catch (e: Exception) {
            Log.w(TAG, "getActiveSessions: ${e.message}")
        }
    }

    private fun updateSessions(list: List<MediaController>?) {
        unregisterCallbacks()
        if (list.isNullOrEmpty()) {
            MediaRepository.update(null, null)
            return
        }
        list.forEach { registerController(it) }
        publish()
    }

    private fun registerController(controller: MediaController) {
        if (controllers.containsKey(controller)) return
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                publish()
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                publish()
            }
        }
        try {
            controller.registerCallback(callback)
            controllers[controller] = callback
        } catch (e: Exception) {
            Log.w(TAG, "registerCallback: ${e.message}")
        }
    }

    private fun unregisterCallbacks() {
        for ((controller, callback) in controllers) {
            try {
                controller.unregisterCallback(callback)
            } catch (_: Exception) {
            }
        }
        controllers.clear()
    }

    private fun publish() {
        val active = controllers.keys.toList()
        if (active.isEmpty()) {
            MediaRepository.update(null, null)
            return
        }
        val controller = active.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING && it.metadata != null
        } ?: active.firstOrNull { it.metadata != null } ?: active.first()

        val metadata = controller.metadata
        if (metadata != null) {
            Log.i(
                TAG,
                "session pkg=${controller.packageName} keys=" +
                    metadata.keySet().joinToString() +
                    " title='${metadata.getText(MediaMetadata.METADATA_KEY_TITLE)}'" +
                    " displayTitle='${metadata.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)}'" +
                    " artist='${metadata.getText(MediaMetadata.METADATA_KEY_ARTIST)}'" +
                    " album='${metadata.getText(MediaMetadata.METADATA_KEY_ALBUM)}'"
            )
        }
        val title = metadata?.getText(MediaMetadata.METADATA_KEY_TITLE)?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: metadata?.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.toString()
                ?.takeIf { it.isNotBlank() }
            ?: "Reproduciendo"
        val artist = metadata?.getText(MediaMetadata.METADATA_KEY_ARTIST)?.toString()
            ?: metadata?.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.toString()
            ?: ""
        val album = metadata?.getText(MediaMetadata.METADATA_KEY_ALBUM)?.toString() ?: ""

        val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        val packageName = controller.packageName ?: ""
        val bluetooth = isBluetooth(packageName)
        val position = controller.playbackState?.position ?: 0L
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

        val nowPlaying = NowPlaying(
            title = title,
            artist = artist,
            album = album,
            artwork = bitmap,
            isPlaying = playing,
            packageName = packageName,
            isBluetooth = bluetooth,
            positionMs = position,
            durationMs = duration
        )
        MediaRepository.update(nowPlaying, controller)

        if (bitmap == null) {
            val uri = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            if (!uri.isNullOrEmpty()) {
                loadArt(uri) { art ->
                    MediaRepository.update(nowPlaying.copy(artwork = art), controller)
                }
            }
        }
    }

    private fun loadArt(uri: String, onLoaded: (Bitmap?) -> Unit) {
        artCache[uri]?.let {
            onLoaded(it)
            return
        }
        Thread {
            val bitmap = try {
                contentResolver.openInputStream(Uri.parse(uri))?.use {
                    BitmapFactory.decodeStream(it)
                }
            } catch (e: Exception) {
                null
            }
            if (bitmap != null) {
                artCache[uri] = bitmap
                onLoaded(bitmap)
            }
        }.apply { isDaemon = true }.start()
    }

    @Suppress("DEPRECATION")
    private fun isBluetooth(packageName: String): Boolean {
        if (packageName.contains("bluetooth", ignoreCase = true) ||
            packageName == "com.tw.bt"
        ) {
            return true
        }
        return try {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.isBluetoothA2dpOn
        } catch (_: Exception) {
            false
        }
    }

    private fun isMediaNotification(notification: Notification, packageName: String): Boolean {
        if (notification.category == Notification.CATEGORY_CALL) return false
        if (notification.category == Notification.CATEGORY_TRANSPORT) return true
        if (notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return true
        val hasActions = notification.actions?.any { it.title != null } == true
        return hasActions &&
            (packageName.contains("bluetooth", ignoreCase = true) || packageName == "com.tw.bt")
    }

    companion object {
        private const val TAG = "MediaListenerService"
    }
}
