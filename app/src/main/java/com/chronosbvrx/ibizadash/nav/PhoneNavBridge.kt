package com.chronosbvrx.ibizadash.nav

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.chronosbvrx.ibizadash.Prefs

/**
 * Puente de navegación "phone-first".
 *
 * El teléfono calcula GPS, progreso, próximo giro y ETA; el estéreo solo
 * publica el estado en la UI. Si el teléfono deja de mandar estados (se
 * durmió, perdió el enlace o se cerró la app), el estéreo recupera su
 * [RouteNavigator] local automáticamente.
 */
object PhoneNavBridge {
    private const val TAG = "PhoneNav"
    private const val TIMEOUT_MS = 10_000L

    @Volatile
    var active: Boolean = false
        private set

    /**
     * El GPS del estéreo es la fuente por defecto. El teléfono solo se usa
     * como respaldo manual para no enviar posiciones cada segundo.
     */
    @Volatile
    var usePhoneGps: Boolean = false
        private set

    @Volatile
    private var lastStateMs = 0L

    @Volatile
    private var appContext: Context? = null

    private var watchdog: Thread? = null

    fun start(context: Context) {
        appContext = context.applicationContext
        usePhoneGps = Prefs.usePhoneGps(context)
        if (watchdog == null) {
            watchdog = Thread {
                while (true) {
                    try {
                        Thread.sleep(3000L)
                        if (!active) continue
                        if (SystemClock.elapsedRealtime() - lastStateMs <= TIMEOUT_MS) continue
                        active = false
                        Log.w(TAG, "sin estado del teléfono; vuelve la guía local")
                        val ctx = appContext
                        if (ctx != null && NavRouteRepository.current != null) {
                            RouteNavigator.start(ctx)
                        }
                    } catch (_: InterruptedException) {
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "watchdog: ${e.message}")
                    }
                }
            }.apply {
                name = "PhoneNavWatch"
                isDaemon = true
                start()
            }
        }
    }

    /** Llega un estado calculado por el teléfono. */
    fun onState(context: Context, state: NavState) {
        appContext = context.applicationContext
        // Fuente por defecto: GPS del coche. Se ignora el estado del teléfono
        // para no gastar enlace ni competir con RouteNavigator.
        if (!usePhoneGps) return
        active = true
        lastStateMs = SystemClock.elapsedRealtime()
        start(context)
        // El móvil manda; la guía local no debe competir con ella.
        if (RouteNavigator.isRunning()) RouteNavigator.stop()
        NavStateRepository.publish(state)
        NavRepository.publish(state.actionText, state.remainingText, state.distText)
    }

    /** Cambia la fuente en caliente (Ajustes → GPS del teléfono). */
    fun setUsePhoneGps(enabled: Boolean) {
        usePhoneGps = enabled
        if (!enabled) {
            active = false
            lastStateMs = 0L
        }
    }

    /** Ruta borrada o reiniciada: el próximo ROUTE_SET decide el modo. */
    fun reset() {
        active = false
        lastStateMs = 0L
    }
}
