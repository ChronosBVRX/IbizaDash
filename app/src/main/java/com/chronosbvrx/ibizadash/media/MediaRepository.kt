package com.chronosbvrx.ibizadash.media

import android.graphics.Bitmap
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.CopyOnWriteArraySet

object MediaRepository {
    private val listeners = CopyOnWriteArraySet<(NowPlaying?) -> Unit>()
    private val handler = Handler(Looper.getMainLooper())

    private var controller: MediaController? = null
    private var demo = false
    private var lastBluetooth: NowPlaying? = null
    private var lastBluetoothAt = 0L

    var current: NowPlaying? = null
        private set

    fun addListener(listener: (NowPlaying?) -> Unit) {
        listeners.add(listener)
        listener(current)
    }

    fun removeListener(listener: (NowPlaying?) -> Unit) {
        listeners.remove(listener)
    }

    fun update(nowPlaying: NowPlaying?, mediaController: MediaController?) {
        if (demo) return
        // A2DP metadata comes from broadcasts, not from a local MediaSession.
        // Don't let an empty session list erase a track that is still playing.
        if (nowPlaying == null) {
            val bt = lastBluetooth
            if (bt != null && SystemClock.elapsedRealtime() - lastBluetoothAt < BT_TTL_MS) {
                current = bt
                controller = null
                notifyListeners(bt)
                return
            }
        }
        current = nowPlaying
        controller = mediaController
        notifyListeners(nowPlaying)
    }

    fun updateBluetooth(nowPlaying: NowPlaying?) {
        if (demo) return
        if (nowPlaying == null) {
            lastBluetooth = null
            if (current?.isBluetooth == true) {
                current = null
                controller = null
                notifyListeners(null)
            }
            return
        }
        lastBluetooth = nowPlaying
        lastBluetoothAt = SystemClock.elapsedRealtime()
        current = nowPlaying
        controller = null
        notifyListeners(nowPlaying)
    }

    fun setDemo(enabled: Boolean) {
        demo = enabled
        if (enabled) {
            current = DemoMedia.nowPlaying()
            controller = null
            notifyListeners(current)
        } else {
            current = lastBluetooth
            controller = null
            notifyListeners(current)
        }
    }

    fun isDemo(): Boolean = demo

    fun playPause(): Boolean {
        if (demo) {
            val track = current ?: return false
            current = track.copy(isPlaying = !track.isPlaying)
            notifyListeners(current)
            return true
        }
        val mediaController = controller ?: return false
        val playing = mediaController.playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing) {
            mediaController.transportControls.pause()
        } else {
            mediaController.transportControls.play()
        }
        return true
    }

    fun next(): Boolean {
        if (demo) return false
        val mediaController = controller ?: return false
        mediaController.transportControls.skipToNext()
        return true
    }

    fun prev(): Boolean {
        if (demo) return false
        val mediaController = controller ?: return false
        mediaController.transportControls.skipToPrevious()
        return true
    }

    fun hasController(): Boolean = controller != null

    fun updateArtwork(artwork: Bitmap?) {
        val c = current ?: return
        current = c.copy(artwork = artwork)
        notifyListeners(current)
    }

    /**
     * Metadatos/portada que llegan del teléfono (Wi-Fi o Bluetooth).
     * No pisa una reproducción local que no venga del teléfono.
     */
    fun updateFromPhone(
        title: String,
        artist: String,
        album: String,
        isPlaying: Boolean,
        artwork: Bitmap?,
        packageName: String = "phone",
        sourceLabel: String = ""
    ) {
        val c = current
        if (c != null && !c.isBluetooth) return
        current = if (c == null) {
            NowPlaying(
                title = title,
                artist = artist,
                album = album,
                artwork = artwork,
                isPlaying = isPlaying,
                packageName = packageName.ifBlank { "phone" },
                isBluetooth = true,
                sourceLabel = sourceLabel
            )
        } else {
            // Si cambia la canción y no llegó portada, no dejar la anterior:
            // parecería que el reproductor nativo muestra una carátula falsa.
            val sameTitle = c.title == title || title.isBlank()
            c.copy(
                // Preferir siempre el dato nuevo; solo conservar el anterior
                // cuando el mensaje llega vacío (actualización parcial).
                title = title.ifBlank { c.title },
                artist = artist.ifBlank { c.artist },
                album = album.ifBlank { c.album },
                artwork = artwork ?: if (sameTitle) c.artwork else null,
                isPlaying = isPlaying,
                packageName = packageName.ifBlank { c.packageName },
                sourceLabel = sourceLabel.ifBlank { c.sourceLabel }
            )
        }
        notifyListeners(current)
    }

    private fun notifyListeners(nowPlaying: NowPlaying?) {
        handler.post {
            listeners.forEach { it(nowPlaying) }
        }
    }

    private const val BT_TTL_MS = 30_000L
}
