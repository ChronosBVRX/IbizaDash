package com.chronosbvrx.ibizacompanion.data

import android.content.Context
import android.util.Log
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Installs the offline tile pack without pushing 100k loose files over adb.
 *
 * Drop `offline-tiles.zip` in the app's external files dir; on the next launch
 * it is extracted once into `offline-tiles/`, which the Leaflet map serves via
 * `setOfflineBase('file://…')`.
 */
object TilePackInstaller {
    private const val TAG = "TilePack"

    fun ensureInstalled(context: Context) {
        val ext = context.getExternalFilesDir(null) ?: return
        val zip = File(ext, "offline-tiles.zip")
        if (!zip.isFile) return
        val dest = File(ext, "offline-tiles")
        val marker = File(dest, ".pack-version")
        val version = "${zip.length()}-${zip.lastModified()}"
        if (marker.isFile && marker.readText() == version) return

        Thread {
            try {
                dest.mkdirs()
                ZipInputStream(zip.inputStream().buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name.trimStart('/')
                        if (name.isNotEmpty() && !name.contains("..")) {
                            val out = File(dest, name)
                            if (entry.isDirectory) {
                                out.mkdirs()
                            } else {
                                out.parentFile?.mkdirs()
                                out.outputStream().buffered().use { stream ->
                                    zis.copyTo(stream)
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                marker.writeText(version)
                // The extracted tree is enough; don't keep a second ~500 MB copy.
                try {
                    zip.delete()
                } catch (_: Exception) {
                }
                Log.i(TAG, "pack offline instalado en ${dest.absolutePath}")
            } catch (e: Exception) {
                Log.w(TAG, "no se pudo extraer el pack: ${e.message}")
            }
        }.apply {
            name = "TilePackInstall"
            isDaemon = true
            start()
        }
    }
}
