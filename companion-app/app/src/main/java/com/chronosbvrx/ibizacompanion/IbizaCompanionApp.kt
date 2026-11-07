package com.chronosbvrx.ibizacompanion

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class IbizaCompanionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // osmdroid configuration (tile cache in app-specific storage).
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        val base = File(getExternalFilesDir(null), "osmdroid")
        base.mkdirs()
        val tiles = File(base, "tiles")
        tiles.mkdirs()
        Configuration.getInstance().osmdroidBasePath = base
        Configuration.getInstance().osmdroidTileCache = tiles

        // If a zip tile pack was pushed over adb, extract it once in background.
        com.chronosbvrx.ibizacompanion.data.TilePackInstaller.ensureInstalled(this)
    }
}