package com.chronosbvrx.ibizadash.bluetooth

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Estado del teléfono conectado por Bluetooth.
 *
 * Datos disponibles sin privilegios:
 *  - Nombre / dirección del dispositivo.
 *  - A2DP (audio) y HFP (manos libres) conectados.
 *  - Batería: BluetoothDevice.getBatteryLevel() (oculto en el SDK; por reflexión).
 *    Muchos teléfonos devuelven -1 si no publican el dato.
 */
class BluetoothStatusClient(
    private val context: Context,
    private val onUpdate: (BluetoothStatus?) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var a2dp: BluetoothA2dp? = null
    private var headset: BluetoothHeadset? = null
    private var started = false

    private val batteryMethod by lazy {
        try {
            BluetoothDevice::class.java.getMethod("getBatteryLevel")
        } catch (e: Throwable) {
            Log.w(TAG, "getBatteryLevel no disponible: ${e.message}")
            null
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dp = proxy as? BluetoothA2dp
                BluetoothProfile.HEADSET -> headset = proxy as? BluetoothHeadset
            }
            refresh()
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dp = null
                BluetoothProfile.HEADSET -> headset = null
            }
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 10_000L)
        }
    }

    fun start() {
        if (started) return
        started = true
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
            }
            context.registerReceiver(receiver, filter)
        } catch (e: Exception) {
            Log.w(TAG, "registerReceiver: ${e.message}")
        }
        try {
            adapter?.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
            adapter?.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)
        } catch (e: Exception) {
            Log.w(TAG, "getProfileProxy: ${e.message}")
        }
        handler.post(poll)
    }

    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacks(poll)
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
        try {
            adapter?.closeProfileProxy(BluetoothProfile.A2DP, a2dp)
            adapter?.closeProfileProxy(BluetoothProfile.HEADSET, headset)
        } catch (_: Exception) {
        }
        a2dp = null
        headset = null
    }

    private fun refresh() {
        val device = connectedDevice() ?: bondedDevice()
        if (device == null) {
            onUpdate(null)
            return
        }
        val a2dpConnected = try {
            a2dp?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED
        } catch (_: Exception) {
            false
        }
        val hfpConnected = try {
            headset?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED
        } catch (_: Exception) {
            false
        }
        val name = try {
            device.name ?: device.address
        } catch (_: Exception) {
            device.address
        }
        val battery = try {
            batteryMethod?.invoke(device) as? Int ?: -1
        } catch (_: Throwable) {
            -1
        }
        val status = BluetoothStatus(
            name = name,
            address = device.address,
            connected = a2dpConnected || hfpConnected,
            battery = battery,
            a2dp = a2dpConnected,
            hfp = hfpConnected
        )
        handler.post { onUpdate(status) }
    }

    private fun connectedDevice(): BluetoothDevice? {
        return try {
            a2dp?.connectedDevices?.firstOrNull()
                ?: headset?.connectedDevices?.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun bondedDevice(): BluetoothDevice? {
        return try {
            adapter?.bondedDevices?.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "BluetoothStatus"
    }
}
