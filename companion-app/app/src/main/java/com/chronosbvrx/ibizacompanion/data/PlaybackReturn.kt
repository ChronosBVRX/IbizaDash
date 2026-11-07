package com.chronosbvrx.ibizacompanion.data

import android.os.SystemClock

/**
 * Marks that the user launched a track in an external app from IbizaCompanion.
 * The bridge service uses this to bring IbizaCompanion back to the foreground
 * once the external app starts playing, even if the task-switch is still in
 * progress.
 */
object PlaybackReturn {
    @Volatile
    private var requestedAt = 0L

    fun markRequested() {
        requestedAt = SystemClock.elapsedRealtime()
    }

    fun hasPending(): Boolean {
        val now = SystemClock.elapsedRealtime()
        return requestedAt > 0L && now - requestedAt < 20_000L
    }

    /** True once the external app has had time to start the new track. */
    fun shouldReturnNow(minDelayMs: Long = 2500L): Boolean {
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - requestedAt
        return requestedAt > 0L && elapsed >= minDelayMs && elapsed < 20_000L
    }

    /** Returns true once if a return was requested in the last 20 seconds. */
    fun consumeIfRecent(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val recent = requestedAt > 0L && now - requestedAt < 20_000L
        if (recent) requestedAt = 0L
        return recent
    }
}