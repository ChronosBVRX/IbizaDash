package com.chronosbvrx.ibizadash.ui

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator

/**
 * Capa de movimiento del launcher.
 *
 * Solo anima propiedades de render node (alpha / translation / scale). No crea
 * bitmaps, no activa capas hardware, no usa TransitionManager y nunca anima
 * layout params ni pesos: no hay buffers de píxeles extra ni relayouts por
 * frame. Así las transiciones se sienten premium sin castigar la RAM del
 * estéreo (2 GB) ni forzar el redibujado de osmdroid.
 *
 * Las duraciones siguen el ajuste "Escala de animación" del sistema y se
 * acortan en dispositivos low-RAM. Si las animaciones están desactivadas, los
 * llamadores aplican el estado final de inmediato.
 */
object Motion {

    /** Curva base: salida rápida, llegada suave. */
    val standard: Interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)

    /** Curva enfatizada para entradas: arranque ágil y asentamiento largo. */
    val emphasized: Interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

    private var scale = 1f

    fun init(context: Context) {
        val systemScale = try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        } catch (_: Exception) {
            1f
        }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowRam = am?.isLowRamDevice == true
        scale = when {
            systemScale <= 0.01f -> 0f
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !ValueAnimator.areAnimatorsEnabled() -> 0f
            lowRam -> 0.78f
            else -> 1f
        }
    }

    fun enabled(): Boolean = scale > 0f

    /** Duración efectiva, acotada para que nunca desaparezca la sensación. */
    fun duration(baseMs: Long): Long {
        if (scale <= 0f) return 0L
        val floor = minOf(70L, baseMs)
        return (baseMs * scale).toLong().coerceIn(floor, baseMs)
    }

    /**
     * Entra desde un desplazamiento con fade. El llamador controla visibility.
     * [fromAlpha] > 0 ayuda a que el pane no quede vacío cuando el panel
     * saliente se oculta de golpe por un cambio de split.
     */
    fun enter(
        view: View,
        dx: Float,
        dy: Float,
        baseDurationMs: Long,
        delayMs: Long = 0L,
        fromAlpha: Float = 0f,
        endAction: (() -> Unit)? = null
    ) {
        if (!enabled()) {
            reset(view)
            endAction?.invoke()
            return
        }
        view.animate().cancel()
        view.alpha = fromAlpha.coerceIn(0f, 1f)
        view.translationX = dx
        view.translationY = dy
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .translationY(0f)
            .setDuration(duration(baseDurationMs))
            .setStartDelay(delayMs)
            .setInterpolator(emphasized)
            .withEndAction {
                reset(view)
                endAction?.invoke()
            }
            .start()
    }

    /** Sale hacia un desplazamiento con fade. El endAction suele ocultar. */
    fun exit(
        view: View,
        dx: Float,
        dy: Float,
        baseDurationMs: Long,
        endAction: (() -> Unit)? = null
    ) {
        if (!enabled()) {
            endAction?.invoke()
            return
        }
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .translationX(dx)
            .translationY(dy)
            .setDuration(duration(baseDurationMs))
            .setInterpolator(standard)
            .withEndAction {
                reset(view)
                endAction?.invoke()
            }
            .start()
    }

    /** Fade puro para pastillas/scrims; no toca translaciones. */
    fun fadeIn(
        view: View,
        baseDurationMs: Long = 200L,
        endAction: (() -> Unit)? = null
    ) {
        if (!enabled()) {
            view.alpha = 1f
            endAction?.invoke()
            return
        }
        view.animate().cancel()
        view.alpha = 0f
        view.animate()
            .alpha(1f)
            .setDuration(duration(baseDurationMs))
            .setInterpolator(standard)
            .withEndAction {
                view.alpha = 1f
                endAction?.invoke()
            }
            .start()
    }

    fun fadeOut(
        view: View,
        baseDurationMs: Long = 160L,
        endAction: (() -> Unit)? = null
    ) {
        if (!enabled()) {
            endAction?.invoke()
            return
        }
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .setDuration(duration(baseDurationMs))
            .setInterpolator(standard)
            .withEndAction {
                view.alpha = 1f
                endAction?.invoke()
            }
            .start()
    }

    /** Pulso corto para el icono activo del dock. */
    fun pulse(
        view: View,
        peak: Float = 1.10f,
        upMs: Long = 90L,
        downMs: Long = 160L
    ) {
        if (!enabled()) {
            resetScale(view)
            return
        }
        view.animate().cancel()
        view.animate()
            .scaleX(peak)
            .scaleY(peak)
            .setDuration(duration(upMs))
            .setInterpolator(standard)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(duration(downMs))
                    .setInterpolator(emphasized)
                    .withEndAction { resetScale(view) }
                    .start()
            }
            .start()
    }

    fun reset(view: View) {
        view.alpha = 1f
        view.translationX = 0f
        view.translationY = 0f
    }

    fun resetScale(view: View) {
        view.scaleX = 1f
        view.scaleY = 1f
    }
}
