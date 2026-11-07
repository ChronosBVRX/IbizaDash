package com.chronosbvrx.ibizadash.car

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log

/**
 * AVRCP passthrough (play/pause/next/prev) using the vendor framework class
 * android.bluetooth.BluetoothAvrcpController. It is the same path the native
 * com.tw.bt app uses; the AIDL btPlayMusic command can only start playback.
 *
 * Android 8.1 does not enforce the hidden-API blocklist, so reflection works.
 */
class AvrcpController(private val context: Context) {

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var proxy: BluetoothProfile? = null
    private var requested = false

    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile != PROFILE_AVRCP) return
            this@AvrcpController.proxy = proxy
            Log.i(TAG, "AVRCP proxy connected: ${proxy?.javaClass?.name}")
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == PROFILE_AVRCP) proxy = null
        }
    }

    fun start() {
        if (requested) return
        requested = true
        try {
            adapter?.getProfileProxy(context, listener, PROFILE_AVRCP)
        } catch (e: Exception) {
            Log.w(TAG, "getProfileProxy: ${e.message}")
        }
    }

    fun stop() {
        val current = proxy ?: return
        try {
            adapter?.closeProfileProxy(PROFILE_AVRCP, current)
        } catch (_: Exception) {
        }
        proxy = null
        requested = false
    }

    fun play(address: String?): Boolean = send(address, KEY_PLAY)

    fun pause(address: String?): Boolean = send(address, KEY_PAUSE)

    fun next(address: String?): Boolean = send(address, KEY_NEXT)

    fun previous(address: String?): Boolean = send(address, KEY_PREVIOUS)

    private fun send(address: String?, keyCode: Int): Boolean {
        val current = proxy ?: return false
        val device = resolveDevice(address) ?: return false
        return try {
            val method = current.javaClass.getMethod(
                "sendPassThroughCmd",
                BluetoothDevice::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            // Some AVRCP sinks interpret the key-release as a second toggle and
            // resume playback. Send the press only; this unit pauses reliably.
            method.invoke(current, device, keyCode, 0)
            Log.i(TAG, "send key=$keyCode to ${device.address}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "sendPassThroughCmd: ${e.message}")
            false
        }
    }

    private fun resolveDevice(address: String?): BluetoothDevice? {
        val adapter = adapter ?: return null
        if (!address.isNullOrBlank()) {
            try {
                return adapter.getRemoteDevice(address)
            } catch (_: Exception) {
            }
        }
        return try {
            adapter.bondedDevices?.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "AvrcpController"
        private const val PROFILE_AVRCP = 12
        private const val KEY_PLAY = 68
        private const val KEY_PAUSE = 70
        private const val KEY_NEXT = 75
        private const val KEY_PREVIOUS = 76
    }
}
