package com.chronosbvrx.ibizacompanion.data

import android.content.Context

/** Preferencias propias de IbizaCompanion. */
object CompanionPrefs {
    private const val FILE = "companion_prefs"
    private const val KEY_AUTO_UPDATE = "auto_update"
    private const val KEY_TRAFFIC = "traffic_key"
    private const val KEY_MODULE_MANIFEST = "module_manifest_url"

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

    /**
     * Clave del proveedor de tráfico (TomTom).
     *
     * Vive en las preferencias del teléfono, no en el APK: el APK se publica en
     * GitHub y una clave compilada quedaría expuesta a cualquiera.
     */
    fun trafficKey(context: Context): String =
        prefs(context).getString(KEY_TRAFFIC, "").orEmpty().trim()

    fun setTrafficKey(context: Context, key: String) {
        prefs(context).edit()
            .putString(KEY_TRAFFIC, key.trim())
            .apply()
    }

    /**
     * URL alternativa del catálogo de módulos. Vacío = el release de GitHub.
     * Sirve para probar con un servidor local sin publicar nada.
     */
    fun moduleManifestUrl(context: Context): String =
        prefs(context).getString(KEY_MODULE_MANIFEST, "").orEmpty().trim()

    fun setModuleManifestUrl(context: Context, url: String) {
        prefs(context).edit()
            .putString(KEY_MODULE_MANIFEST, url.trim())
            .apply()
    }
}
