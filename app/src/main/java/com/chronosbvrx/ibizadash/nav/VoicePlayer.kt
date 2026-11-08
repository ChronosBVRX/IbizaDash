package com.chronosbvrx.ibizadash.nav

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import java.io.File

/**
 * Reproduce los avisos de voz de la navegación.
 *
 * Usa el canal de "indicaciones de navegación" para que el sistema baje la
 * música automáticamente (ducking) mientras habla. Los clips son WAV cortos
 * generados por el teléfono; aquí solo se encadenan y se liberan al terminar.
 */
object VoicePlayer {
    private const val TAG = "VoicePlayer"

    private var player: MediaPlayer? = null
    private val queue = ArrayDeque<File>()
    private var focusHeld = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { }

    /** Detiene lo que suene y reproduce la secuencia indicada. */
    fun speak(context: Context, clips: List<File>) {
        if (clips.isEmpty() || !com.chronosbvrx.ibizadash.Prefs.voiceOn(context)) return
        stop(context)
        synchronized(queue) { clips.forEach { queue.addLast(it) } }
        playNext(context)
    }

    fun stop(context: Context) {
        synchronized(queue) { queue.clear() }
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        abandonFocus(context)
    }

    private fun playNext(context: Context) {
        val next: File = synchronized(queue) { queue.removeFirstOrNull() } ?: run {
            abandonFocus(context)
            return
        }
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(next.absolutePath)
            mp.setOnCompletionListener { p ->
                try {
                    p.release()
                } catch (_: Exception) {
                }
                if (player === p) player = null
                playNext(context)
            }
            mp.setOnErrorListener { p, what, extra ->
                Log.w(TAG, "MediaPlayer error $what/$extra en ${next.name}")
                try {
                    p.release()
                } catch (_: Exception) {
                }
                if (player === p) player = null
                playNext(context)
                true
            }
            requestFocus(context)
            player = mp
            mp.prepare()
            mp.start()
            Log.i(TAG, "hablando: ${next.name} (${next.length()}B)")
        } catch (e: Exception) {
            Log.w(TAG, "reproducir ${next.name}: ${e.message}")
            try {
                player?.release()
            } catch (_: Exception) {
            }
            player = null
            playNext(context)
        }
    }

    @Suppress("DEPRECATION")
    private fun requestFocus(context: Context) {
        if (focusHeld) return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            val result = am.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
            focusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun abandonFocus(context: Context) {
        if (!focusHeld) return
        focusHeld = false
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            am.abandonAudioFocus(focusListener)
        } catch (_: Exception) {
        }
    }
}
