package com.chronosbvrx.ibizadash.nav

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.OutputStream
import java.util.UUID

/**
 * Canal de respaldo por Bluetooth RFCOMM.
 *
 * Wi-Fi Direct es el transporte preferido ([WifiNavBridgeServer]); este
 * servidor sigue escuchando para que la app funcione igual en equipos sin
 * Wi-Fi Direct o cuando el teléfono está fuera del alcance de la red.
 */
object BtNavBridgeServer {
    private const val TAG = "BtNavBridge"
    val NAV_BRIDGE_UUID: UUID =
        UUID.fromString("00001101-c0de-4b56-5258-4942495a4101")

    @Volatile
    private var running = false
    private var thread: Thread? = null

    /** Live client outputs. Transient senders come and go; the phone's
     *  persistent MediaBridgeService socket stays open. */
    private val liveOuts =
        java.util.Collections.synchronizedSet(mutableSetOf<OutputStream>())

    /**
     * Sends a JSON message to the phone over RFCOMM (used for REROUTE_REQUEST).
     * Tries every live client until one accepts it. Returns false when nobody
     * is listening.
     */
    fun sendToPhone(obj: JSONObject): Boolean {
        val data = (obj.toString() + "\n").toByteArray()
        val snapshot: List<OutputStream>
        synchronized(liveOuts) {
            snapshot = liveOuts.toList()
        }
        for (out in snapshot) {
            try {
                synchronized(out) {
                    out.write(data)
                    out.flush()
                }
                Log.i(TAG, "tx ${obj.optString("type")}")
                return true
            } catch (e: Exception) {
                try {
                    synchronized(liveOuts) {
                        liveOuts.remove(out)
                    }
                } catch (_: Exception) {
                }
            }
        }
        Log.w(TAG, "tx ${obj.optString("type")}: sin enlace")
        return false
    }

    /**
     * Sends a message to every live client. Used for navigation state so the
     * persistent media socket always receives it even when a transient route
     * socket is also open. Returns true if at least one client accepted it.
     */
    fun broadcast(obj: JSONObject): Boolean {
        val data = (obj.toString() + "\n").toByteArray()
        val snapshot: List<OutputStream>
        synchronized(liveOuts) {
            snapshot = liveOuts.toList()
        }
        var sent = false
        for (out in snapshot) {
            try {
                synchronized(out) {
                    out.write(data)
                    out.flush()
                }
                sent = true
            } catch (e: Exception) {
                try {
                    synchronized(liveOuts) {
                        liveOuts.remove(out)
                    }
                } catch (_: Exception) {
                }
            }
        }
        if (sent) Log.i(TAG, "tx* ${obj.optString("type")}")
        return sent
    }

    fun hasClients(): Boolean = synchronized(liveOuts) { liveOuts.isNotEmpty() }

    fun start(context: Context) {
        if (running) return
        running = true
        thread = Thread {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            if (adapter == null || !adapter.isEnabled) {
                Log.w(TAG, "Bluetooth adapter unavailable")
                running = false
                return@Thread
            }
            while (running) {
                var serverSocket: android.bluetooth.BluetoothServerSocket? = null
                var client: android.bluetooth.BluetoothSocket? = null
                try {
                    serverSocket = try {
                        adapter.listenUsingInsecureRfcommWithServiceRecord("IbizaDashNavBridge", NAV_BRIDGE_UUID)
                    } catch (e: Exception) {
                        Log.w(TAG, "insecure listen failed: ${e.message}; trying secure")
                        adapter.listenUsingRfcommWithServiceRecord("IbizaDashNavBridge", NAV_BRIDGE_UUID)
                    }
                    Log.i(TAG, "escuchando RFCOMM (UUID $NAV_BRIDGE_UUID)")
                    client = serverSocket.accept()
                    Log.i(TAG, "cliente conectado: ${client.remoteDevice?.address}")
                    serverSocket.close()
                    serverSocket = null
                    // Handle each client on its own thread so the persistent media
                    // connection does not block route sending (separate socket).
                    val accepted = client
                    Thread {
                        try {
                            handleClient(context, accepted)
                        } catch (e: Exception) {
                            Log.w(TAG, "client error: ${e.message}")
                        } finally {
                            try { accepted.close() } catch (_: Exception) { }
                        }
                    }.apply {
                        name = "BtNavClient"
                        isDaemon = true
                        start()
                    }
                    client = null
                } catch (e: Exception) {
                    Log.w(TAG, "bridge error: ${e.message}")
                    // Sin Bluetooth encendido/permiso, no girar en vacío.
                    try {
                        Thread.sleep(3000L)
                    } catch (_: InterruptedException) {
                        break
                    }
                } finally {
                    try { client?.close() } catch (_: Exception) { }
                    try { serverSocket?.close() } catch (_: Exception) { }
                }
            }
        }.apply {
            name = "BtNavBridge"
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        try { thread?.interrupt() } catch (_: Exception) { }
        thread = null
    }

    private fun handleClient(context: Context, socket: android.bluetooth.BluetoothSocket) {
        val input = socket.inputStream
        var mine: OutputStream? = null
        try {
            mine = socket.outputStream
            synchronized(liveOuts) {
                liveOuts.add(mine)
            }
            // New phone socket: hand it the current navigation snapshot right
            // away, otherwise its dashboard shows "sin ruta" until the next fix.
            try {
                mine?.let { out ->
                    writeLine(out, NavStateRelay.snapshotObject())
                }
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
        try {
            val reader = java.io.BufferedReader(java.io.InputStreamReader(input))
            val out = mine
            while (running) {
                val line = try { reader.readLine() } catch (_: Exception) { null } ?: break
                if (line.isBlank()) continue
                Log.i(TAG, "rx len=${line.length} head=${line.take(60)}")
                if (out != null) {
                    NavBridgeProtocol.handle(context, line) { reply -> writeLine(out, reply) }
                }
            }
        } finally {
            if (mine != null) {
                try {
                    synchronized(liveOuts) {
                        liveOuts.remove(mine)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun writeLine(out: OutputStream, obj: JSONObject) {
        synchronized(out) {
            out.write((obj.toString() + "\n").toByteArray())
            out.flush()
        }
    }
}
