package com.chronosbvrx.ibizadash.media

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

object MediaKeys {
    fun send(context: Context, keyCode: Int) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (_: Exception) {
        }
    }
}
