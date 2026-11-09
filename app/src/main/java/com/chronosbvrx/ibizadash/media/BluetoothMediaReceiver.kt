package com.chronosbvrx.ibizadash.media

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.chronosbvrx.ibizadash.music.OfflineMusicCatalog

/**
 * A2DP sink / AVRCP metadata on vendor head units does not always appear as a
 * local MediaSession. The Bluetooth stack broadcasts the track info instead.
 * This receiver listens to the common Autochips/Spreadtrum/AVRCP actions,
 * maps the extras and publishes them through [MediaRepository].
 *
 * The extra keys vary between stacks, so parsing is tolerant and every packet
 * is logged (tag BtMedia) for field verification on the real unit.
 */
class BluetoothMediaReceiver : BroadcastReceiver() {

    private val handler = Handler(Looper.getMainLooper())
    private var context: Context? = null
    private var polling = false
    private var lastPlaybackState = -1

    private val pollRunnable = object : Runnable {
        override fun run() {
            val current = MediaRepository.current
            if (current == null || !current.isBluetooth) {
                polling = false
                return
            }
            val active = resolvedPlaying(current.isPlaying)
            if (active != current.isPlaying) {
                val updated = current.copy(isPlaying = active)
                MediaRepository.updateBluetooth(updated)
                context?.let { saveCache(it, updated) }
            }
            handler.postDelayed(this, 2000L)
        }
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        handler.postDelayed(pollRunnable, 1200L)
    }

