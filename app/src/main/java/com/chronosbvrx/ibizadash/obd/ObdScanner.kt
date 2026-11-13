package com.chronosbvrx.ibizadash.obd

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * Un dispositivo Bluetooth visible para el selector de OBD.
 *
 * [likelyObd] resume el nombre y los servicios anunciados: los adaptadores
 * baratos no siempre se llaman "OBD"/"ELM", así que la lista enseña todo lo
 * encontrado y deja que el usuario elija, pero el emparejamiento automático
 * solo se arriesga con los que parecen un ELM327.
 */
data class ObdDevice(
    val name: String,
    val address: String,
    val type: Int,
    val bonded: Boolean,
    val rssi: Int?,
    val likelyObd: Boolean
) {
    val label: String
        get() = buildString {
            append(name.ifBlank { "Sin nombre" })
            append(" · ")
            append(address)
            if (bonded) append(" · vinculado")
            if (rssi != null && rssi > -127) append(" · $rssi dBm")
        }
}

/**
 * Búsqueda activa de adaptadores ELM327.
 *
 * La app anterior solo miraba los dispositivos ya vinculados y escaneaba BLE
 * con filtros de nombre. Un ELM327 nuevo (o un clon con nombre raro) no
 * aparecía nunca. Aquí se hace descubrimiento clásico **y** BLE, se listan
 * todos los devices y se marca cuáles parecen OBD.
 */
object ObdScanner {

    private const val TAG = "ObdScan"
    private val SCAN_LOCK = Any()
    private val ELM_SERVICES = arrayOf(
        UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("000018f0-0000-1000-8000-00805f9b34fb")
    )
    private val KEYWORDS = arrayOf(
        "obd", "elm", "elm327", "vgate", "v-link", "vlink", "superbox", "327",
        "obdii", "b16", "mini", "icar", "konnwei", "vepeak", "obdlink",
        "autel", "launch", "bluedriver", "scantool", "diagnos", "hho"
    )

