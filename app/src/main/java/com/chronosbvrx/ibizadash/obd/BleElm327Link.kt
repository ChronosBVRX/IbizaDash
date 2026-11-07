package com.chronosbvrx.ibizadash.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Enlace BLE mínimo para adaptadores ELM327 tipo B16 / Vgate iCar Pro.
 *
 * Los ELM327 "clásicos" usan SPP; los B16 modernos son BLE y no aceptan
 * `createRfcommSocket`. Este enlace descubre la característica de escritura y
 * la de notificación (FFE0/FFE1 o 18F0/2AF0+2AF1) y se comporta como un
 * puerto serie: escribe comandos y acumula las notificaciones hasta `>`.
 */
class BleElm327Link(
    private val context: Context,
    private val device: BluetoothDevice
) {
    companion object {
        private const val TAG = "BleElm327"
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val FFE0 = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        private val FFE1 = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        private val SERVICE_18F0 = UUID.fromString("000018f0-0000-1000-8000-00805f9b34fb")
        private const val CHUNK = 20
    }

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null

    @Volatile
    private var ready = false

    private val connectLatch = CountDownLatch(1)
    private val buffer = StringBuilder()
    private val lock = Any()

    @SuppressLint("MissingPermission")
    fun connect(timeoutMs: Long = 12_000L): Boolean {
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    try {
                        g.discoverServices()
                    } catch (e: Exception) {
                        Log.w(TAG, "discoverServices: ${e.message}")
                        connectLatch.countDown()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    ready = false
                    connectLatch.countDown()
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    connectLatch.countDown()
                    return
                }
                findCharacteristics(g)
                val n = notifyChar
                val w = writeChar
                if (n == null || w == null) {
                    Log.w(TAG, "sin características de escritura/notificación")
                    connectLatch.countDown()
                    return
                }
                try {
                    g.setCharacteristicNotification(n, true)
                } catch (e: Exception) {
                    Log.w(TAG, "setCharacteristicNotification: ${e.message}")
                }
                val cccd = n.getDescriptor(CCCD)
                if (cccd == null) {
                    ready = true
                    connectLatch.countDown()
                } else {
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    try {
                        if (!g.writeDescriptor(cccd)) {
                            ready = true
                            connectLatch.countDown()
                        }
                    } catch (e: Exception) {
                        ready = true
                        connectLatch.countDown()
                    }
                }
            }

            override fun onDescriptorWrite(
                g: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                if (descriptor.uuid == CCCD) {
                    ready = status == BluetoothGatt.GATT_SUCCESS
                    connectLatch.countDown()
                }
            }

            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                val data = characteristic.value ?: return
                if (data.isEmpty()) return
                synchronized(lock) {
                    buffer.append(String(data, Charsets.US_ASCII))
                }
            }
        }
        return try {
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) return false
            val ok = connectLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
            if (!ok || !ready) {
                close()
                false
            } else {
                Log.i(TAG, "BLE listo: ${device.name} ${device.address}")
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "connect: ${e.message}")
            close()
            false
        }
    }

    private fun findCharacteristics(g: BluetoothGatt) {
        var w: BluetoothGattCharacteristic? = null
        var n: BluetoothGattCharacteristic? = null

        fun scan(list: Iterable<BluetoothGattCharacteristic>) {
            for (ch in list) {
                val p = ch.properties
                if (w == null && (p and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
                ) {
                    w = ch
                }
                if (n == null && (p and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                            BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0
                ) {
                    n = ch
                }
            }
        }

        // Preferir los servicios típicos de ELM327 BLE.
        val preferred = g.getService(FFE0) ?: g.getService(SERVICE_18F0)
        if (preferred != null) scan(preferred.characteristics)
        if (w == null || n == null) {
            for (svc in g.services) scan(svc.characteristics)
        }
        // FFE1 suele servir para escribir y notificar a la vez.
        if (w == null && n != null) w = n
        if (n == null && w != null &&
            (w!!.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
        ) {
            n = w
        }
        // Si el servicio preferido no dio nada, buscar FFE1 explícitamente.
        if (w == null || n == null) {
            val ffe1 = g.getService(FFE0)?.getCharacteristic(FFE1)
            if (ffe1 != null) {
                if (w == null) w = ffe1
                if (n == null) n = ffe1
            }
        }
        writeChar = w
        notifyChar = n
        Log.i(TAG, "write=${w?.uuid} notify=${n?.uuid}")
    }

    @SuppressLint("MissingPermission")
    fun write(text: String): Boolean {
        val g = gatt ?: return false
        val ch = writeChar ?: return false
        val bytes = text.toByteArray(Charsets.US_ASCII)
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + CHUNK, bytes.size)
            val chunk = bytes.copyOfRange(offset, end)
            var sent = false
            var attempts = 0
            while (!sent && attempts < 3) {
                attempts++
                try {
                    ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    ch.value = chunk
                    if (g.writeCharacteristic(ch)) {
                        sent = true
                    } else {
                        // Algunos B16 solo aceptan escritura sin respuesta.
                        ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        ch.value = chunk
                        sent = g.writeCharacteristic(ch)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "write: ${e.message}")
                }
                if (!sent) Thread.sleep(15L)
            }
            if (!sent) return false
            offset = end
            Thread.sleep(8L)
        }
        return true
    }

    /** Espera la respuesta completa (termina en `>`) o devuelve lo recibido. */
    fun readUntilPrompt(timeoutMs: Long): String {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            synchronized(lock) {
                val idx = buffer.indexOf(">")
                if (idx >= 0) {
                    val out = buffer.substring(0, idx + 1)
                    buffer.delete(0, idx + 1)
                    return out
                }
            }
            Thread.sleep(10L)
        }
        synchronized(lock) {
            val out = buffer.toString()
            buffer.setLength(0)
            return out
        }
    }

    fun isConnected(): Boolean = ready

    @SuppressLint("MissingPermission")
    fun close() {
        ready = false
        try {
            gatt?.disconnect()
        } catch (_: Exception) {
        }
        try {
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        writeChar = null
        notifyChar = null
        synchronized(lock) { buffer.setLength(0) }
    }
}
