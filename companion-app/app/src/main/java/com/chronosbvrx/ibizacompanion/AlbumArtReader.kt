package com.chronosbvrx.ibizacompanion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Reads the current track's album art from the phone's active media sessions
 * (MediaMetadata.METADATA_KEY_ALBUM_ART / ART / ART_URI) so it can be sent to
 * the stereo over Bluetooth.
 */
object AlbumArtReader {
    private const val TAG = "AlbumArtReader"

    fun readCurrent(context: Context): Triple<String, String, String>? {
        // Returns (title, artist, album) if available; artwork handled separately.
        return try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                ?: return null
            val controllers = msm.getActiveSessions(null)
            val c = controllers.firstOrNull() ?: return null
            val md = c.metadata ?: return null
            Triple(
                md.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                md.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                md.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
            )
        } catch (e: Exception) {
            Log.w(TAG, "readCurrent: ${e.message}")
            null
        }
    }

    fun readArtworkBitmap(context: Context, controller: MediaController?): Bitmap? {
        val md = controller?.metadata ?: return null
        val direct = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
        if (direct != null) return direct
        // Fallback: some apps expose only a URI.
        val uri = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: return null
        return try {
            context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun bitmapToBase64(bmp: Bitmap, maxSize: Int = 256): String {
        val scaled = if (bmp.width > maxSize || bmp.height > maxSize) {
            val ratio = maxSize.toFloat() / maxOf(bmp.width, bmp.height)
            Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true)
        } else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}