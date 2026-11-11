package com.chronosbvrx.ibizacompanion.service

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.chronosbvrx.ibizacompanion.data.CompanionPrefs

/**
 * Dispara el enlace con el estéreo cuando el teléfono se conecta al
 * Bluetooth del auto (receptor AUX o adaptador OBD).
 *
 * - Si hay un equipo guardado en Ajustes, solo ese MAC dispara.
 * - Si no hay ninguno, se aprende el primero cuyo nombre coincida con los
 *   receptores conocidos (HJDWQN, iCar, Vgate…) y se guarda.
 * - Cualquier otro Bluetooth (audífonos, smartwatch) se ignora.
 */
class CarBtWatcher(
    private val context: Context,
    private val onCarConnected: () -> Unit
) {
    companion object {
        private const val TAG = "CarBt"

        /** Nombres de los receptores del auto (sin distinguir mayúsculas). */
        private val NAME_HINTS = listOf(
            "hjdwqn", "icar", "vgate", "obd", "ibiza", "seat", "bt5.3", "aux"
        )
    }

    private var receiver: BroadcastReceiver? = null

    fun start() {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val mac = device?.address ?: return
                val name = runCatching { device?.name }.getOrNull().orEmpty()
                if (!isCarDevice(mac, name)) return
                // Primer equipo del auto: se recuerda para los próximos viajes.
                if (CompanionPrefs.carBtMac(context).isBlank() && name.isNotBlank()) {
                    CompanionPrefs.setCarBtDevice(context, mac, name)
                    Log.i(TAG, "equipo del auto aprendido: $name ($mac)")
                }
                Log.i(TAG, "auto conectado: ${name.ifBlank { mac }} · abriendo enlace")
                try {
                    onCarConnected()
                } catch (e: Exception) {
                    Log.w(TAG, "trigger: ${e.message}")
                }
            }
        }
        val ok = runCatching {
            ContextCompat.registerReceiver(
                context,
                r,
                IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED),
                ContextCompat.RECEIVER_EXPORTED
            )
            receiver = r
        }.onFailure { Log.w(TAG, "registro: ${it.message}") }
        if (ok.isSuccess) Log.i(TAG, "vigilando Bluetooth del auto")
    }

    fun stop() {
        val r = receiver ?: return
        runCatching { context.unregisterReceiver(r) }
        receiver = null
    }

    private fun isCarDevice(mac: String, name: String): Boolean {
        val saved = CompanionPrefs.carBtMac(context)
        if (saved.isNotBlank()) {
            return saved.equals(mac, ignoreCase = true)
        }
        val lower = name.lowercase()
        return NAME_HINTS.any { lower.contains(it) }
    }
}
