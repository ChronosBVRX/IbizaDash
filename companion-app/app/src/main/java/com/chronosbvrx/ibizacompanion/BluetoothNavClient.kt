package com.chronosbvrx.ibizacompanion

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID

/**
 * Canal de respaldo por Bluetooth RFCOMM.
 *
 * El transporte principal es [WifiBridgeClient]; este cliente sigue
 * funcionando para equipos sin Wi-Fi Direct y para el envío puntual de una
 * ruta cuando el servicio aún no está enlazado.
 */
class BluetoothNavClient(private val context: Context) : BridgeTransport {
    companion object {
        val NAV_BRIDGE_UUID: UUID =
            UUID.fromString("00001101-c0de-4b56-5258-4942495a4101")
        private const val TAG = "BtNavClient"
    }

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @Volatile
    private var socket: android.bluetooth.BluetoothSocket? = null

    @Volatile
    private var output: OutputStream? = null

    @Volatile
    private var closed = false

    private var readerThread: Thread? = null

    /** Cada línea JSON que llega del estéreo (PONG, NAV_STATE, REROUTE_REQUEST). */
    @Volatile
    var onLine: ((String) -> Unit)? = null

    fun findStereo(): BluetoothDevice? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val granted = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return null
        }
        return try {
            val saved = com.chronosbvrx.ibizacompanion.data.StereoPrefs.mac(context)
            if (!saved.isNullOrBlank()) {
                return adapter?.getRemoteDevice(saved)
            }
            adapter?.bondedDevices?.firstOrNull { d ->
                d.name?.contains("Ibiza", ignoreCase = true) == true ||
                    d.name?.contains("Dash", ignoreCase = true) == true
            } ?: adapter?.bondedDevices?.firstOrNull()
        } catch (e: SecurityException) {
            null
        }
    }

    fun connect(device: BluetoothDevice): Boolean {
        return try {
            close()
            closed = false
            val sock = device.createInsecureRfcommSocketToServiceRecord(NAV_BRIDGE_UUID)
            sock.connect()
            socket = sock
            output = sock.outputStream
            startReader(sock)
            Log.i(TAG, "conectado a ${device.name} ${device.address}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "connect ${device.name} ${device.address}: ${e.javaClass.simpleName}: ${e.message}")
            close()
            false
        }
    }

    private fun startReader(sock: android.bluetooth.BluetoothSocket) {
        readerThread?.interrupt()
        readerThread = Thread {
            try {
                BufferedReader(InputStreamReader(sock.inputStream)).use { reader ->
                    while (!closed && sock.isConnected) {
                        val line = reader.readLine() ?: break
                        if (line.isNotBlank()) {
                            try {
                                onLine?.invoke(line)
                            } catch (e: Exception) {
                                Log.w(TAG, "onLine: ${e.message}")
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
            Log.i(TAG, "lector Bluetooth terminado")
        }.apply {
            name = "BtNavReader"
            isDaemon = true
            start()
        }
    }

    override fun isConnected(): Boolean {
        return try {
            !closed && socket?.isConnected == true
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    override fun send(obj: JSONObject): Boolean {
        return try {
            val out = output ?: run {
                Log.w(TAG, "send sin socket")
                return false
            }
            val data = (obj.toString() + "\n").toByteArray()
            out.write(data)
            out.flush()
            Log.i(TAG, "enviado ${obj.optString("type")} bytes=${data.size}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "send: ${e.message}")
            close()
            false
        }
    }

    /** Compatibilidad con llamadas antiguas del servicio. */
    fun sendRouteSet(
        destinationName: String,
        totalDistanceMeters: Int,
        totalDurationSeconds: Int,
        encodedPolyline: String,
        steps: List<JSONObject>
    ): Boolean = send(BridgeRouter.routeJson(
        destinationName, totalDistanceMeters, totalDurationSeconds, encodedPolyline, steps
    ))

    fun sendStepUpdate(
        nextInstruction: String,
        maneuver: String,
        distanceToTurnMeters: Int,
        remainingDistanceMeters: Int,
        etaText: String
    ): Boolean = send(BridgeRouter.stepJson(
        nextInstruction, maneuver, distanceToTurnMeters, remainingDistanceMeters, etaText
    ))

    fun clearRoute(): Boolean = send(JSONObject().put("type", "ROUTE_CLEAR"))

    fun sendMediaArt(
        title: String,
        artist: String,
        album: String,
        isPlaying: Boolean,
        pngBase64: String
    ): Boolean =
        send(BridgeRouter.mediaArtJson(title, artist, album, isPlaying, pngBase64))

    fun sendPing(): Boolean = send(BridgeRouter.pingJson())

    override fun close() {
        closed = true
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        output = null
        readerThread?.interrupt()
        readerThread = null
    }
}
