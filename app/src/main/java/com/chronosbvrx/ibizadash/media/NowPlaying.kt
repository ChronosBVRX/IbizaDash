package com.chronosbvrx.ibizadash.media

import android.graphics.Bitmap

data class NowPlaying(
    val title: String,
    val artist: String,
    val album: String,
    val artwork: Bitmap?,
    val isPlaying: Boolean,
    val packageName: String,
    val isBluetooth: Boolean,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)
