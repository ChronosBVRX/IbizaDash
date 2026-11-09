package com.chronosbvrx.ibizadash.nav

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.chronosbvrx.ibizadash.Prefs
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

/**
 * Puente Wi-Fi con el teléfono.
 *
 * Tres capas independientes:
 *  1. Servidor TCP en [NavBridgeProtocol.TCP_PORT] (funciona en Wi-Fi Direct,
 *     hotspot o LAN).
 *  2. Respondedor UDP de descubrimiento en [NavBridgeProtocol.DISCOVERY_PORT].
 *  3. Grupo Wi-Fi Direct creado por el estéreo (Group Owner) para que el
 *     teléfono se conecte sin depender de una red compartida.
 *
 * Si el chip del estéreo no soporta Wi-Fi Direct, las capas 1 y 2 siguen
 * funcionando cuando ambos equipos comparten una red. No hay respaldo por
 * Bluetooth: este es el único canal de datos con el teléfono.
 */
object WifiNavBridgeServer {
    private const val TAG = "WifiNavBridge"

    @Volatile
    private var running = false

    private var appContext: Context? = null
    private var serverThread: Thread? = null
    private var discoveryThread: Thread? = null
    private var p2pTicker: Thread? = null
    private var serverSocket: ServerSocket? = null
    private var discoverySocket: DatagramSocket? = null

    private val liveOuts =
        Collections.synchronizedSet(mutableSetOf<OutputStream>())

    // Wi-Fi Direct
    private var p2pManager: WifiP2pManager? = null
    private var p2pChannel: WifiP2pManager.Channel? = null
    private var p2pReceiver: BroadcastReceiver? = null
    private var lastP2pCheckMs = 0L
    private var wifiAutoEnableTried = false
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Frecuencia del grupo P2P en MHz (0 = todavía desconocida). */
    @Volatile
    var groupFrequencyMhz: Int = 0
        private set

    /** El chip rechazó 5 GHz: no se reintenta hasta reiniciar o cambiar el ajuste. */
    @Volatile
    private var bandLocked2Ghz = false

    /** Se acaba de recrear el grupo para cambiar de banda; falta leer el resultado. */
    @Volatile
    private var awaitingBandResult = false

    private var lastBandAttemptMs = 0L
    private val bandCooldownMs = 10 * 60_000L

    /** Texto de estado para la UI de ajustes rápidos. */
    @Volatile
    var p2pState: String = "iniciando"
        private set

    @Volatile
    var groupName: String? = null
        private set

    @Volatile
    var clientCount: Int = 0
        private set

    /** Último mensaje recibido de cualquier cliente; evita elegir un Wi-Fi mudo. */
    @Volatile
    private var lastClientRxMs = 0L