    private fun isMusicActive(): Boolean? {
        val ctx = context ?: return null
        return try {
            (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive
        } catch (_: Exception) {
            null
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        this.context = context.applicationContext
        startPolling()
        val extras = intent.extras
        val action = intent.action ?: return
        if (extras != null) {
            val dump = extras.keySet().joinToString(" | ") { key ->
                "$key=${extras.get(key)?.toString()?.take(90)}"
            }
            Log.i(TAG, "action=$action extras=[$dump]")
        } else {
            Log.i(TAG, "action=$action extras=null")
        }

        val map = mutableMapOf<String, Any?>()
        extras?.keySet()?.forEach { key ->
            map[key.lowercase()] = extras.get(key)
        }

        @Suppress("DEPRECATION")
        val avrcpMetadata = extras?.getParcelable<MediaMetadata>(AVRCP_METADATA)
        @Suppress("DEPRECATION")
        val avrcpPlayback = extras?.getParcelable<PlaybackState>(AVRCP_PLAYBACK)

        if (avrcpMetadata != null) {
            publishAvrcp(avrcpMetadata, avrcpPlayback)
            return
        }

        val a2dpState = extras?.getInt("android.bluetooth.profile.extra.STATE", -1) ?: -1
        if (action.contains("CONNECTION_STATE_CHANGED") && a2dpState == 0) {
            context?.let { clearCache(it) }
            MediaRepository.updateBluetooth(null)
            return
        }
        if (a2dpState == A2DP_STATE_PLAYING || a2dpState == A2DP_STATE_NOT_PLAYING) {
            val current = MediaRepository.current
            if (current != null && current.isBluetooth) {
                val playing = a2dpState == A2DP_STATE_PLAYING
                // The sink may report "playing" (route active) even when the
                // phone is paused. A paused AVRCP state is authoritative.
                if (!(playing && lastPlaybackState == PlaybackState.STATE_PAUSED)) {
                    val updated = current.copy(isPlaying = playing)
                    MediaRepository.updateBluetooth(updated)
                    context?.let { saveCache(it, updated) }
                }
            }
            return
        }

        val device = extras?.getParcelable<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        val deviceName = device?.name
        val title = map.text(
            "android.title", "title", "track", "tracktitle", "track_title",
            "trackname", "track_name", "music_title", "id3_title", "song",
            "songname", "song_name", "media_title", "name"
        )
        val artist = map.text(
            "android.artist", "artist", "artist_name", "artistname",
            "music_artist", "id3_artist", "singer", "performer"
        )
        val album = map.text(
            "android.album", "album", "album_name", "albumname",
            "music_album", "id3_album"
        )
        val durationMs = map.durationMs(
            "duration", "duration_ms", "track_length", "tracklength",
            "total_time", "totaltime", "length", "android.media.metadata.duration"
        )
        val positionMs = map.durationMs(
            "position", "position_ms", "current_time", "currenttime",
            "progress", "android.media.metadata.position"
        )
        val playing = map.playing(
            "playing", "isplaying", "is_playing", "playstate", "play_state",
            "playstatus", "play_status", "state",
            "android.bluetooth.a2dpsink.extra.playing_state"
        )
        val artwork = map.bitmap("artwork", "album_art", "albumart", "cover", "cover_art", "bitmap")
        val sourcePackage = map.sourcePackage(action)

        if (title.isNullOrBlank()) {
            // State-only packets (pause/play) keep the last track.
            if (playing != null) {
                val current = MediaRepository.current
                if (current != null && current.isBluetooth) {
                    val updated = current.copy(isPlaying = playing)
                    MediaRepository.updateBluetooth(updated)
                    context?.let { saveCache(it, updated) }
                }
            }
            return
        }

        val previous = MediaRepository.current
        val now = NowPlaying(
            title = title,
            artist = artist.orEmpty(),
            album = album.orEmpty(),
            artwork = artwork ?: previous?.takeIf { it.title == title }?.artwork,
            isPlaying = resolvedPlaying(playing ?: previous?.isPlaying),
            packageName = sourcePackage,
            isBluetooth = true,
            positionMs = positionMs ?: 0L,
            durationMs = durationMs ?: 0L
        )
        MediaRepository.updateBluetooth(now)
        context?.let { saveCache(it, now) }
        loadCover(now)
    }

    private fun publishAvrcp(metadata: MediaMetadata, playback: PlaybackState?) {
        Log.i(
            TAG,
            "meta keys=" + metadata.keySet().joinToString() +
                " title=${metadata.getText(MediaMetadata.METADATA_KEY_TITLE)}" +
                " artist=${metadata.getText(MediaMetadata.METADATA_KEY_ARTIST)}" +
                " album=${metadata.getText(MediaMetadata.METADATA_KEY_ALBUM)}"
        )
        val title = metadata.getText(MediaMetadata.METADATA_KEY_TITLE)?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: metadata.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.toString()
                ?.takeIf { it.isNotBlank() }
            ?: return
        val artist = metadata.getText(MediaMetadata.METADATA_KEY_ARTIST)?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: metadata.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.toString()
                ?.takeIf { it.isNotBlank() }
            ?: metadata.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)?.toString()
                .orEmpty()
        val album = metadata.getText(MediaMetadata.METADATA_KEY_ALBUM)?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: metadata.getText(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)?.toString()
                .orEmpty()
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
            .takeIf { it > 0L } ?: 0L
        val art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        val previous = MediaRepository.current
        val sameTrack = previous != null && previous.title == title
        playback?.state?.let { lastPlaybackState = it }
        val playing = resolvedPlaying(if (sameTrack) previous!!.isPlaying else null)

        Log.i(
            TAG,
            "avrcp track title=$title artist=$artist album=$album " +
                "duration=$duration art=${art != null} playing=$playing"
        )

        val now = NowPlaying(
            title = title,
            artist = artist,
            album = album,
            artwork = art ?: previous?.takeIf { sameTrack }?.artwork,
            isPlaying = playing,
            packageName = previous
                ?.takeIf { sameTrack && it.packageName != "bluetooth" }
                ?.packageName
                ?: "bluetooth",
            isBluetooth = true,
            positionMs = playback?.position?.takeIf { it >= 0L } ?: 0L,
            durationMs = duration
        )
        MediaRepository.updateBluetooth(now)
        context?.let { saveCache(it, now) }
        loadCover(now)
    }

    /**
     * Detecta la app que reproduce cuando el stack la expone.
     *
     * Spotify anuncia públicamente `com.spotify.music.metadatachanged`,
     * así que esa action por sí sola ya es una fuente confiable. Las apps
     * genéricas A2DP/AVRCP normalmente no envían esta identificación.
     */
    private fun MutableMap<String, Any?>.sourcePackage(action: String?): String {
        val direct = text(
            "package", "package_name", "packagename", "originating_package",
            "source_package", "session_package", "media_package",
            "source_app", "app_package", "player_package", "application",
            "source", "app"
        )?.trim()?.takeIf { it.contains(".") }
        if (direct != null) return direct.lowercase()
        val act = action.orEmpty().lowercase()
        return when {
            act.startsWith("com.spotify.music") -> "com.spotify.music"
            act.contains("youtube") && act.contains("music") ->
                "com.google.android.apps.youtube.music"
            act.contains("youtube") -> "com.google.android.youtube"
            else -> "bluetooth"
        }
    }

    private fun loadCover(track: NowPlaying) {
        if (track.artwork != null) return
        if (!OfflineMusicCatalog.isReady) return
        val match = OfflineMusicCatalog.lookup(track.artist, track.title) ?: return
        OfflineMusicCatalog.loadCover(match) { art ->
            if (art == null) return@loadCover
            val current = MediaRepository.current ?: return@loadCover
            if (current.title == track.title && current.artist == track.artist) {
                MediaRepository.updateBluetooth(current.copy(artwork = art))
            }
        }
    }

    private fun resolvedPlaying(fallback: Boolean?): Boolean {
        return when (lastPlaybackState) {
            PlaybackState.STATE_PLAYING -> true
            PlaybackState.STATE_PAUSED, PlaybackState.STATE_STOPPED -> false
            else -> isMusicActive() ?: fallback ?: true
        }
    }

    private fun MutableMap<String, Any?>.text(vararg keys: String): String? {
        for (key in keys) {
            val value = this[key] ?: continue
            val text = when (value) {
                is CharSequence -> value.toString()
                is String -> value
                else -> null
            }
            if (!text.isNullOrBlank() && !text.equals("null", ignoreCase = true)) return text
        }
        return null
    }

    private fun MutableMap<String, Any?>.durationMs(vararg keys: String): Long? {
        for (key in keys) {
            val value = this[key] ?: continue
            val raw = when (value) {
                is Long -> value
                is Int -> value.toLong()
                is Short -> value.toLong()
                is String -> value.toLongOrNull()
                else -> null
            } ?: continue
            if (raw <= 0L) continue
            // AVRCP usually reports milliseconds; some vendors report seconds.
            return if (raw < 3_600L) raw * 1_000L else raw
        }
        return null
    }

    private fun MutableMap<String, Any?>.playing(vararg keys: String): Boolean? {
        for (key in keys) {
            val value = this[key] ?: continue
            when (value) {
                is Boolean -> return value
                is Int -> return when (value) {
                    1, 3 -> true   // AVRCP PLAYING / FWD_SEEK
                    0, 2, 4, 5 -> false
                    else -> null
                }
                is String -> when (value.lowercase()) {
                    "true", "playing", "play", "1" -> return true
                    "false", "paused", "pause", "stopped", "0" -> return false
                }
            }
        }
        return null
    }

    private fun MutableMap<String, Any?>.bitmap(vararg keys: String): Bitmap? {
        for (key in keys) {
            val value = this[key] ?: continue
            if (value is Bitmap) return value
        }
        return null
    }

    private fun saveCache(context: Context, track: NowPlaying) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("title", track.title)
            .putString("artist", track.artist)
            .putString("album", track.album)
            .putBoolean("playing", track.isPlaying)
            .apply()
    }

