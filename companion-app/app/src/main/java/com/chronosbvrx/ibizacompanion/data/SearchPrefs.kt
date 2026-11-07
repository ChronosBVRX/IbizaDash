package com.chronosbvrx.ibizacompanion.data

import android.content.Context

/** Remembers the preferred music app for searches. */
object SearchPrefs {
    private const val FILE = "search_prefs"
    private const val KEY_FAVORITE = "favorite_package"

    fun favoritePackage(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_FAVORITE, null)

    fun setFavoritePackage(context: Context, pkg: String?) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FAVORITE, pkg)
            .apply()
    }
}