    fun start(context: Context) {
        if (running) return
        running = true
        appContext = context.applicationContext
        Log.i(TAG, "iniciando puente Wi-Fi (TCP ${NavBridgeProtocol.TCP_PORT})")
        startTcpServer()
        startDiscovery()
        startP2p(context.applicationContext)
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) { }
        try { discoverySocket?.close() } catch (_: Exception) { }
        synchronized(liveOuts) {
            liveOuts.forEach { out -> try { out.close() } catch (_: Exception) { } }
            liveOuts.clear()
        }
        clientCount = 0
        try {
            p2pReceiver?.let { appContext?.unregisterReceiver(it) }
        } catch (_: Exception) {
        }
        p2pReceiver = null
        p2pChannel?.let { ch ->
            try { p2pManager?.removeGroup(ch, null) } catch (_: Exception) { }
            try { ch.close() } catch (_: Exception) { }
        }
        p2pChannel = null
        p2pManager = null
        serverThread?.interrupt()
        discoveryThread?.interrupt()
        p2pTicker?.interrupt()
        serverThread = null
        discoveryThread = null
        p2pTicker = null
    }

    fun hasClients(): Boolean {
        val connected = synchronized(liveOuts) { liveOuts.isNotEmpty() }
        if (!connected) return false
        // El teléfono manda PING cada 15 s. Si lleva más de un minuto callado,
        // el socket está muerto aunque el sistema aún no lo haya cerrado.
        val last = lastClientRxMs
        return last == 0L || SystemClock.elapsedRealtime() - last < 60_000L
    }

    /** Envía a todos los clientes Wi-Fi. Devuelve true si alguno lo aceptó. */
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
            } catch (_: Exception) {
                removeOutput(out)
            }
        }
        if (sent) Log.i(TAG, "tx* ${obj.optString("type")}")
        return sent
    }

    /** Envía al primer cliente Wi-Fi disponible. */
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
            } catch (_: Exception) {
                removeOutput(out)
            }
        }
        return false
    }

    private fun removeOutput(out: OutputStream) {
        synchronized(liveOuts) {
            liveOuts.remove(out)
            clientCount = liveOuts.size
        }
    }

    // ------------------------------------------------------------------
    // TCP
    // ------------------------------------------------------------------

    private fun startTcpServer() {
        serverThread = Thread {
            while (running) {
                try {
                    val ss = ServerSocket()
                    ss.reuseAddress = true
                    ss.bind(InetSocketAddress(NavBridgeProtocol.TCP_PORT))
                    serverSocket = ss
                    Log.i(TAG, "TCP escuchando en 0.0.0.0:${NavBridgeProtocol.TCP_PORT}")
                    while (running) {
                        val client = try {
                            ss.accept()
                        } catch (e: Exception) {
                            if (!running) break
                            Log.w(TAG, "accept: ${e.message}")
                            continue
                        }
                        Thread {
                            try {
                                handleClient(client)
                            } catch (e: Exception) {
                                Log.w(TAG, "cliente TCP: ${e.message}")
                            } finally {
                                try { client.close() } catch (_: Exception) { }
                            }
                        }.apply {
                            name = "WifiNavClient"
                            isDaemon = true
                            start()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "servidor TCP: ${e.message}")
                    try { serverSocket?.close() } catch (_: Exception) { }
                    serverSocket = null
                    if (!running) break
                    try { Thread.sleep(4000L) } catch (_: InterruptedException) { break }
                }
            }
        }.apply {
            name = "WifiNavTcp"
            isDaemon = true
            start()
        }
    }

    private fun handleClient(socket: Socket) {
        val context = appContext ?: return
        try {
            socket.tcpNoDelay = true
            socket.keepAlive = true
        } catch (_: Exception) {
        }
        val reader = try {
            BufferedReader(InputStreamReader(socket.getInputStream()))
        } catch (e: Exception) {
            Log.w(TAG, "input: ${e.message}")
            return
        }
        val out = try {
            socket.getOutputStream()
        } catch (e: Exception) {
            Log.w(TAG, "output: ${e.message}")
            return
        }
        var registered = false
        var authenticated = false
        try {
            while (running) {
                val line = try { reader.readLine() } catch (_: Exception) { null } ?: break
                if (line.isBlank()) continue

                if (!authenticated) {
                    val hello = try { JSONObject(line) } catch (_: Exception) { null }
                    if (hello == null ||
                        hello.optString("type") != "HELLO" ||
                        hello.optString("token") != NavBridgeProtocol.NAV_TOKEN
                    ) {
                        Log.w(TAG, "cliente TCP rechazado: falta HELLO/token")
                        try {
                            writeLine(
                                out,
                                JSONObject().put("type", "ERROR").put("reason", "auth")
                            )
                        } catch (_: Exception) {
                        }
                        break
                    }
                    authenticated = true
                    lastClientRxMs = SystemClock.elapsedRealtime()
                    synchronized(liveOuts) {
                        liveOuts.add(out)
                        clientCount = liveOuts.size
                    }
                    registered = true
                    writeLine(
                        out,
                        JSONObject()
                            .put("type", "WELCOME")
                            .put("proto", NavBridgeProtocol.PROTOCOL_VERSION)
                            .put("transport", "wifi")
                            .put(
                                "stereoVersionCode",
                                com.chronosbvrx.ibizadash.ota.OtaReceiver
                                    .installedVersionCode(context)
                            )
                            .put(
                                "stereoVersionName",
                                com.chronosbvrx.ibizadash.ota.OtaReceiver
                                    .installedVersionName(context)
                            )
                    )
                    // Snapshot inmediato para que el teléfono pinte la ruta
                    // activa aunque el estéreo llevara horas encendido.
                    writeLine(out, NavStateRelay.snapshotObject())
                    // Fuente de posición: por defecto el GPS del estéreo.
                    // El teléfono deja de emitir PHONE_NAV_STATE si no se usa.
                    writeLine(
                        out,
                        JSONObject()
                            .put("type", "NAV_MODE")
                            .put("gps", if (Prefs.usePhoneGps(context)) "phone" else "stereo")
                    )
                    Log.i(
                        TAG,
                        "cliente Wi-Fi autenticado ${socket.inetAddress?.hostAddress} " +
                            "(total=$clientCount)"
                    )
                    continue
                }

                if (!line.contains("\"TILE_BATCH\"") && !line.contains("\"MAP_FRAME\"")) {
                    Log.i(TAG, "rx ${line.take(120)}")
                }
                NavBridgeProtocol.handle(context, line) { reply -> writeLine(out, reply) }
                lastClientRxMs = SystemClock.elapsedRealtime()
            }
        } catch (e: Exception) {
            Log.w(TAG, "lectura TCP: ${e.message}")
        } finally {
            if (registered) removeOutput(out)
            try { socket.close() } catch (_: Exception) { }
            Log.i(TAG, "cliente Wi-Fi desconectado (total=$clientCount)")
        }
    }

    private fun writeLine(out: OutputStream, obj: JSONObject) {
        synchronized(out) {
            out.write((obj.toString() + "\n").toByteArray())
            out.flush()
        }
    }

    // ------------------------------------------------------------------
    // Descubrimiento UDP (LAN / hotspot)
    // ------------------------------------------------------------------

    private fun startDiscovery() {
        discoveryThread = Thread {
            try {
                val ds = DatagramSocket(null)
                ds.reuseAddress = true
                ds.bind(InetSocketAddress(NavBridgeProtocol.DISCOVERY_PORT))
                discoverySocket = ds
                Log.i(TAG, "UDP descubrimiento en ${NavBridgeProtocol.DISCOVERY_PORT}")
                while (running) {
                    val buf = ByteArray(128)
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        ds.receive(packet)
                    } catch (e: Exception) {
                        if (!running) break
                        continue
                    }
                    val msg = String(packet.data, 0, packet.length).trim()
                    if (msg == NavBridgeProtocol.DISCOVER_REQUEST) {
                        val reply = "${NavBridgeProtocol.DISCOVER_REPLY}:${NavBridgeProtocol.TCP_PORT}".toByteArray()
                        try {
                            ds.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
                            Log.i(TAG, "discovery respondido a ${packet.address.hostAddress}")
                        } catch (_: Exception) {
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "descubrimiento UDP: ${e.message}")
            }
        }.apply {
            name = "WifiNavDiscovery"
            isDaemon = true
            start()
        }
    }

    /** Devuelve la IP que ve el teléfono, útil para mostrar en Ajustes. */
    fun localIpv4(): String? {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
            val preferred = interfaces.filter {
                it.name.startsWith("wlan") || it.name.startsWith("p2p")
            }.ifEmpty { interfaces }
            for (iface in preferred) {
                val addr = iface.inetAddresses.toList()
                    .filterIsInstance<java.net.Inet4Address>()
                    .firstOrNull { !it.isLoopbackAddress }
                if (addr != null) return addr.hostAddress
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------
    // Wi-Fi Direct (Group Owner)
    // ------------------------------------------------------------------

    private fun startP2p(context: Context) {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)) {
            p2pState = "sin Wi-Fi Direct"
            Log.w(TAG, "el equipo no declara FEATURE_WIFI_DIRECT")
            return
        }
        val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        if (manager == null) {
            p2pState = "sin soporte P2P"
            return
        }
        val channel = try {
            manager.initialize(context, Looper.getMainLooper()) {
                Log.w(TAG, "canal P2P perdido")
            }
        } catch (e: Exception) {
            Log.w(TAG, "P2P initialize: ${e.message}")
            p2pState = "P2P no disponible"
            return
        }
        p2pManager = manager
        p2pChannel = channel

        registerP2pReceiver(context)
        p2pState = "buscando"
        p2pTicker = Thread {
            while (running) {
                try {
                    val now = System.currentTimeMillis()
                    if (now - lastP2pCheckMs > 15_000L) {
                        lastP2pCheckMs = now
                        mainHandler.post { ensureGroup() }
                    }
                    Thread.sleep(4_000L)
                } catch (_: InterruptedException) {
                    break
                } catch (_: Exception) {
                }
            }
        }.apply {
            name = "WifiNavP2p"
            isDaemon = true
            start()
        }
    }

    private fun registerP2pReceiver(context: Context) {
        if (p2pReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val enabled = intent.getIntExtra(
                            WifiP2pManager.EXTRA_WIFI_STATE,
                            WifiP2pManager.WIFI_P2P_STATE_DISABLED
                        ) == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                        p2pState = if (enabled) "listo" else "Wi-Fi Direct apagado"
                        if (enabled) mainHandler.post { ensureGroup() }
                    }

                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val info = try {
                            intent.getParcelableExtra<android.net.wifi.p2p.WifiP2pInfo>(
                                WifiP2pManager.EXTRA_WIFI_P2P_INFO
                            )
                        } catch (_: Exception) {
                            null
                        }
                        if (info?.groupFormed == true) {
                            p2pState = if (info.isGroupOwner) {
                                if (groupFrequencyMhz >= 5000) "grupo 5 GHz" else "grupo activo"
                            } else {
                                "cliente P2P"
                            }
                            Log.i(
                                TAG,
                                "P2P formado GO=${info.isGroupOwner} " +
                                    "ip=${info.groupOwnerAddress?.hostAddress}"
                            )
                            if (info.isGroupOwner) {
                                // Confirmar la banda real del grupo (5 GHz o 2,4 GHz).
                                try {
                                    p2pChannel?.let { ch ->
                                        p2pManager?.requestGroupInfo(ch) { g ->
                                            if (g != null) {
                                                groupFrequencyMhz = groupFrequency(g)
                                                p2pState = groupStateText(g)
                                            }
                                        }
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        } else {
                            if (p2pState.startsWith("grupo") || p2pState.contains("cliente")) {
                                p2pState = "grupo cerrado"
                            }
                        }
                    }
                }
            }
        }
        p2pReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        try {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            Log.w(TAG, "registrar receiver P2P: ${e.message}")
        }
    }

    private fun ensureGroup() {
        val manager = p2pManager ?: return
        val channel = p2pChannel ?: return
        val context = appContext ?: return
        if (!running) return

        val wifi = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifi?.isWifiEnabled != true) {
            p2pState = "WiFi apagado"
            // Wi-Fi Direct necesita la radio encendida. Se intenta una sola vez
            // para no pelear con el usuario si decide apagarla.
            if (!wifiAutoEnableTried) {
                wifiAutoEnableTried = true
                try {
                    @Suppress("DEPRECATION")
                    val ok = wifi?.setWifiEnabled(true) == true
                    Log.i(TAG, "Wi-Fi apagado; encendiendo=$ok")
                    if (ok) p2pState = "encendiendo WiFi"
                } catch (e: Exception) {
                    Log.w(TAG, "encender Wi-Fi: ${e.message}")
                }
            }
            return
        }

        try {
            manager.requestGroupInfo(channel) { group ->
                if (group != null) {
                    groupName = group.networkName
                    val freq = groupFrequency(group)
                    groupFrequencyMhz = freq
                    if (awaitingBandResult) {
                        awaitingBandResult = false
                        if (freq in 1..4999) {
                            bandLocked2Ghz = true
                            Log.i(
                                TAG,
                                "el chip no abrió el grupo en 5 GHz; se queda en " +
                                    "$freq MHz"
                            )
                        } else {
                            Log.i(TAG, "grupo Wi-Fi Direct en 5 GHz ($freq MHz)")
                        }
                    }
                    if (shouldUpgradeBand(group)) {
                        Log.i(
                            TAG,
                            "grupo en $freq MHz sin clientes; se recrea en 5 GHz"
                        )
                        awaitingBandResult = true
                        lastBandAttemptMs = System.currentTimeMillis()
                        p2pState = "pasando a 5 GHz"
                        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                            override fun onSuccess() {
                                createGroupPreferred(manager, channel, prefer5 = true)
                            }

                            override fun onFailure(reason: Int) {
                                Log.w(TAG, "removeGroup: ${reasonName(reason)}")
                                createGroupPreferred(manager, channel, prefer5 = true)
                            }
                        })
                        return@requestGroupInfo
                    }
                    p2pState = groupStateText(group)
                    return@requestGroupInfo
                }
                if (!running) return@requestGroupInfo
                createGroupPreferred(manager, channel, prefer5 = prefer5Ghz())
            }
        } catch (e: Exception) {
            Log.w(TAG, "requestGroupInfo: ${e.message}")
        }
    }

    private fun prefer5Ghz(): Boolean {
        // La banda del grupo se elige con API 29+; antes no hay forma pública.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            appContext?.let { Prefs.p2pPrefer5Ghz(it) } ?: true
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Frecuencia del grupo. `WifiP2pGroup.getFrequency()` es API 29+, así que en
     * el estéreo Android 8.1 (API 27) hay que devolver 0 sin llamarlo.
     */
    private fun groupFrequency(group: WifiP2pGroup?): Int {
        if (group == null) return 0
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        return try {
            group.frequency
        } catch (_: Throwable) {
            0
        }
    }

    /**
     * ¿Conviene recrear el grupo en 5 GHz? Solo si el usuario lo prefiere, el
     * chip no lo rechazó antes, el grupo actual es de 2,4 GHz y no hay ningún
     * teléfono conectado (recrear el grupo corta el enlace unos segundos).
     */
    private fun shouldUpgradeBand(group: WifiP2pGroup): Boolean {
        if (!running) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        if (!prefer5Ghz() || bandLocked2Ghz) return false
        if (groupFrequency(group) >= 5000) return false
        if (clientCount > 0 || hasClients()) return false
        val now = System.currentTimeMillis()
        return lastBandAttemptMs == 0L || now - lastBandAttemptMs > bandCooldownMs
    }

    private fun groupStateText(group: WifiP2pGroup?): String {
        val freq = groupFrequency(group).takeIf { it > 0 } ?: groupFrequencyMhz
        return if (freq >= 5000) "grupo 5 GHz" else "grupo listo"
    }

    /**
     * Crea el grupo Wi-Fi Direct pidiendo 5 GHz cuando se puede. Si el chip no
     * es dual-band, cae automáticamente a la banda automática (2,4 GHz) para
     * no dejar al teléfono sin enlace.
     */
    private fun createGroupPreferred(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        prefer5: Boolean
    ) {
        val listener = object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "grupo Wi-Fi Direct creado (prefer5=$prefer5)")
                manager.requestGroupInfo(channel) { g ->
                    groupName = g?.networkName
                    groupFrequencyMhz = groupFrequency(g)
                    p2pState = groupStateText(g)
                }
            }

            override fun onFailure(reason: Int) {
                Log.w(TAG, "createGroup(prefer5=$prefer5): ${reasonName(reason)}")
                if (prefer5) {
                    // El chip no acepta 5 GHz: se queda en 2,4 GHz hasta reiniciar.
                    bandLocked2Ghz = true
                    awaitingBandResult = false
                    createGroupPreferred(manager, channel, prefer5 = false)
                } else {
                    p2pState = "P2P: ${reasonName(reason)}"
                }
            }
        }
        try {
            if (prefer5 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                P2pBand.create5Ghz(manager, channel, listener)
            } else {
                manager.createGroup(channel, listener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "createGroup: ${e.message}")
            if (prefer5) {
                bandLocked2Ghz = true
                awaitingBandResult = false
                createGroupPreferred(manager, channel, prefer5 = false)
            } else {
                p2pState = "P2P: ${e.message}"
            }
        }
    }

    /**
     * Reaplica la preferencia de banda después de cambiarla en Ajustes.
     * Si hay un teléfono conectado, el cambio espera a que se desconecte.
     */
    fun refreshBandPreference() {
        bandLocked2Ghz = false
        awaitingBandResult = false
        lastBandAttemptMs = 0L
        if (running) mainHandler.post { ensureGroup() }
    }

    private fun reasonName(reason: Int): String = when (reason) {
        WifiP2pManager.P2P_UNSUPPORTED -> "no soportado"
        WifiP2pManager.BUSY -> "ocupado"
        WifiP2pManager.ERROR -> "error"
        else -> "código $reason"
    }

    /** Estado compacto para el panel de ajustes rápidos. */
    fun statusText(): String {
        val clients = clientCount
        val ip = localIpv4()
        val group = p2pState == "grupo listo" ||
            p2pState == "grupo creado" ||
            p2pState == "grupo 5 GHz" ||
            p2pState == "grupo activo"
        return when {
            clients > 0 && hasClients() -> "Wi-Fi · $clients"
            clients > 0 -> "Wi-Fi · sin respuesta"
            group && groupFrequencyMhz >= 5000 -> "Wi-Fi Direct 5G"
            group -> "Wi-Fi Direct 2,4G"
            p2pState == "WiFi apagado" -> "WiFi apagado"
            !ip.isNullOrBlank() -> "Wi-Fi · $ip"
            p2pState.isBlank() -> "Wi-Fi · —"
            else -> p2pState
        }
    }
}

/**
 * Creación del grupo Wi-Fi Direct en 5 GHz.
 *
 * Vive aparte a propósito: `WifiP2pConfig.Builder` y `createGroup(Channel,
 * WifiP2pConfig, ActionListener)` son API 29+. En el estéreo Android 8.1 este
 * objeto nunca se carga, así que el verificador no toca clases que no existen.
 */
private object P2pBand {
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    fun create5Ghz(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        listener: WifiP2pManager.ActionListener
    ) {
        // El framework solo aplica la banda si el config es "válido como grupo":
        // dirección no vacía + SSID + passphrase.
        val config = WifiP2pConfig.Builder()
            .setDeviceAddress(android.net.MacAddress.fromString("02:00:00:00:00:01"))
            .setNetworkName("DIRECT-IbizaDash")
            .setPassphrase("ibizadash-5g")
            .setGroupOperatingBand(WifiP2pConfig.GROUP_OWNER_BAND_5GHZ)
            .build()
        manager.createGroup(channel, config, listener)
    }
}
