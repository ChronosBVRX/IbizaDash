package com.chronosbvrx.ibizacompanion

import android.content.Context
import android.media.AudioManager

/**
 * Convivencia Wi-Fi Direct + Bluetooth (A2DP) en el POCO.
 *
 * El audio va del POCO al estéreo por Bluetooth 2,4 GHz y los datos por el
 * mismo 2,4 GHz (Wi-Fi Direct). La ráfaga de Wi-Fi TX del teléfono agota el
 * tiempo de antena del BT y el estéreo se queda sin buffer: se escucha
 * cortado. Cuando suena música, los envíos grandes (tiles, módulos, OTA,
 * voces) se espacian para dejar huecos al BT.
 */
object AudioCoexistence {
    /**
     * ¿Suena música (local o por Bluetooth)? Es el estado que más corta el
     * A2DP cuando el POCO transmite Wi-Fi a la vez.
     */
    fun isMusicPlaying(context: Context): Boolean {
        return try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audio?.isMusicActive == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Ritmo máximo para envíos grandes. Sin música se mantiene holgado;
     * con música se baja para no ahogar el A2DP.
     */
    fun bulkBytesPerSec(context: Context): Long =
        if (isMusicPlaying(context)) 60_000L else 250_000L

    /** Pausa sugerida tras un bloque grande, para dejar hueco al BT. */
    fun paceMs(context: Context, bytes: Long): Long {
        val rate = bulkBytesPerSec(context)
        return (bytes * 1000L / rate).coerceIn(0L, 400L)
    }
}
