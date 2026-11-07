package com.chronosbvrx.ibizacompanion

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.os.Handler
import android.util.Log
import androidx.core.content.ContextCompat
import com.chronosbvrx.ibizacompanion.core.state.WifiPeerInfo
import com.chronosbvrx.ibizacompanion.data.StereoPrefs
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Transporte principal: Wi-Fi Direct + TCP.
 *
 * Estrategia de conexión, en orden:
 *  1. IP de LAN/hotspot guardada por el usuario.
 *  2. Grupo Wi-Fi Direct ya formado (el estéreo es Group Owner).
 *  3. Descubrimiento UDP del estéreo en la red Wi-Fi actual.
 *  4. Descubrimiento y conexión Wi-Fi Direct.
 *
 * El TCP va a [PORT] con el protocolo JSON de [BridgeTransport]. Al conectar
 * se envía HELLO con el token compartido; el estéreo responde WELCOME.
 */
class WifiBridgeClient(
    context: Context,
    private val onLine: (String) -> Unit
) : BridgeTransport {

    companion object {
        private const val TAG = "WifiBridge"

        /** Debe coincidir con NavBridgeProtocol del estéreo. */
        private const val PORT = 47800
        private const val DISCOVERY_PORT = 47801
        private const val DISCOVER_REQUEST = "IBIZADASH_DISCOVER"
        private const val DISCOVER_REPLY = "IBIZADASH"
        private const val TOKEN = "chronosbvrx-ibiza-6j"
        private const val PROTO = 2

        private const val DISCOVERY_EVERY_MS = 12_000L
        private const val P2P_TIMEOUT_MS = 22_000L
    }

    data class State(
        val enabled: Boolean,
        val connected: Boolean,
        val host: String?,
        val detail: String,
        val peers: List<WifiPeerInfo>
    )

    /** Callback de estado; se invoca desde el hilo que corresponda. */
    @Volatile
    var onState: ((State) -> Unit)? = null

    private val appContext = context.applicationContext
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivity =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    @Volatile
    private var running = false

    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var output: OutputStream? = null

    private val sendLock = Any()

    @Volatile
    private var p2pEnabled = false

    @Volatile
    private var groupFormed = false

    @Volatile
    private var groupOwnerAddress: String? = null

    @Volatile
    private var connecting = false

    @Volatile
    private var lastP2pAttemptMs = 0L

    @Volatile
    private var lastDiscoveryMs = 0L

    @Volatile
    private var peers: List<WifiPeerInfo> = emptyList()

    @Volatile
    private var detail: String = "iniciando"

    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private var loopThread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    fun start() {
        if (running) return
        running = true
        initP2p()
        registerReceiver()
        startLoop()
        emit("buscando estéreo…")
    }

    fun stop() {
        running = false
        close()
        try {
            receiver?.let { appContext.unregisterReceiver(it) }
        } catch (_: Exception) {
        }
        receiver = null
        try {
            channel?.let { ch ->
                manager?.cancelConnect(ch, null)
                ch.close()
            }
        } catch (_: Exception) {
        }
        channel = null
        manager = null
        loopThread?.interrupt()
        loopThread = null
    }

    fun reconnectNow() {
        close()
        connecting = false
        lastP2pAttemptMs = 0L
        lastDiscoveryMs = 0L
        emit("reconectando…")
    }

    /** Fuerza un descubrimiento Wi-Fi Direct (botón "Buscar estéreo"). */
    fun scanPeers() {
        if (!p2pEnabled) {
            emit("Wi-Fi Direct apagado o sin soporte")
            return
        }
        if (!hasP2pPermission()) {
            emit("Falta permiso Wi-Fi")
            return
        }
        discoverPeers(force = true)
    }

    /** Conecta a un peer concreto elegido en la UI. */
    fun connectPeer(address: String) {
        if (address.isBlank()) return
        StereoPrefs.setP2pAddress(appContext, address)
        connectPeerAddress(address)
    }

    // ------------------------------------------------------------------
    // BridgeTransport
    // ------------------------------------------------------------------

    override fun isConnected(): Boolean {
        val s = socket ?: return false
        return try {
            running && s.isConnected && !s.isClosed
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    override fun send(obj: JSONObject): Boolean {
        val out = output ?: return false
        return try {
            val data = (obj.toString() + "\n").toByteArray()
            synchronized(sendLock) {
                out.write(data)
                out.flush()
            }
            if (obj.optString("type") != "TILE_BATCH" &&
                obj.optString("type") != "MAP_FRAME"
            ) {
                Log.i(TAG, "tx ${obj.optString("type")} ${data.size}B")
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "send: ${e.javaClass.simpleName}: ${e.message}")
            closeSocket("envío falló")
            false
        }
    }

    override fun close() {
        closeSocket("cerrado")
    }

    // ------------------------------------------------------------------
    // Bucle de conexión
    // ------------------------------------------------------------------

    private fun startLoop() {
        loopThread = Thread {
            var backoff = 3_000L
            while (running) {
                try {
                    if (!StereoPrefs.wifiEnabled(appContext)) {
                        if (isConnected()) closeSocket("Wi-Fi desactivado")
                        emit("Wi-Fi desactivado en la app")
                        Thread.sleep(3_000L)
                        continue
                    }
                    if (isConnected()) {
                        Thread.sleep(2_000L)
                        backoff = 3_000L
                        continue
                    }

                    // Si el P2P se quedó "conectando" demasiado, liberar.
                    if (connecting && SystemClock.elapsedRealtime() - lastP2pAttemptMs > P2P_TIMEOUT_MS) {
                        connecting = false
                        try {
                            channel?.let { manager?.cancelConnect(it, null) }
                        } catch (_: Exception) {
                        }
                    }

                    attempt()

                    Thread.sleep(backoff)
                    backoff = (backoff * 3 / 2).coerceAtMost(15_000L)
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "loop: ${e.message}")
                    try {
                        Thread.sleep(2_000L)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
        }.apply {
            name = "WifiBridgeLoop"
            isDaemon = true
            start()
        }
    }

    private fun attempt() {
        // 1) IP manual (LAN o hotspot). Se intenta siempre: en modo hotspot el
        //    teléfono puede no exponer la red AP como Network de Wi-Fi.
        val savedHost = StereoPrefs.wifiHost(appContext)
        if (!savedHost.isNullOrBlank() && tryTcp(savedHost, remember = false)) return

        if (hasWifiNetwork()) {
            // 3) Descubrimiento UDP en la Wi-Fi compartida.
            val lanHost = discoverLanHost()
            if (lanHost != null && tryTcp(lanHost, remember = true)) return
        }

        // 2) Grupo Wi-Fi Direct ya formado; el GO es el estéreo.
        val go = groupOwnerAddress
        if (groupFormed && !go.isNullOrBlank() && tryTcp(go, remember = false)) return

        // 4) Wi-Fi Direct.
        if (!p2pEnabled) {
            emit(if (wifiManager?.isWifiEnabled == true) "Wi-Fi Direct no disponible" else "Wi-Fi apagado")
            return
        }
        if (!hasP2pPermission()) {
            emit("Falta permiso Wi-Fi")
            return
        }
        val savedPeer = StereoPrefs.p2pAddress(appContext)
        if (!savedPeer.isNullOrBlank()) {
            connectPeerAddress(savedPeer)
        } else {
            discoverPeers()
        }
    }

    // ------------------------------------------------------------------
    // TCP
    // ------------------------------------------------------------------

    private fun tryTcp(host: String, remember: Boolean): Boolean {
        if (isConnected()) return true
        val candidate = Socket()
        return try {
            candidate.tcpNoDelay = true
            candidate.connect(InetSocketAddress(host, PORT), 3_500)
            val out = candidate.getOutputStream()
            socket = candidate
            output = out
            startReader(candidate)
            // Handshake: el servidor sólo añade el socket a su lista tras
            // validar el token.
            send(
                JSONObject()
                    .put("type", "HELLO")
                    .put("proto", PROTO)
                    .put("token", TOKEN)
                    .put("client", "IbizaCompanion")
                    .put("transport", "wifi")
            )
            if (!isConnected()) return false
            if (remember) StereoPrefs.setWifiHost(appContext, host)
            emit("Wi-Fi · $host")
            Log.i(TAG, "TCP conectado a $host:$PORT")
            true
        } catch (e: Exception) {
            try {
                candidate.close()
            } catch (_: Exception) {
            }
            false
        }
    }

    private fun startReader(sock: Socket) {
        Thread {
            try {
                BufferedReader(InputStreamReader(sock.getInputStream())).use { reader ->
                    while (running && !sock.isClosed) {
                        val line = reader.readLine() ?: break
                        if (line.isNotBlank()) {
                            try {
                                onLine(line)
                            } catch (e: Exception) {
                                Log.w(TAG, "onLine: ${e.message}")
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
            if (socket === sock) {
                closeSocket("conexión perdida")
            }
        }.apply {
            name = "WifiBridgeReader"
            isDaemon = true
            start()
        }
    }

    private fun closeSocket(reason: String) {
        val s = socket
        socket = null
        output = null
        try {
            s?.close()
        } catch (_: Exception) {
        }
        if (s != null) {
            Log.i(TAG, "socket cerrado: $reason")
            emit(reason)
        }
    }

    // ------------------------------------------------------------------
    // Descubrimiento UDP en LAN / hotspot
    // ------------------------------------------------------------------

    private fun hasWifiNetwork(): Boolean = wifiNetworks().isNotEmpty()

    private fun wifiNetworks(): List<android.net.Network> {
        val cm = connectivity ?: return emptyList()
        return try {
            cm.allNetworks.filter { network ->
                cm.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun discoverLanHost(): String? {
        val cm = connectivity ?: return null
        for (network in wifiNetworks()) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            val lp = cm.getLinkProperties(network) ?: continue
            val link = lp.linkAddresses.firstOrNull { it.address is Inet4Address } ?: continue
            val addr = link.address as Inet4Address
            val bcast = broadcastAddress(addr, link.prefixLength) ?: continue
            var ds: DatagramSocket? = null
            try {
                ds = DatagramSocket()
                network.bindSocket(ds)
                ds.broadcast = true
                ds.soTimeout = 900
                val msg = DISCOVER_REQUEST.toByteArray()
                ds.send(DatagramPacket(msg, msg.size, bcast, DISCOVERY_PORT))
                val buf = ByteArray(128)
                val reply = DatagramPacket(buf, buf.size)
                ds.receive(reply)
                val text = String(reply.data, 0, reply.length).trim()
                if (text.startsWith(DISCOVER_REPLY)) {
                    Log.i(TAG, "estéreo descubierto en ${reply.address.hostAddress}")
                    return reply.address.hostAddress
                }
            } catch (_: Exception) {
            } finally {
                try {
                    ds?.close()
                } catch (_: Exception) {
                }
            }
        }
        return null
    }

    private fun broadcastAddress(addr: Inet4Address, prefix: Int): InetAddress? {
        return try {
            if (prefix <= 0 || prefix > 32) {
                val parts = addr.hostAddress?.split('.') ?: return null
                if (parts.size != 4) return null
                return InetAddress.getByName("${parts[0]}.${parts[1]}.${parts[2]}.255")
            }
            val bytes = addr.address
            var ip = 0
            for (b in bytes) ip = (ip shl 8) or (b.toInt() and 0xFF)
            val mask = if (prefix == 32) 0 else (-1 shl (32 - prefix))
            val bcast = ip or mask.inv()
            InetAddress.getByAddress(
                byteArrayOf(
                    ((bcast ushr 24) and 0xFF).toByte(),
                    ((bcast ushr 16) and 0xFF).toByte(),
                    ((bcast ushr 8) and 0xFF).toByte(),
                    (bcast and 0xFF).toByte()
                )
            )
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------
    // Wi-Fi Direct
    // ------------------------------------------------------------------

    private fun initP2p() {
        if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)) {
            Log.w(TAG, "el teléfono no declara FEATURE_WIFI_DIRECT")
            return
        }
        val mgr = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        if (mgr == null) {
            Log.w(TAG, "sin WifiP2pManager")
            return
        }
        manager = mgr
        channel = try {
            mgr.initialize(appContext, Looper.getMainLooper()) {
                Log.w(TAG, "canal P2P perdido")
                emit("canal Wi-Fi Direct perdido")
            }
        } catch (e: Exception) {
            Log.w(TAG, "initialize P2P: ${e.message}")
            null
        }
    }

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        p2pEnabled = intent.getIntExtra(
                            WifiP2pManager.EXTRA_WIFI_STATE,
                            WifiP2pManager.WIFI_P2P_STATE_DISABLED
                        ) == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                        if (p2pEnabled) {
                            emit("Wi-Fi Direct listo")
                            discoverPeers()
                        } else {
                            emit("Wi-Fi Direct apagado")
                        }
                    }

                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        if (p2pEnabled) requestPeers()
                    }

                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        requestConnectionInfo()
                    }
                }
            }
        }
        receiver = r
        try {
            ContextCompat.registerReceiver(
                appContext,
                r,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            Log.w(TAG, "registrar receiver: ${e.message}")
        }
    }

    private fun requestConnectionInfo() {
        val mgr = manager ?: return
        val ch = channel ?: return
        try {
            mgr.requestConnectionInfo(ch) { info: WifiP2pInfo? ->
                if (info == null) return@requestConnectionInfo
                groupFormed = info.groupFormed
                if (!info.groupFormed) {
                    groupOwnerAddress = null
                    connecting = false
                    emit("Wi-Fi Direct sin grupo")
                    return@requestConnectionInfo
                }
                val host = info.groupOwnerAddress?.hostAddress
                groupOwnerAddress = host
                if (info.isGroupOwner) {
                    // El teléfono quedó como GO: el estéreo tendría que iniciar
                    // la conexión. Se intenta LAN como respaldo.
                    emit("Wi-Fi Direct: teléfono es GO; usando LAN")
                } else {
                    connecting = false
                    emit("Wi-Fi Direct · $host")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "requestConnectionInfo: ${e.message}")
        }
    }

    private fun requestPeers() {
        val mgr = manager ?: return
        val ch = channel ?: return
        try {
            mgr.requestPeers(ch) { list ->
                val mapped = list.deviceList.map { d ->
                    WifiPeerInfo(
                        name = d.deviceName?.takeIf { it.isNotBlank() } ?: "Estéreo",
                        address = d.deviceAddress,
                        status = deviceStatus(d)
                    )
                }.sortedBy { it.name.lowercase() }
                peers = mapped
                emit(detail)
                autoConnect(mapped)
            }
        } catch (e: Exception) {
            Log.w(TAG, "requestPeers: ${e.message}")
        }
    }

    private fun autoConnect(list: List<WifiPeerInfo>) {
        if (isConnected() || connecting) return
        val saved = StereoPrefs.p2pAddress(appContext)
        val chosen = list.firstOrNull { it.address == saved }
            ?: list.firstOrNull {
                it.name.contains("ibiza", true) || it.name.contains("dash", true)
            }
            ?: list.singleOrNull() // en el coche suele ser el único peer
            ?: return
        connectPeerAddress(chosen.address)
    }

    private fun discoverPeers(force: Boolean = false) {
        val mgr = manager ?: return
        val ch = channel ?: return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastDiscoveryMs < DISCOVERY_EVERY_MS) return
        lastDiscoveryMs = now
        try {
            mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    emit("buscando estéreo…")
                    // No todos los ROM envían PEERS_CHANGED; pedir la lista
                    // explícitamente un momento después del descubrimiento.
                    mainHandler.postDelayed({ requestPeers() }, 1500L)
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "discoverPeers: ${reasonName(reason)}")
                    emit("Wi-Fi Direct: ${reasonName(reason)}")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "discoverPeers: ${e.message}")
        }
    }

    private fun connectPeerAddress(address: String) {
        val mgr = manager ?: return
        val ch = channel ?: return
        if (isConnected()) return
        if (connecting && SystemClock.elapsedRealtime() - lastP2pAttemptMs < P2P_TIMEOUT_MS) return
        val config = try {
            WifiP2pConfig().apply {
                deviceAddress = address
                wps.setup = WpsInfo.PBC
                // 0 = preferir que el peer sea Group Owner (el estéreo).
                groupOwnerIntent = 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "config P2P: ${e.message}")
            return
        }
        connecting = true
        lastP2pAttemptMs = SystemClock.elapsedRealtime()
        try {
            mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    StereoPrefs.setP2pAddress(appContext, address)
                    emit("conectando Wi-Fi Direct…")
                    Log.i(TAG, "invitación P2P enviada a $address")
                    // Red de seguridad por si el ROM no emite CONNECTION_CHANGED.
                    mainHandler.postDelayed({ requestConnectionInfo() }, 5000L)
                }

                override fun onFailure(reason: Int) {
                    connecting = false
                    Log.w(TAG, "connect P2P: ${reasonName(reason)}")
                    emit("Wi-Fi Direct: ${reasonName(reason)}")
                    if (reason == WifiP2pManager.BUSY) {
                        try {
                            mgr.cancelConnect(ch, null)
                        } catch (_: Exception) {
                        }
                    }
                }
            })
        } catch (e: Exception) {
            connecting = false
            Log.w(TAG, "connect: ${e.message}")
        }
    }

    private fun hasP2pPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun deviceStatus(device: WifiP2pDevice): String = when (device.status) {
        WifiP2pDevice.AVAILABLE -> "disponible"
        WifiP2pDevice.INVITED -> "invitado"
        WifiP2pDevice.CONNECTED -> "conectado"
        WifiP2pDevice.FAILED -> "falló"
        WifiP2pDevice.UNAVAILABLE -> "no disponible"
        else -> "—"
    }

    private fun reasonName(reason: Int): String = when (reason) {
        WifiP2pManager.P2P_UNSUPPORTED -> "no soportado"
        WifiP2pManager.BUSY -> "ocupado"
        WifiP2pManager.ERROR -> "error"
        else -> "código $reason"
    }

    private fun emit(newDetail: String) {
        detail = newDetail
        val state = State(
            enabled = StereoPrefs.wifiEnabled(appContext),
            connected = isConnected(),
            host = socket?.inetAddress?.hostAddress,
            detail = newDetail,
            peers = peers
        )
        try {
            onState?.invoke(state)
        } catch (e: Exception) {
            Log.w(TAG, "onState: ${e.message}")
        }
    }
}
