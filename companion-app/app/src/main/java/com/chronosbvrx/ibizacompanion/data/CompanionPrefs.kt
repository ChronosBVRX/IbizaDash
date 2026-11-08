package com.chronosbvrx.ibizacompanion.data

import android.content.Context

/** Preferencias propias de IbizaCompanion. */
object CompanionPrefs {
    private const val FILE = "companion_prefs"
    private const val KEY_AUTO_UPDATE = "auto_update"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Revisar GitHub en segundo plano al abrir la app y cada 6 horas.
     * Se puede desactivar desde Ajustes; la acción manual "Buscar ahora"
     * sigue disponible.
     */
    fun autoUpdate(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_UPDATE, true)

    fun setAutoUpdate(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
    }
}
