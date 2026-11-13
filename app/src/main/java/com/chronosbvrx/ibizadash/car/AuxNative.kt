package com.chronosbvrx.ibizadash.car

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.chronosbvrx.ibizadash.MainActivity

/**
 * Selecciona la fuente AUX usando la app nativa `com.tw.auxin`.
 *
 * El servicio `com.tw.service.xt` ignora los `twdata` de AUX (probado en el
 * estéreo: la fuente se queda en 3). La única ruta que sí deja `SourceValue=7`
 * es el `AuxInActivity` nativo. Se abre durante ~500 ms para que el MCU
 * enganche la entrada AUX, y luego se vuelve al launcher. El `onPause` del
 * activity nativo no borra la fuente: el MCU mantiene el 7 y el audio sigue
 * entrando por el cable.
 */
object AuxNative {
    private const val TAG = "AuxNative"
    private val handler = Handler(Looper.getMainLooper())
    private var selecting = false

    fun selectAux(activity: Activity, onReturn: () -> Unit = {}) {
        if (selecting) return
        selecting = true
        val aux = Intent()
            .setClassName("com.tw.auxin", "com.tw.auxin.AuxInActivity")
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        val started = try {
            activity.applicationContext.startActivity(aux)
            true
        } catch (e: Exception) {
            Log.w(TAG, "AuxInActivity no disponible: ${e.message}")
            false
        }
        if (!started) {
            selecting = false
            onReturn()
            return
        }
        handler.postDelayed({
            try {
                val back = Intent(activity.applicationContext, MainActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                    )
                activity.applicationContext.startActivity(back)
            } catch (e: Exception) {
                Log.w(TAG, "volver al launcher: ${e.message}")
            } finally {
                selecting = false
                onReturn()
            }
        }, 550L)
    }
}