    /**
     * Escanea durante [durationMs] y devuelve lo encontrado.
     *
     * Bloquea el hilo que lo llama: úsalo desde un hilo de trabajo. [cancelled]
     * permite cortar la espera cuando el cliente OBD se detiene.
     */
    fun scan(
        context: Context,
        durationMs: Long = 12_000L,
        cancelled: () -> Boolean = { false }
    ): List<ObdDevice> {
        synchronized(SCAN_LOCK) {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = manager?.adapter ?: return emptyList()
            if (!adapter.isEnabled) {
                Log.w(TAG, "Bluetooth apagado")
                return emptyList()
            }
            if (!canScan(context)) {
                Log.w(TAG, "Faltan permisos de Bluetooth para escanear")
                return bondedDevices(adapter)
            }

            val found = java.util.Collections.synchronizedMap(
                LinkedHashMap<String, ObdDevice>()
            )
            bondedDevices(adapter).forEach { found[it.address] = it }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action != BluetoothDevice.ACTION_FOUND) return
                    val device = @Suppress("DEPRECATION")
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        ?: return
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                        .toInt()
                        .takeIf { it > -127 }
                    val item = device.toObdDevice(rssi) ?: return
                    found[item.address] = item
                    Log.i(TAG, "clásico: ${item.label}")
                }
            }

            val bleCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    addBle(found, result)
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                    results?.forEach { addBle(found, it) }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.w(TAG, "BLE scan failed: $errorCode")
                }
            }

            var receiverRegistered = false
            var bleStarted = false
            try {
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    IntentFilter().apply {
                        addAction(BluetoothDevice.ACTION_FOUND)
                    },
                    ContextCompat.RECEIVER_EXPORTED
                )
                receiverRegistered = true
            } catch (e: Exception) {
                Log.w(TAG, "registerReceiver: ${e.message}")
            }

            try {
                if (adapter.isDiscovering) adapter.cancelDiscovery()
                if (!adapter.startDiscovery()) Log.w(TAG, "startDiscovery=false")
            } catch (e: Exception) {
                Log.w(TAG, "startDiscovery: ${e.message}")
            }

            try {
                adapter.bluetoothLeScanner?.startScan(bleCallback)
                bleStarted = true
            } catch (e: Exception) {
                Log.w(TAG, "startScan BLE: ${e.message}")
            }

            val deadline = System.currentTimeMillis() + durationMs
            try {
                while (System.currentTimeMillis() < deadline && !cancelled()) {
                    Thread.sleep(100L)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                try {
                    if (adapter.isDiscovering) adapter.cancelDiscovery()
                } catch (_: Exception) {
                }
                if (bleStarted) {
                    try {
                        adapter.bluetoothLeScanner?.stopScan(bleCallback)
                    } catch (_: Exception) {
                    }
                }
                if (receiverRegistered) {
                    try {
                        context.unregisterReceiver(receiver)
                    } catch (_: Exception) {
                    }
                }
            }

            val snapshot = synchronized(found) { found.values.toList() }
            return snapshot.sortedWith(
                compareByDescending<ObdDevice> { it.likelyObd }
                    .thenByDescending { it.bonded }
                    .thenByDescending { it.rssi ?: -127 }
                    .thenBy { it.name.lowercase() }
            )
        }
    }

    /** Inicia el emparejamiento con el PIN habitual de los ELM327. */
    fun pair(device: BluetoothDevice, pin: String) {
        if (device.bondState == BluetoothDevice.BOND_BONDED) return
        // En esta ROM el servicio de emparejamiento lee esta propiedad y no
        // muestra diálogo. Se intenta por reflexión y por setprop.
        try {
            val props = Class.forName("android.os.SystemProperties")
            props.getMethod("set", String::class.java, String::class.java)
                .invoke(null, "persist.service.bt.pincode", pin)
        } catch (_: Exception) {
        }
        try {
            Runtime.getRuntime()
                .exec(arrayOf("setprop", "persist.service.bt.pincode", pin))
                .waitFor()
        } catch (_: Exception) {
        }
        try {
            device.javaClass
                .getMethod("setPin", ByteArray::class.java)
                .invoke(device, pin.toByteArray())
        } catch (_: Exception) {
        }
        try {
            device.createBond()
        } catch (e: Exception) {
            Log.w(TAG, "createBond: ${e.message}")
        }
    }

    fun score(name: String?): Int {
        val n = name.orEmpty().lowercase()
        if (n.isBlank()) return 0
        return KEYWORDS.count { n.contains(it) }
    }

    private fun addBle(found: MutableMap<String, ObdDevice>, result: ScanResult?) {
        val device = result?.device ?: return
        val address = safeAddress(device) ?: return
        val name = result.scanRecord?.deviceName ?: safeName(device)
        val uuids = result.scanRecord?.serviceUuids?.map { it.uuid }.orEmpty()
        val likely = score(name) > 0 || uuids.any { it in ELM_SERVICES }
        val item = ObdDevice(
            name = name.orEmpty(),
            address = address,
            type = device.type,
            bonded = safeBond(device),
            rssi = result.rssi,
            likelyObd = likely
        )
        found[item.address] = item
        Log.i(TAG, "BLE: ${item.label} likely=$likely")
    }

    private fun bondedDevices(adapter: BluetoothAdapter): List<ObdDevice> {
        return try {
            adapter.bondedDevices.orEmpty().mapNotNull { it.toObdDevice(null) }
        } catch (e: Exception) {
            Log.w(TAG, "bondedDevices: ${e.message}")
            emptyList()
        }
    }

    private fun BluetoothDevice.toObdDevice(rssi: Int?): ObdDevice? {
        val address = safeAddress(this) ?: return null
        val name = safeName(this).orEmpty()
        return ObdDevice(
            name = name,
            address = address,
            type = type,
            bonded = safeBond(this),
            rssi = rssi,
            likelyObd = score(name) > 0
        )
    }

    private fun safeName(device: BluetoothDevice): String? = try {
        device.name
    } catch (_: SecurityException) {
        null
    } catch (_: Exception) {
        null
    }

    private fun safeAddress(device: BluetoothDevice): String? = try {
        device.address
    } catch (_: SecurityException) {
        null
    } catch (_: Exception) {
        null
    }

    private fun safeBond(device: BluetoothDevice): Boolean = try {
        device.bondState == BluetoothDevice.BOND_BONDED
    } catch (_: Exception) {
        false
    }

    private fun canScan(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
}
