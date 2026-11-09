package com.chronosbvrx.ibizadash.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.SystemClock
import com.chronosbvrx.ibizadash.Prefs
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Minimal ELM327 Bluetooth OBD-II client.
 *
 * Runs on its own thread, speaks the standard AT/PID protocol and publishes
 * [ObdTelemetry]. Reads RPM, coolant, engine load, throttle, intake temp,
 * speed, fuel level, battery voltage, MIL/DTC and VIN.
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
    private var lastBleScanMs = 0L
    private var connectFailures = 0

    @Volatile
    private var running = false

    @Volatile
    private var generation = 0

    private val onceQueue = java.util.concurrent.ConcurrentLinkedQueue<() -> Unit>()

    /** Last UDS fuel read; fuel moves slowly, one read every 30 s is enough. */
    @Volatile
    private var lastFuelUdsMs = 0L
    @Volatile
    private var lastFuelOkMs = 0L

    /** PIDs que la ECU declaró soportar en 0100/0120/0140. */
    private val supportedPids = java.util.Collections.synchronizedSet(HashSet<Int>())

    /**
     * El mapa de PIDs solo se usa si se pudo leer y contiene RPM (0x0C), que es
     * obligatorio en OBD-II. Si el clon responde algo raro, se ignora y se
     * consulta todo como antes (fail-open).
     */
    @Volatile
    private var pidMaskTrusted = false

    private data class FuelReading(val raw: Int)

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

    /**
     * Sonda de SOLO LECTURA del PID 012F (nivel de gasolina).
     * Nunca borra ni configura nada; el resultado se explica en la consola.
     */
    fun probeFuel() {
        if (!isLive()) {
            ObdConsole.info("= Sin conexión con el adaptador. Enciende el contacto y verifica el OBDII.")
            return
        }
        onceQueue.add {
            if (ObdRepository.current.demo) {
                ObdConsole.info("= Modo demo: conecta el adaptador real para probar 012F")
                return@add
            }
            val resp = sendCommand("012F", 2500L)
            val bytes = payload(resp, "412F")
            if (bytes != null && bytes.isNotEmpty()) {
                val pct = bytes[0] * 100f / 255f
                ObdRepository.publish(ObdRepository.current.copy(fuelPct = pct))
                ObdConsole.info("= ¡NIVEL REAL! ${pct.roundToInt()} % — tu ECU sí entrega 012F")
            } else {
                ObdConsole.info("= 012F → SIN DATOS. Tu ECU no entrega nivel de gasolina.")
                ObdConsole.info("= El flotador vive en el cuadro (dirección 17); el modo OBD genérico no lo alcanza. Solo VCDS/OBDeleven lo leen.")
            }
        }
    }

    /**
     * Lee el nivel real de combustible desde el **cuadro de instrumentos**
     * (UDS, CAN 0x714 → 0x77E, DID 0x2206). El motor no expone el nivel por
     * OBD-II PID 2F en este coche; el cuadro sí lo tiene.
     *
     * Es de solo lectura. Al terminar restaura el adaptador a OBD-II normal.
     */
    fun probeFuelUds() {
        if (!isLive()) {
            ObdConsole.info("= Sin conexión con el adaptador. Enciende el contacto y verifica el OBDII.")
            return
        }
        onceQueue.add {
            if (ObdRepository.current.demo) {
                ObdConsole.info("= Modo demo: desactívalo para leer el nivel real del cuadro")
                return@add
            }
            val did = Prefs.fuelUdsDid(context)
            ObdConsole.info("= Leyendo nivel UDS DID 0x%04X en el cuadro (0x714)…".format(did))
            val reading = readFuelUds(log = true)
            if (reading != null) {
                publishFuel(reading.raw, "UDS 0x%04X".format(did))
            } else {
                ObdConsole.info("= UDS 0x%04X sin respuesta. Escaneando 0x2200–0x2210…".format(did))
                scanFuelDids()
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
            val speed = (12 + 10 * sin(t / 5)).toInt().coerceAtLeast(0)
            val fuel = (62 - t / 120).toFloat().coerceIn(0f, 100f)
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
                    speedKmh = speed,
                    fuelPct = fuel,
                    fuelLevelPct = fuel,
                    fuelLevelLiters = fuel * 45f / 100f,
                    fuelRaw = (fuel * 0.9f).toInt(),
                    fuelSource = "DEMO",
                    batteryV = battery,
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
        val fast = FreshReading()
        val slow = FreshReading()
        while (alive(gen)) {
            val device = resolveDevice(mac)
            if (device == null) {
                ObdRepository.publish(last.copy(connected = false, error = "Sin adaptador OBD emparejado"))
                Thread.sleep(4000L)
                continue
            }
            try {
                if (!openLink(device)) throw IOException("OBD link failed")
                connectFailures = 0
                last = last.copy(connected = true, demo = false, deviceName = device.name, error = null)

                initialize()
                fast.clear()
                slow.clear()
                val vin = queryVin()
                val (mil, dtcCount, dtcs) = queryDtcs()
                last = last.copy(vin = vin, milOn = mil, dtcCount = dtcCount, dtcs = dtcs)
                ObdRepository.publish(last)

                var slowCounter = 0
                while (alive(gen)) {
                    val now = SystemClock.elapsedRealtime()
                    val rpm = if (supports(0x0C)) queryValue("010C", "410C") { bytes ->
                        if (bytes.size >= 2) {
                            (((bytes[0] * 256) + bytes[1]) / 4).toFloat()
                        } else {
                            null
                        }
                    } else null
                    val load = if (supports(0x04)) queryValue("0104", "4104") { bytes ->
                        if (bytes.isNotEmpty()) bytes[0] * 100f / 255f else null
                    } else null
                    val throttle = if (supports(0x11)) queryValue("0111", "4111") { bytes ->
                        if (bytes.isNotEmpty()) bytes[0] * 100f / 255f else null
                    } else null
                    val speed = if (supports(0x0D)) queryValue("010D", "410D") { bytes ->
                        if (bytes.isNotEmpty()) bytes[0].toFloat() else null
                    } else null

                    fast.put(K_RPM, rpm, now)
                    fast.put(K_LOAD, load, now)
                    fast.put(K_THROTTLE, throttle, now)
                    fast.put(K_SPEED, speed, now)

                    if (++slowCounter % 2 == 0) {
                        val coolant = if (supports(0x05)) queryValue("0105", "4105") { bytes ->
                            if (bytes.isNotEmpty()) (bytes[0] - 40).toFloat() else null
                        } else null
                        val intake = if (supports(0x0F)) queryValue("010F", "410F") { bytes ->
                            if (bytes.isNotEmpty()) (bytes[0] - 40).toFloat() else null
                        } else null
                        val fuel = if (supports(0x2F)) queryValue("012F", "412F") { bytes ->
                            if (bytes.isNotEmpty()) bytes[0] * 100f / 255f else null
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
                        slow.put(K_FUEL, fuel, now)
                        slow.put(K_BATTERY, battery, now)
                        slow.put(K_TRIM, fuelTrim, now)
                        slow.put(K_TIMING, timing, now)
                        // Nivel real del cuadro por UDS (cada 60 s; cambia lento).
                        val nowFuel = SystemClock.elapsedRealtime()
                        if (Prefs.fuelUdsEnabled(context) && nowFuel - lastFuelUdsMs > 60_000L) {
                            lastFuelUdsMs = nowFuel
                            val reading = readFuelUds(log = false)
                            val pct = reading?.let { fuelPctFromRaw(it.raw) }
                            if (reading != null && pct != null) {
                                lastFuelOkMs = nowFuel
                                last = last.copy(
                                    fuelRaw = reading.raw,
                                    fuelLevelPct = pct,
                                    fuelLevelLiters = pct * Prefs.fuelTankLiters(context) / 100f,
                                    fuelSource = "UDS"
                                )
                            } else if (last.fuelSource == "UDS" &&
                                nowFuel - lastFuelOkMs > FUEL_UDS_TTL_MS
                            ) {
                                // Sin respuesta durante demasiado tiempo: deja de
                                // mostrar un nivel viejo.
                                last = last.copy(
                                    fuelRaw = null,
                                    fuelLevelPct = null,
                                    fuelLevelLiters = null,
                                    fuelSource = null
                                )
                            }
                        }
                    }

                    // Un valor solo sigue visible mientras esté vigente: si la
                    // ECU deja de contestar, el campo pasa a "sin lectura" y el
                    // velocímetro puede caer a CAN o GPS.
                    last = last.copy(
                        connected = true,
                        rpm = fast.get(K_RPM, now, FAST_TTL_MS)?.toInt(),
                        loadPct = fast.get(K_LOAD, now, FAST_TTL_MS),
                        throttlePct = fast.get(K_THROTTLE, now, FAST_TTL_MS),
                        speedKmh = fast.get(K_SPEED, now, FAST_TTL_MS)?.toInt(),
                        coolantC = slow.get(K_COOLANT, now, SLOW_TTL_MS),
                        intakeC = slow.get(K_INTAKE, now, SLOW_TTL_MS),
                        fuelPct = slow.get(K_FUEL, now, SLOW_TTL_MS),
                        fuelTrimPct = slow.get(K_TRIM, now, SLOW_TTL_MS),
                        timingAdvanceDeg = slow.get(K_TIMING, now, SLOW_TTL_MS),
                        batteryV = slow.get(K_BATTERY, now, SLOW_TTL_MS),
                        updatedAtMs = now
                    )
                    ObdRepository.publish(last)
                    Log.d(
                        TAG,
                        "telemetry rpm=${last.rpm} coolant=${last.coolantC} speed=${last.speedKmh} " +
                            "load=${last.loadPct} throttle=${last.throttlePct} fuel=${last.fuelPct} " +
                            "trim=${last.fuelTrimPct} timing=${last.timingAdvanceDeg} bat=${last.batteryV}"
                    )
                    // Comandos puntuales pedidos desde la UI (releer fallas, sondas).
                    while (onceQueue.isNotEmpty()) {
                        try {
                            onceQueue.poll()?.invoke()
                        } catch (e: Exception) {
                            Log.w(TAG, "once: ${e.message}")
                        }
                    }
                    Thread.sleep(350L)
                }
            } catch (e: Exception) {
                Log.w(TAG, "OBD session: ${e.message}")
                fast.clear()
                slow.clear()
                val msg = if (e.message?.contains("BLE") == true || e.message?.contains("no-OBD") == true) {
                    e.message ?: "Dispositivo BLE o no-OBD (requiere ELM327 SPP clásico)"
                } else {
                    "Sin respuesta del adaptador"
                }
                ObdRepository.publish(last.copy(connected = false, error = msg))
                closeSocket()
                connectFailures++
                if (connectFailures >= 3 && !Prefs.obdMac(context).isNullOrBlank()) {
                    // MAC BLE aleatoria o adaptador cambiado: volver a buscar.
                    Prefs.setObdMac(context, null)
                    connectFailures = 0
                }
                if (alive(gen)) Thread.sleep(5000L)
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
        // Discover PIDs 0x21–0x40 (includes fuel level 0x2F) and 0x41–0x60.
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
     * se observó que 010D (velocidad) y 012F (combustible) desaparecían aunque
     * la ECU sí respondía. Ocultar lecturas por esa máscara es peor que perder
     * unos milisegundos en un PID no soportado, así que se consulta todo.
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

    private fun resolveDevice(mac: String?): BluetoothDevice? {
        val adapter = adapter ?: return null
        if (!adapter.isEnabled) return null
        if (!mac.isNullOrBlank()) {
            return try {
                adapter.getRemoteDevice(mac)
            } catch (_: Exception) {
                null
            }
        }
        return try {
            val candidates = adapter.bondedDevices?.filter { device ->
                val name = device.name?.lowercase().orEmpty()
                name.contains("obd") || name.contains("elm") || name.contains("vgate") ||
                    name.contains("v-link") || name.contains("superbox") || name.contains("327") ||
                    name.contains("b16") || name.contains("mini")
            }.orEmpty()
            val match = candidates.firstOrNull { it.name?.lowercase()?.contains("obdii") == true }
                ?: candidates.firstOrNull()
            if (match != null) return match

            // BLE (B16/Vgate): no aparece en bondedDevices; escanear con throttle.
            val now = SystemClock.elapsedRealtime()
            if (now - lastBleScanMs > 20_000L) {
                lastBleScanMs = now
                scanBle()?.let { device ->
                    Prefs.setObdMac(context, device.address)
                    return device
                }
            }
            null
        } catch (_: SecurityException) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanBle(): BluetoothDevice? {
        val scanner = try {
            adapter?.bluetoothLeScanner
        } catch (_: Exception) {
            null
        } ?: return null
        val latch = java.util.concurrent.CountDownLatch(1)
        var found: BluetoothDevice? = null
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val device = result?.device ?: return
                val name = (result.scanRecord?.deviceName ?: device.name ?: "").lowercase()
                val uuids = result.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
                val elmService = uuids.any {
                    it == java.util.UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb") ||
                        it == java.util.UUID.fromString("000018f0-0000-1000-8000-00805f9b34fb")
                }
                if (elmService || name.contains("obd") || name.contains("elm") || name.contains("b16") ||
                    name.contains("vgate") || name.contains("v-link") || name.contains("327")
                ) {
                    found = device
                    try {
                        scanner.stopScan(this)
                    } catch (_: Exception) {
                    }
                    latch.countDown()
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "BLE scan failed: $errorCode")
                latch.countDown()
            }
        }
        return try {
            scanner.startScan(callback)
            latch.await(7, java.util.concurrent.TimeUnit.SECONDS)
            try {
                scanner.stopScan(callback)
            } catch (_: Exception) {
            }
            found
        } catch (e: Exception) {
            Log.w(TAG, "scanBle: ${e.message}")
            null
        }
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
            if (device.type == BluetoothDevice.DEVICE_TYPE_DUAL) {
                val link = BleElm327Link(context, device)
                if (link.connect()) {
                    ble = link
                    socket = null
                    input = null
                    output = null
                    return true
                }
                link.close()
            }
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
        // Purge invalid device (BLE or non-OBD name) so auto-scan can recover.
        val name = device.name?.lowercase().orEmpty()
        val keywords = listOf("obd", "elm", "vgate", "v-link", "superbox", "327", "obdii", "b16", "mini")
        if (device.type == BluetoothDevice.DEVICE_TYPE_LE || keywords.none { name.contains(it) }) {
            Prefs.setObdMac(context, null)
        }
        if (device.type == BluetoothDevice.DEVICE_TYPE_LE) {
            throw IOException("Dispositivo BLE o no-OBD (requiere ELM327 SPP clásico)")
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
        // Accelerate CAN responses on v2.1 clones by requesting a single frame.
        var response = sendCommand("$command 1", 1500L)
        if (response.isBlank() || response.contains("?")) {
            response = sendCommand(command, 1500L)
        }
        if (response.contains("NO DATA", ignoreCase = true) ||
            response.contains("UNABLE", ignoreCase = true) ||
            response.contains("ERROR", ignoreCase = true)
        ) {
            Log.d(TAG, "$command no data: ${response.replace("\n", " ").take(80)}")
            return null
        }
        val bytes = payload(response, prefix) ?: return null
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

    // ------------------------------------------------------------------
    // Nivel de combustible real por UDS (cuadro de instrumentos, 0x714)
    // ------------------------------------------------------------------

    /**
     * Petición UDS de solo lectura al cuadro (0x714 → 0x77E):
     * `22 2206` devuelve el nivel de combustible en **litros** (raw directo,
     * p. ej. 2C = 44 L). El cuadro del Ibiza 6J es UDS VDO
     * (`EV_Kombi_UDS_VDD_RM09`); el motor no entrega PID 2F.
     *
     * La secuencia raw (`ATCAF0` + `03 22 22 06 55 55 55 55` + flow control)
     * es la que funciona en estos clones; dejar que el ELM327 auto-formatee
     * la petición suele devolver NO DATA.
     */
    private fun readFuelUds(log: Boolean): FuelReading? {
        if (!isLive()) return null
        val did = Prefs.fuelUdsDid(context)
        val req = "22 %04X".format(did)
        return try {
            sendCommand("ATSP6", 700L)
            sendCommand("ATSH714", 500L)
            val cra = sendCommand("ATCRA77E", 650L)
            if (cra.contains("?")) {
                // Clones sin ATCRA: filtro/máscara manual para 0x77E.
                sendCommand("ATCF77E", 450L)
                sendCommand("ATCM7FF", 450L)
            }
            // Secuencia UDS VAG probada: frame ISO-TP completo con PCI 0x03,
            // padding 0x55 y flow control explícito hacia 0x714.
            sendCommand("ATFCSH714", 450L)
            sendCommand("ATFCSD300000", 450L)
            sendCommand("ATFCSM1", 450L)
            sendCommand("ATAL", 450L)
            val caf = sendCommand("ATCAF0", 450L)
            // Un MINI ELM327 v1.5 que no soporte ATCAF0 devuelve "?".
            // En ese caso el frame raw no se puede enviar: usar auto-formato.
            if (caf.isNotBlank() && !caf.contains("?")) {
                val rawFrame = "03 22 %04X 55 55 55 55".format(did)
                val rawResp = sendCommand(rawFrame, 1800L)
                if (log) ObdConsole.info("= UDS raw $rawFrame → ${oneLine(rawResp)}")
                val raw2 = parseUdsFuel(rawResp, did)
                if (raw2 != null) return FuelReading(raw2)
            } else if (log) {
                ObdConsole.info("= Este clon no soporta ATCAF0; probando auto-formato")
            }

            // Fallback: petición auto-formateada y sesión extendida. Algunos
            // clones no aceptan ATCAF0 pero sí el formato normal.
            sendCommand("ATCAF1", 400L)
            var resp = sendCommand(req, 1800L)
            if (log) ObdConsole.info("= UDS $req → ${oneLine(resp)}")
            if (isNegative(resp)) {
                val session = sendCommand("1003", 1200L)
                if (log) ObdConsole.info("= UDS 1003 → ${oneLine(session)}")
                resp = sendCommand(req, 1800L)
                if (log) ObdConsole.info("= UDS $req (ext) → ${oneLine(resp)}")
            }
            val raw = parseUdsFuel(resp, did)
            if (raw == null && log) {
                ObdConsole.info("= Sin nivel válido. Respuesta: ${oneLine(resp)}")
            }
            raw?.let { FuelReading(it) }
        } catch (e: Exception) {
            Log.w(TAG, "UDS fuel: ${e.message}")
            null
        } finally {
            restoreObd()
        }
    }

    /** Diagnóstico: prueba DIDs 0x2200–0x2210 y deja ver cuáles responden. */
    private fun scanFuelDids() {
        if (!isLive()) return
        try {
            sendCommand("ATSP6", 700L)
            sendCommand("ATSH714", 500L)
            val cra = sendCommand("ATCRA77E", 650L)
            if (cra.contains("?")) {
                sendCommand("ATCF77E", 450L)
                sendCommand("ATCM7FF", 450L)
            }
            sendCommand("1003", 1200L)
            for (did in 0x2200..0x2210) {
                val resp = sendCommand("22 %04X".format(did), 900L)
                val raw = parseUdsFuel(resp, did)
                if (raw != null) {
                    ObdConsole.info("= DID 0x%04X → raw=$raw (%s)".format(did, oneLine(resp)))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "scan DIDs: ${e.message}")
        } finally {
            restoreObd()
        }
    }

    private fun publishFuel(raw: Int, source: String) {
        val pct = fuelPctFromRaw(raw) ?: return
        val liters = pct * Prefs.fuelTankLiters(context) / 100f
        ObdRepository.publish(
            ObdRepository.current.copy(
                fuelRaw = raw,
                fuelLevelPct = pct,
                fuelLevelLiters = liters,
                fuelSource = source
            )
        )
        ObdConsole.info("= Nivel real: raw=$raw → %.1f L (%.0f %%) · $source".format(liters, pct))
    }

    /**
     * DID 0x2206 devuelve el nivel en **litros** en los cuadros VAG UDS
     * (Octavia/Yeti/Ibiza); el Touareg NF usa medias unidades, por eso la
     * calibración de tanque lleno/vacío es ajustable.
     */
    private fun fuelPctFromRaw(raw: Int): Float? {
        if (raw < 0 || raw > 255) return null
        val full = Prefs.fuelFullRaw(context)
        val empty = Prefs.fuelEmptyRaw(context)
        val span = full - empty
        if (span <= 0) return null
        return ((raw - empty) * 100f / span).coerceIn(0f, 100f)
    }

    /** Busca `62 <DID>` en la respuesta y devuelve el primer byte de dato. */
    private fun parseUdsFuel(response: String, did: Int): Int? {
        val clean = response.uppercase()
            .replace(" ", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace(">", "")
        if (clean.contains("7F22")) return null
        val prefix = "62%04X".format(did)
        val index = clean.indexOf(prefix)
        if (index < 0) return null
        val rest = clean.substring(index + prefix.length)
        if (rest.length < 2) return null
        return rest.substring(0, 2).toIntOrNull(16)
    }

    private fun isNegative(response: String): Boolean {
        val clean = response.uppercase().replace(" ", "").replace("\r", "").replace("\n", "")
        return clean.contains("7F22")
    }

    private fun restoreObd() {
        sendCommand("ATFCSM0", 350L)
        sendCommand("ATCAF1", 350L)
        val reset = sendCommand("ATCRA7E8", 450L)
        if (reset.contains("?")) {
            sendCommand("ATAR", 350L)
        }
        sendCommand("ATSH7DF", 400L)
        sendCommand("ATSP6", 500L)
        sendCommand("ATH0", 300L)
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

        /** PIDs lentos (cada 2 ciclos, con posibles timeouts): más margen. */
        private const val SLOW_TTL_MS = 120_000L

        /** Nivel UDS del cuadro: se relee cada 60 s; 3 min sin respuesta lo borra. */
        private const val FUEL_UDS_TTL_MS = 180_000L

        private const val K_RPM = "rpm"
        private const val K_LOAD = "load"
        private const val K_THROTTLE = "throttle"
        private const val K_SPEED = "speed"
        private const val K_COOLANT = "coolant"
        private const val K_INTAKE = "intake"
        private const val K_FUEL = "fuel"
        private const val K_BATTERY = "battery"
        private const val K_TRIM = "trim"
        private const val K_TIMING = "timing"
    }
}