    private fun clearCache(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private fun restoreCache(context: Context) {
        val connected = try {
            BluetoothAdapter.getDefaultAdapter()
                ?.getProfileConnectionState(11) == BluetoothProfile.STATE_CONNECTED
        } catch (_: Exception) {
            false
        }
        if (!connected) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val title = prefs.getString("title", null)?.takeIf { it.isNotBlank() } ?: return
        val track = NowPlaying(
            title = title,
            artist = prefs.getString("artist", "").orEmpty(),
            album = prefs.getString("album", "").orEmpty(),
            artwork = null,
            isPlaying = prefs.getBoolean("playing", false),
            packageName = "bluetooth",
            isBluetooth = true
        )
        MediaRepository.updateBluetooth(track)
        loadCover(track)
    }

    companion object {
        private const val TAG = "BtMedia"
        private const val PREFS = "bt_media"

        private const val AVRCP_METADATA =
            "android.bluetooth.avrcp-controller.profile.extra.METADATA"
        private const val AVRCP_PLAYBACK =
            "android.bluetooth.avrcp-controller.profile.extra.PLAYBACK"
        private const val A2DP_STATE_PLAYING = 11
        private const val A2DP_STATE_NOT_PLAYING = 10

        private val ACTIONS = arrayOf(
            "android.bluetooth.avrcp-controller.profile.action.TRACK_EVENT",
            "com.android.bluetooth.a2dpsink.action.METADATA_CHANGED",
            "com.android.getelementattrrsp",
            "android.bluetooth.a2dpsink.profile.action.PLAYING_STATE_CHANGED",
            "android.bluetooth.a2dpsink.profile.action.CONNECTION_STATE_CHANGED",
            "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED",
            "com.android.bluetooth.a2dpsink.action.PLAYING_STATE_CHANGED",
            "com.autochips.bluetooth.MUSIC_INFO",
            "com.autochips.bt.music.id3",
            "com.autochips.bluetooth.avrcpct.AvrcpCtPlayerUtility.action.ACTION_MEDIA_DATA_UPDATE",
            "com.autochips.bluetooth.avrcpct.AvrcpCtPlayerUtility.action.ACTION_PLAYBACK_DATA_UPDATE",
            "com.tw.bt.playa2dpsinkcmd",
            "com.tw.bt.music.info",
            "com.android.music.metachanged",
            "com.android.music.playstatechanged",
            "com.spotify.music.metadatachanged",
            "com.spotify.mobile.android.metadatachanged",
            "com.spotify.music.playbackstatechanged",
            "com.spotify.mobile.android.playbackstatechanged",
            "com.spotify.music.active",
            "com.spotify.music.queuechanged"
        )

        private var instance: BluetoothMediaReceiver? = null

        fun register(context: Context) {
            if (instance != null) return
            val receiver = BluetoothMediaReceiver()
            val filter = IntentFilter()
            ACTIONS.forEach { filter.addAction(it) }
            try {
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                instance = receiver
                receiver.context = context.applicationContext
                receiver.restoreCache(context)
                Log.i(TAG, "registered ${ACTIONS.size} BT metadata actions")
            } catch (e: Exception) {
                Log.w(TAG, "register failed: ${e.message}")
            }
        }
    }
}
