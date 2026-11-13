package com.chronosbvrx.ibizadash.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.chronosbvrx.ibizadash.Prefs
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.math.sin

/**
 * Minimal ELM327 Bluetooth OBD-II client.
 *
 * Runs on its own thread, speaks the standard AT/PID protocol and publishes
 * [ObdTelemetry]. Reads RPM, coolant, engine load, throttle, intake temp,
 * mixture trim, timing, battery voltage, MIL/DTC and VIN.
 *
 * A demo mode generates plausible values so the UI can be validated without
 * an adapter (used by `--ez obd_demo true` and the OBD dialog).
 */
class Elm327Client(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private var thread: Thread? = null
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var ble: BleElm327Link? = null
    private var connectFailures = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var running = false

    @Volatile
    private var generation = 0

    private val onceQueue = java.util.concurrent.ConcurrentLinkedQueue<() -> Unit>()

    /** PIDs que la ECU declaró soportar en 0100/0120/0140. */
    private val supportedPids = java.util.Collections.synchronizedSet(HashSet<Int>())

    /**
     * El mapa de PIDs solo se usa si se pudo leer y contiene RPM (0x0C), que es
     * obligatorio en OBD-II. Si el clon responde algo raro, se ignora y se
     * consulta todo como antes (fail-open).
     */
    @Volatile
    private var pidMaskTrusted = false

    /**
     * Última lectura válida de un PID con su marca de tiempo.
     *
     * Antes se conservaba el último valor aunque la ECU dejara de responder:
     * con el socket abierto pero sin datos, la UI seguía mostrando RPM o
     * velocidad viejas como si fueran actuales (y el velocímetro no caía a
     * GPS). Ahora cada dato caduca por separado.
     */
    private class FreshReading {
        private val values = HashMap<String, Float>()
        private val stamps = HashMap<String, Long>()

        fun put(key: String, value: Float?, now: Long) {
            if (value == null || !value.isFinite()) return
            values[key] = value
            stamps[key] = now
        }

        /** Devuelve el valor solo si sigue vigente; null si nunca llegó o caducó. */
        fun get(key: String, now: Long, ttlMs: Long): Float? {
            val stamp = stamps[key] ?: return null
            if (now - stamp > ttlMs) return null
            return values[key]
        }

        fun clear() {
            values.clear()
            stamps.clear()
        }
    }

    /** Relee MIL/DTC sin reconectar (se ejecuta en el hilo OBD, no bloquea la UI). */
    fun refreshDtcs() {
        if (!isLive()) {
            ObdConsole.info("= Sin conexión con el adaptador. Enciende el contacto y verifica el OBDII.")
            return
        }
        onceQueue.add {
            if (ObdRepository.current.demo) {
                ObdConsole.info("= Modo demo: conecta el adaptador real para releer fallas")
                return@add
            }
            try {
                val (mil, count, codes) = queryDtcs()
                if (mil != null || codes.isNotEmpty()) {
                    ObdRepository.publish(ObdRepository.current.copy(milOn = mil, dtcCount = count, dtcs = codes))
                    ObdConsole.info("= Fallas releídas: ${if (codes.isEmpty()) "sin códigos" else codes.joinToString(", ")}")
                } else {
                    ObdConsole.info("= Sin respuesta al releer fallas (¿contacto apagado?)")
                }
            } catch (_: Exception) {
                ObdConsole.info("= No se pudo releer fallas: adaptador sin respuesta")
            }
        }
    }

    /** Relee el VIN (0902) sin reconectar. */
    fun probeVin() {        if (!isLive()) {
            ObdConsole.info("= Sin conexión con el adaptador. Enciende el contacto y verifica el OBDII.")
            return
        }
        onceQueue.add {
            if (ObdRepository.current.demo) {
                ObdConsole.info("= Modo demo: conecta el adaptador real para leer el VIN")
                return@add
            }
            val vin = queryVin()
            if (vin != null) {
                ObdRepository.publish(ObdRepository.current.copy(vin = vin))
                ObdConsole.info("= VIN: $vin")
            } else {
                ObdConsole.info("= 0902 → sin respuesta (normal en clones con mensajes largos)")
            }
        }
    }

    fun connect(mac: String?, demo: Boolean = false) {
        stop()
        val gen = ++generation
        running = true
        thread = Thread {
            try {
                if (demo) {
                    runDemo(gen)
                } else {
                    runReal(mac, gen)
                }
            } catch (_: InterruptedException) {
                // stop()/new session; normal shutdown
            } catch (e: Throwable) {
                Log.e(TAG, "OBD thread crashed", e)
            }
        }.apply {
            name = "ObdElm327"
            isDaemon = true
            start()
        }
    }

    /**
     * Escanea clásico + BLE para el selector de adaptadores.
     * El callback se entrega en el hilo principal.
     */
    fun discoverDevices(onDone: (List<ObdDevice>) -> Unit) {
        ObdConsole.info("= Buscando adaptadores OBD (Bluetooth clásico y BLE)…")
        Thread {
            val list = try {
                ObdScanner.scan(context, 12_000L)
            } catch (e: Exception) {
                Log.w(TAG, "discoverDevices: ${e.message}")
                emptyList()
            }
            if (list.isNotEmpty()) {
                ObdConsole.info(
                    "= Encontrados: " + list.joinToString(", ") { it.label }.take(240)
                )
            } else {
                ObdConsole.info("= No se encontró ningún dispositivo Bluetooth")
            }
            mainHandler.post { onDone(list) }
        }.apply {
            name = "ObdScan"
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        generation++
        onceQueue.clear()
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        try {
            ble?.close()
        } catch (_: Exception) {
        }
        socket = null
        input = null
        output = null
        ble = null
        thread = null
        if (ObdRepository.current.connected) {
            ObdRepository.publish(ObdRepository.current.copy(connected = false, error = null))
        }
    }

    private fun alive(gen: Int): Boolean = running && generation == gen

    private fun isLive(): Boolean {
        return try {
            (socket?.isConnected == true || ble?.isConnected() == true) &&
                ObdRepository.current.connected
        } catch (_: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------
    // Demo
    // ------------------------------------------------------------------

    private fun runDemo(gen: Int) {
        val start = SystemClock.elapsedRealtime()
        while (alive(gen)) {
            val t = (SystemClock.elapsedRealtime() - start) / 1000.0
            val rpm = (900 + 320 * sin(t / 2.4) + 90 * sin(t / 0.7)).toInt().coerceIn(0, 6500)
            val load = (28 + 18 * sin(t / 3.1)).toFloat().coerceIn(0f, 100f)
            val throttle = (12 + 8 * sin(t / 2.2)).toFloat().coerceIn(0f, 100f)
            val coolant = (88 + 2 * sin(t / 12)).toFloat()
            val intake = (31 + 2 * sin(t / 9)).toFloat()
            val battery = (14.1 + 0.15 * sin(t / 4)).toFloat()

            ObdRepository.publish(
                ObdTelemetry(
                    connected = true,
                    demo = true,
                    deviceName = "OBD DEMO",
                    rpm = rpm,
                    coolantC = coolant,
                    loadPct = load,
                    throttlePct = throttle,
                    intakeC = intake,
                    batteryV = battery,
                    fuelLevelPct = 62f,
                    milOn = false,
                    dtcCount = 0,
                    dtcs = emptyList(),
                    vin = "VSSZZZ6JZBR000000"
                )
            )
            Thread.sleep(500L)
        }
    }

    // ------------------------------------------------------------------
    // Real ELM327 session
    // ------------------------------------------------------------------

    private fun runReal(mac: String?, gen: Int) {
        var last = ObdTelemetry()
        var currentMac = mac
        val tried = LinkedHashSet<String>()
        val fast = FreshReading()
        val slow = FreshReading()
        while (alive(gen)) {
            val device = resolveDevice(currentMac, tried)
            if (device == null) {
                tried.clear()
                ObdRepository.publish(
                    last.copy(
                        connected = false,
                        error = "Buscando adaptador OBD (enciende el contacto y revisa el ELM327)"
                    )
                )
                Thread.sleep(6000L)
                continue
            }
            try {
                if (!openLink(device)) throw IOException("OBD link failed")
                connectFailures = 0
                tried.clear()
                val connectedAddress = safeAddress(device)
                if (connectedAddress.isNotBlank()) {
                    currentMac = connectedAddress
                    Prefs.setObdMac(context, connectedAddress)
                }
                last = last.copy(
                    connected = true,
                    demo = false,
                    deviceName = runCatching { device.name }.getOrNull(),
                    error = null
                )

                initialize()
                fast.clear()
                slow.clear()
                val vin = queryVin()
                val (mil, dtcCount, dtcs) = queryDtcs()
                last = last.copy(vin = vin, milOn = mil, dtcCount = dtcCount, dtcs = dtcs)
                ObdRepository.publish(last)

                var slowCounter = 0
                var silentCycles = 0
                while (alive(gen)) {
                    // Si el enlace murió (Broken pipe / BLE caído), no seguir
                    // consultando el socket muerto: se fuerza reapertura.
                    val bleLink = ble
                    if (bleLink != null && !bleLink.isConnected()) {
                        throw IOException("BLE link lost")
                    }
                    val stSocket = socket
                    if (stSocket == null && bleLink == null) {
                        throw IOException("OBD link lost")
                    }
                    val now = SystemClock.elapsedRealtime()
                    // Sondeo ligero: RPM cada ciclo; carga y acelerador cada 8.
                    // La velocidad ya no se pide: este coche no responde 010D y
                    // el velocímetro usa el GPS.
                    val rpm = if (supports(0x0C)) queryValue("010C", "410C") { bytes ->
                        if (bytes.size >= 2) {
                            (((bytes[0] * 256) + bytes[1]) / 4).toFloat()
                        } else {
                            null
                        }
                    } else null
                    slowCounter++
                    val load = if (slowCounter % 8 == 0 && supports(0x04)) queryValue("0104", "4104") { bytes ->
                        if (bytes.isNotEmpty()) bytes[0] * 100f / 255f else null
                    } else null
                    val throttle = if (slowCounter % 8 == 0 && supports(0x11)) queryValue("0111", "4111") { bytes ->
                        if (bytes.isNotEmpty()) bytes[0] * 100f / 255f else null
                    } else null

                    fast.put(K_RPM, rpm, now)
                    fast.put(K_LOAD, load, now)
                    fast.put(K_THROTTLE, throttle, now)

                    // Bloque lento completo cada 10 ciclos (~10 s con sleep
                    // 1000 ms): puebla el dashboard tras cada reconexión y
                    // refresca sin ráfagas constantes.
                    if (slowCounter % 10 == 0) {
                        val coolant = if (supports(0x05)) queryValue("0105", "4105") { bytes ->
                            if (bytes.isNotEmpty()) (bytes[0] - 40).toFloat() else null
                        } else null
                        val intake = if (supports(0x0F)) queryValue("010F", "410F") { bytes ->
                            if (bytes.isNotEmpty()) (bytes[0] - 40).toFloat() else null
                        } else null
                        val battery = (if (supports(0x42)) queryValue("0142", "4142") { bytes ->
                            if (bytes.size >= 2) ((bytes[0] * 256) + bytes[1]) / 1000f else null
                        } else null) ?: queryAtrv()
                        val fuelTrim = if (supports(0x06)) queryValue("0106", "4106") { bytes ->
                            if (bytes.isNotEmpty()) (bytes[0] - 128) * 100f / 128f else null
                        } else null
                        val timing = if (supports(0x0E)) queryValue("010E", "410E") { bytes ->
                            if (bytes.isNotEmpty()) bytes[0] / 2f - 64f else null
                        } else null
                        slow.put(K_COOLANT, coolant, now)
                        slow.put(K_INTAKE, intake, now)
                        slow.put(K_BATTERY, battery, now)
                        slow.put(K_TRIM, fuelTrim, now)
                        slow.put(K_TIMING, timing, now)
                    }

                    // Un valor solo sigue visible mientras esté vigente: si la
                    // ECU deja de contestar, el campo pasa a "sin lectura".
                    last = last.copy(
                        connected = true,
                        rpm = fast.get(K_RPM, now, FAST_TTL_MS)?.toInt(),
                        loadPct = fast.get(K_LOAD, now, FAST_TTL_MS),
                        throttlePct = fast.get(K_THROTTLE, now, FAST_TTL_MS),
                        coolantC = slow.get(K_COOLANT, now, SLOW_TTL_MS),
                        intakeC = slow.get(K_INTAKE, now, SLOW_TTL_MS),
                        fuelTrimPct = slow.get(K_TRIM, now, SLOW_TTL_MS),
                        timingAdvanceDeg = slow.get(K_TIMING, now, SLOW_TTL_MS),
                        batteryV = slow.get(K_BATTERY, now, SLOW_TTL_MS),
                        updatedAtMs = now
                    )
                    if (alive(gen)) ObdRepository.publish(last)
                    Log.d(
                        TAG,
                        "telemetry rpm=${last.rpm} coolant=${last.coolantC} " +
                            "load=${last.loadPct} throttle=${last.throttlePct} " +
                            "trim=${last.fuelTrimPct} timing=${last.timingAdvanceDeg} bat=${last.batteryV}"
                    )
                    // Si la ECU no responde en varios ciclos seguidos (todos
                    // los rápidos null) se fuerza reapertura en vez de quedarse
                    // en NO DATA para siempre.
                    if (rpm == null && load == null && throttle == null) {
                        silentCycles++
                        if (silentCycles >= 8) {
                            ObdConsole.info("= La ECU no responde (NO DATA)… reconectando adaptador")
                            Log.w(TAG, "silent streak -> reconnect")
                            closeSocket()
                            throw IOException("ECU silent, reconnect")
                        }
                    } else {
                        silentCycles = 0
                    }
                    // Comandos puntuales pedidos desde la UI (releer fallas, sondas).
                    while (onceQueue.isNotEmpty()) {
                        try {
                            onceQueue.poll()?.invoke()
                        } catch (e: Exception) {
                            Log.w(TAG, "once: ${e.message}")
                        }
                    }
                    Thread.sleep(1000L)
                }
            } catch (e: Exception) {
                Log.w(TAG, "OBD session: ${e.message}")
                fast.clear()
                slow.clear()
                // Una sesión vieja no debe pisar a la nueva ni cerrarle el
                // socket: stop()/connect() ya cerraron lo suyo.
                if (!alive(gen)) break
                val msg = if (e.message?.contains("BLE") == true || e.message?.contains("no-OBD") == true) {
                    e.message ?: "Dispositivo BLE o no-OBD (requiere ELM327 SPP clásico)"
                } else {
                    "Sin respuesta del adaptador"
                }
                ObdRepository.publish(last.copy(connected = false, error = msg))
                closeSocket()
                // Si hay varios ELM327 vinculados (el viejo y el nuevo), el
                // siguiente intento prueba el otro en vez de insistir al que
                // está apagado.
                try {
                    tried.add(device.address)
                } catch (_: Exception) {
                }
                currentMac = null
                connectFailures++
                if (connectFailures >= 3 && !Prefs.obdMac(context).isNullOrBlank()) {
                    // MAC BLE aleatoria o adaptador cambiado: volver a buscar.
                    Prefs.setObdMac(context, null)
                    connectFailures = 0
                }
                Thread.sleep(5000L)
            }
        }
    }

    private fun initialize() {
        supportedPids.clear()
        pidMaskTrusted = false
        sendCommand("ATZ", 2500L)
        Thread.sleep(700L)
        sendCommand("ATE0", 1200L)
        sendCommand("ATL0", 800L)
        // Clones v2.1 may answer '?' to ATS0; never abort on that.
        sendCommand("ATS0", 800L)
        sendCommand("ATH0", 800L)
        // Temporizado adaptativo del clon BLE: sin él, la ECU suele dejar de
        // responder (NO DATA) tras las primeras consultas.
        sendCommand("ATAT2", 800L)

        // Identificar el clon (MINI ELM327 v1.5/2.1, Vgate, etc.) y el
        // protocolo negociado. Es la primera línea que hay que mirar si una
        // lectura no aparece.
        val adapterId = sendCommand("ATI", 900L)
        if (adapterId.isNotBlank()) {
            ObdConsole.info("= Adaptador: ${oneLine(adapterId)}")
            Log.i(TAG, "ATI: ${oneLine(adapterId)}")
        }

        // Try auto-detect first (safest for 2009–2013 Ibiza variants).
        sendCommand("ATSP0", 1500L)
        var supported = sendCommand("0100", 6000L)
        val protocol = sendCommand("ATDPN", 900L)
        if (protocol.isNotBlank()) {
            ObdConsole.info("= Protocolo: ${oneLine(protocol)}")
            Log.i(TAG, "ATDPN: ${oneLine(protocol)}")
        }
        Log.d(TAG, "0100 (ATSP0): ${supported.replace("\n", " ").take(120)}")
        if (!supported.contains("4100") && !supported.contains("41 00")) {
            // Fall back to forced ISO 15765-4 CAN 11-bit 500k.
            sendCommand("ATSP6", 1500L)
            supported = sendCommand("0100", 6000L)
            Log.d(TAG, "0100 (ATSP6): ${supported.replace("\n", " ").take(120)}")
        }
        if (!supported.contains("4100") && !supported.contains("41 00")) {
            // Last resort: let the adapter search again.
            sendCommand("ATSP0", 1500L)
            val retry = sendCommand("0100", 5000L)
            Log.d(TAG, "0100 (retry ATSP0): ${retry.replace("\n", " ").take(120)}")
        }
        // Discover PIDs 0x21–0x40 and 0x41–0x60 for diagnostics.
        val pids21 = sendCommand("0120", 2500L)
        Log.d(TAG, "0120: ${pids21.replace("\n", " ").take(120)}")
        val pids41 = sendCommand("0140", 2500L)
        Log.d(TAG, "0140: ${pids41.replace("\n", " ").take(120)}")

        // Mapa de PIDs soportados: evita pedir datos que la ECU no tiene y
        // esperar el timeout de cada uno en cada ciclo.
        parseSupported(supported, "4100", 0x00)
        if (supportedPids.contains(0x0C)) {
            // 0x0C (RPM) es obligatorio; si no aparece, la respuesta no se
            // entendió y se ignora el mapa para no perder datos.
            pidMaskTrusted = true
            parseSupported(pids21, "4120", 0x20)
            parseSupported(pids41, "4140", 0x40)
            Log.i(
                TAG,
                "PIDs soportados (${supportedPids.size}): " +
                    supportedPids.sorted().joinToString(",") { "%02X".format(it) }
            )
        } else {
            supportedPids.clear()
            Log.w(TAG, "0100 sin PIDs interpretables; se consultan todos")
        }
    }

    /**
     * La máscara 0100/0120/0140 se conserva solo para diagnóstico.
     *
     * En clones ELM327 y en respuestas multi-ECU la máscara llega incompleta:
     * se observó que algunos PIDs desaparecían aunque la ECU sí respondía.
     * Ocultar lecturas por esa máscara es peor que perder unos milisegundos en
     * un PID no soportado, así que se consulta todo.
     */
    private fun supports(pid: Int): Boolean = true

    /** Decodifica una respuesta de máscara de PIDs (4 bytes = 32 PIDs). */
    private fun parseSupported(response: String, prefix: String, basePid: Int) {
        val bytes = payload(response, prefix) ?: return
        for (i in bytes.indices) {
            val value = bytes[i]
            for (bit in 0..7) {
                if ((value shr (7 - bit)) and 1 == 1) {
                    supportedPids.add(basePid + i * 8 + bit + 1)
                }
            }
        }
    }

    private fun resolveDevice(
        mac: String?,
        exclude: Set<String> = emptySet()
    ): BluetoothDevice? {
        val adapter = adapter ?: return null
        if (!adapter.isEnabled) return null

        // 1) MAC elegida por el usuario o guardada por una conexión anterior.
        if (!mac.isNullOrBlank() && mac !in exclude) {
            val device = try {
                adapter.getRemoteDevice(mac)
            } catch (_: Exception) {
                null
            }
            if (device != null) {
                if (device.type != BluetoothDevice.DEVICE_TYPE_LE) {
                    ObdScanner.pair(device, Prefs.obdPin(context))
                }
                return device
            }
        }

        // 2) Vinculados con pinta de ELM327 (sin importar la marca exacta).
        // Se prueban en orden hasta que uno responda: el adaptador viejo pudo
        // quedarse en la lista aunque el usuario ya use uno nuevo.
        val bonded = try {
            adapter.bondedDevices?.toList().orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
        val likelyBonded = bonded
            .map { it to ObdScanner.score(runCatching { it.name }.getOrNull()) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .filterNot { safeAddress(it) in exclude }
        likelyBonded.firstOrNull()?.let { device ->
            if (device.type != BluetoothDevice.DEVICE_TYPE_LE) {
                ObdScanner.pair(device, Prefs.obdPin(context))
            }
            return device
        }

        // 3) Búsqueda activa: clásico + BLE. Un adaptador nuevo no aparece en
        // bondedDevices hasta que alguien lo empareja; antes se quedaba en
        // "Sin adaptador OBD emparejado" para siempre.
        ObdConsole.info("= Sin OBD vinculado; buscando un adaptador nuevo…")
        val found = try {
            ObdScanner.scan(context, 9_000L) { !running }
        } catch (e: Exception) {
            Log.w(TAG, "scan: ${e.message}")
            emptyList()
        }
        val best = found
            .filterNot { it.address in exclude }
            .firstOrNull { it.likelyObd && it.type != BluetoothDevice.DEVICE_TYPE_LE }
            ?: found.filterNot { it.address in exclude }.firstOrNull { it.likelyObd }
            ?: found.filterNot { it.address in exclude }
                .firstOrNull { it.type == BluetoothDevice.DEVICE_TYPE_LE && it.bonded }
        if (best != null) {
            val device = try {
                adapter.getRemoteDevice(best.address)
            } catch (_: Exception) {
                null
            }
            if (device != null) {
                if (device.type != BluetoothDevice.DEVICE_TYPE_LE) {
                    ObdScanner.pair(device, Prefs.obdPin(context))
                }
                Prefs.setObdMac(context, device.address)
                ObdConsole.info("= OBD detectado: ${best.label}")
                return device
            }
        }
        return null
    }

    private fun safeAddress(device: BluetoothDevice): String = try {
        device.address
    } catch (_: Exception) {
        ""
    }

    @SuppressLint("MissingPermission")
    private fun openLink(device: BluetoothDevice): Boolean {
        if (device.type == BluetoothDevice.DEVICE_TYPE_LE) {
            val link = BleElm327Link(context, device)
            if (link.connect()) {
                ble = link
                socket = null
                input = null
                output = null
                return true
            }
            link.close()
            return false
        }
        return try {
            val sock = openClassicSocket(device)
            socket = sock
            input = sock.inputStream
            output = sock.outputStream
            ble?.close()
            ble = null
            true
        } catch (e: Exception) {
            // Muchos clones nuevos se anuncian como BR/EDR pero solo hablan
            // GATT. Se intenta BLE también para DUAL/CLÁSICO antes de rendirse.
            val link = BleElm327Link(context, device)
            if (link.connect()) {
                ble = link
                socket = null
                input = null
                output = null
                return true
            }
            link.close()
            throw e
        }
    }

    @SuppressLint("MissingPermission")
    private fun openClassicSocket(device: BluetoothDevice): BluetoothSocket {
        try {
            adapter?.cancelDiscovery()
        } catch (_: Exception) {
        }
        // 1) Insecure SPP first (preferred on Chinese head units).
        try {
            val sock = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            sock.connect()
            return sock
        } catch (e: Exception) {
            Log.w(TAG, "Insecure SPP failed: ${e.message}; trying secure SPP")
        }
        // 2) Secure SPP.
        try {
            val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
            sock.connect()
            return sock
        } catch (e: Exception) {
            Log.w(TAG, "Secure SPP failed: ${e.message}; trying RFCOMM channels")
        }
        // 3) Raw RFCOMM channels 1..2 via reflection.
        for (channel in intArrayOf(1, 2)) {
            var sock: BluetoothSocket? = null
            try {
                val method = device.javaClass.getMethod(
                    "createRfcommSocket",
                    Int::class.javaPrimitiveType
                )
                sock = method.invoke(device, channel) as BluetoothSocket
                sock.connect()
                Log.i(TAG, "RFCOMM channel $channel connected")
                return sock
            } catch (e: Exception) {
                Log.w(TAG, "RFCOMM channel $channel failed: ${e.message}")
                try {
                    sock?.close()
                } catch (_: Exception) {
                }
            }
        }
        // No se borra la MAC aquí: si es un clon con nombre raro, runReal
        // agota sus reintentos y decide. Borrarla al primer fallo impedía
        // reconectar a un ELM327 nuevo con nombre no estándar.
        if (device.type == BluetoothDevice.DEVICE_TYPE_LE) {
            throw IOException("Dispositivo BLE: se reintentará por GATT")
        }
        throw IOException("OBD SPP/RFCOMM connect failed")
    }

    private fun sendCommand(command: String, timeoutMs: Long): String {
        ble?.let { link ->
            return try {
                ObdConsole.tx(command)
                if (!link.write(command + "\r")) {
                    return ""
                }
                val resp = link.readUntilPrompt(timeoutMs)
                ObdConsole.rx(resp)
                resp
            } catch (e: Exception) {
                Log.w(TAG, "$command (BLE): ${e.message}")
                ""
            }
        }
        val out = output ?: return ""
        return try {
            ObdConsole.tx(command)
            out.write((command + "\r").toByteArray())
            out.flush()
            val resp = readResponse(timeoutMs)
            ObdConsole.rx(resp)
            resp
        } catch (e: Exception) {
            Log.w(TAG, "$command: ${e.message}")
            ""
        }
    }

    private fun readResponse(timeoutMs: Long): String {
        val inp = input ?: return ""
        val sb = StringBuilder()
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        val buffer = ByteArray(256)
        while (running && SystemClock.elapsedRealtime() < deadline) {
            val available = try {
                inp.available()
            } catch (_: Exception) {
                0
            }
            if (available > 0) {
                val read = inp.read(buffer, 0, minOf(available, buffer.size))
                for (i in 0 until read) {
                    val c = buffer[i].toInt().toChar()
                    sb.append(c)
                    if (c == '>') return sb.toString()
                }
            } else {
                Thread.sleep(10L)
            }
        }
        return sb.toString()
    }

    private fun queryValue(
        command: String,
        prefix: String,
        parser: (IntArray) -> Float?
    ): Float? {
        // Consulta en la forma estándar (sin sufijo). En el ELM327 v1.5/BLE
        // del estéreo (68:03…) el sufijo " 1" devolvía NO DATA aunque la ECU
        // sí respondía; se prueba el comando limpio primero.
        var response = sendCommand(command, 1500L)
        if (response.isBlank() || response.contains("?")) {
            response = sendCommand("$command 1", 1500L)
        }
        if (response.contains("NO DATA", ignoreCase = true) ||
            response.contains("UNABLE", ignoreCase = true) ||
            response.contains("ERROR", ignoreCase = true)
        ) {
            Log.d(TAG, "$command no data: ${response.replace("\n", " ").take(80)}")
            return null
        }
        var bytes = payload(response, prefix)
        if (bytes == null) {
            // Eco o respuesta truncada por BLE: se reintenta una vez en vez
            // de dar el ciclo por perdido.
            try {
                Thread.sleep(40L)
            } catch (_: Exception) {
            }
            response = sendCommand(command, 1500L)
            if (response.contains("NO DATA", ignoreCase = true)) {
                Log.d(TAG, "$command no data (retry): ${response.replace("\n", " ").take(80)}")
                return null
            }
            bytes = payload(response, prefix) ?: return null
        }
        return parser(bytes)
    }

    private fun payload(response: String, prefix: String): IntArray? {
        val clean = response.uppercase()
            .replace(" ", "")
            .replace("\r", "")
            .replace("\n", "")
        val index = clean.indexOf(prefix)
        if (index < 0) return null
        var hex = clean.substring(index + prefix.length)
        val end = hex.indexOf('>')
        if (end >= 0) hex = hex.substring(0, end)
        val bytes = ArrayList<Int>()
        var i = 0
        while (i + 1 < hex.length) {
            val pair = hex.substring(i, i + 2)
            val value = pair.toIntOrNull(16) ?: break
            bytes.add(value)
            i += 2
        }
        return if (bytes.isEmpty()) null else bytes.toIntArray()
    }

    private fun queryVin(): String? {
        val response = sendCommand("0902", 1500L)
        val clean = response.uppercase().replace(" ", "").replace("\r", "")
            .replace("\n", "").replace(">", "")
        val index = clean.indexOf("4902")
        if (index < 0) return null
        val hex = clean.substring(index + 4)
        val chars = StringBuilder()
        var i = 0
        while (i + 1 < hex.length) {
            val value = hex.substring(i, i + 2).toIntOrNull(16) ?: break
            if (value in 0x20..0x7E) chars.append(value.toChar())
            i += 2
        }
        val vin = chars.toString().trim()
        return if (vin.length >= 17) vin.take(17) else null
    }

    private fun queryDtcs(): Triple<Boolean?, Int?, List<String>> {
        val response = sendCommand("0101", 1200L)
        val status = payload(response, "4101")
        val mil = status?.getOrNull(0)?.let { (it and 0x80) != 0 }
        val count = status?.getOrNull(0)?.let { it and 0x7F }

        val dtcResponse = sendCommand("03", 1800L)
        val clean = dtcResponse.uppercase().replace(" ", "").replace("\r", "")
            .replace("\n", "").replace(">", "")
        val codes = ArrayList<String>()
        val header = clean.indexOf("43")
        var index = if (header >= 0) header + 4 else -1
        while (index >= 0 && index + 4 <= clean.length) {
            val first = clean.substring(index, index + 2).toIntOrNull(16) ?: break
            val second = clean.substring(index + 2, index + 4).toIntOrNull(16) ?: break
            if (first == 0 && second == 0) break
            val letter = when (first shr 6) {
                0 -> 'P'
                1 -> 'C'
                2 -> 'B'
                else -> 'U'
            }
            val code = ((first and 0x3F) shl 8) or second
            codes.add(String.format("%c%04X", letter, code))
            index += 4
        }
        return Triple(mil, count, codes)
    }

    private fun queryAtrv(): Float? {
        return try {
            val response = sendCommand("ATRV", 800L)
            val match = Regex("(\\d+\\.?\\d*)\\s*V", RegexOption.IGNORE_CASE).find(response)
            match?.groupValues?.get(1)?.toFloatOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun oneLine(text: String): String = text
        .replace("\r", " ")
        .replace("\n", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(100)

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        try {
            ble?.close()
        } catch (_: Exception) {
        }
        socket = null
        input = null
        output = null
        ble = null
    }

    companion object {
        private const val TAG = "ObdElm327"
        private val SPP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /** PIDs rápidos: caducan antes para no mostrar valores viejos. */
        private const val FAST_TTL_MS = 10_000L

        /** PIDs lentos (cada 10 ciclos, con posibles timeouts): más margen. */
        private const val SLOW_TTL_MS = 120_000L

        private const val K_RPM = "rpm"
        private const val K_LOAD = "load"
        private const val K_THROTTLE = "throttle"
        private const val K_COOLANT = "coolant"
        private const val K_INTAKE = "intake"
        private const val K_BATTERY = "battery"
        private const val K_TRIM = "trim"
        private const val K_TIMING = "timing"
    }
}
