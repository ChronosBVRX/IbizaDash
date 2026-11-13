package com.chronosbvrx.ibizadash.car

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log

class TwCommandClient(
    private val context: Context,
    private val onReverse: (Boolean) -> Unit,
    private val onPhoneStatus: (Int) -> Unit = {},
    private val onCallStatus: (Int, String?, String?) -> Unit = { _, _, _ -> },
    private val onConnectedStatus: (Int) -> Unit = {},
    private val onVehicleBundle: (Bundle) -> Unit = {},
    private val onCarInfo: (Bundle) -> Unit = {}
) {
    private var commandBinder: TwCommandBinder? = null
    private var reverseCallback: ReverseCallback? = null
    private var btCallback: BtCallback? = null
    private var vehicleCallback: VehicleCallback? = null
    private var bound = false
    private var started = false
    private var receiverRegistered = false
    private var pendingAux = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var reconnectAttempt = 0

    private val carReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val extras = intent.extras ?: return
            val dump = extras.keySet().joinToString(" | ") { key ->
                "$key=${extras.get(key)}"
            }
            Log.i("VehicleData", "broadcast ${intent.action} extras=[$dump]")
            onCarInfo(extras)
        }
    }

    private val deathRecipient = android.os.IBinder.DeathRecipient {
        scheduleReconnect()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null) return
            reconnectAttempt = 0
            try {
                binder.linkToDeath(deathRecipient, 0)
            } catch (_: Exception) {
            }
            try {
                val command = TwCommandBinder(binder)
                val reverse = ReverseCallback(onReverse)
                val bt = BtCallback(onPhoneStatus, onCallStatus, onConnectedStatus)
                val vehicle = VehicleCallback { bundle -> onVehicleBundle(bundle) }
                commandBinder = command
                reverseCallback = reverse
                btCallback = bt
                vehicleCallback = vehicle
                command.register(reverse)
                command.register(bt)
                command.register(vehicle)
                command.getReverseStatus()
                command.getBtPhoneStatus()
                command.requestSource()
                if (pendingAux) {
                    openAux()
                    pendingAux = false
                }
                Log.i("TwCommandClient", "conectado a com.tw.service.xt")
            } catch (e: Exception) {
                Log.w("TwCommandClient", "error al conectar: ${e.message}")
                scheduleReconnect()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            commandBinder = null
            reverseCallback = null
            btCallback = null
            vehicleCallback = null
            scheduleReconnect()
        }

        override fun onBindingDied(name: ComponentName?) {
            commandBinder = null
            reverseCallback = null
            btCallback = null
            vehicleCallback = null
            scheduleReconnect()
        }

        override fun onNullBinding(name: ComponentName?) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (!started) return
        val delay = when (reconnectAttempt) {
            0 -> 1000L
            1 -> 2000L
            2 -> 5000L
            else -> 10000L
        }
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(3)
        handler.postDelayed({
            if (!started) return@postDelayed
            try {
                if (bound) context.unbindService(connection)
            } catch (_: Exception) {
            }
            bound = false
            commandBinder = null
            reverseCallback = null
            btCallback = null
            vehicleCallback = null
            val intent = android.content.Intent("com.tw.service.xt.CommandService.Bind")
                .setPackage("com.tw.service.xt")
            try {
                bound = context.bindService(intent, connection, android.content.Context.BIND_AUTO_CREATE)
                Log.i("TwCommandClient", "rebind=$bound")
            } catch (e: Exception) {
                bound = false
                Log.w("TwCommandClient", "rebind failed: ${e.message}")
            }
        }, delay)
    }

    fun start() {
        if (started) return
        started = true
        val intent = Intent("com.tw.service.xt.CommandService.Bind")
            .setPackage("com.tw.service.xt")
        try {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            Log.i("TwCommandClient", "bindService=$bound")
        } catch (e: Exception) {
            bound = false
            Log.w("TwCommandClient", "bindService falló: ${e.message}")
        }

        if (!receiverRegistered) {
            try {
                context.registerReceiver(carReceiver, IntentFilter("car_info_recieve"))
                receiverRegistered = true
                context.sendBroadcast(Intent("car_info_request"))
                Log.i("TwCommandClient", "car_info receiver registrado")
            } catch (e: Exception) {
                Log.w("TwCommandClient", "registerReceiver: ${e.message}")
            }
        }
    }

    fun btNext(): Boolean = commandBinder?.let { it.btNext(); true } ?: false

    fun btOpenMusic(): Boolean = commandBinder?.let { it.btOpenMusic(); true } ?: false

    /** Fuente AUX del MCU (la única entrada de música que usa el launcher). */
    fun openAux(): Boolean {
        val applied = commandBinder?.let { it.openAux(); true } ?: false
        if (!applied) pendingAux = true
        return applied
    }

    /** Sincroniza la fuente real del MCU con la UI. */
    fun requestSource(): Boolean = commandBinder?.let { it.requestSource(); true } ?: false

    fun radioOpenChannel(name: String): Boolean = commandBinder?.let { it.radioOpenChannel(name); true } ?: false

    fun btPrev(): Boolean = commandBinder?.let { it.btPrev(); true } ?: false

    fun btPlay(): Boolean = commandBinder?.let { it.btPlayMusic(); true } ?: false

    fun btPause(): Boolean = commandBinder?.let { it.btPlayMusic(); true } ?: false

    fun btAccept(): Boolean = commandBinder?.let { it.btAccept(); true } ?: false

    fun btHangUP(): Boolean = commandBinder?.let { it.btHangUP(); true } ?: false

    fun radioNext(): Boolean = commandBinder?.let { it.radioNext(); true } ?: false

    fun radioPre(): Boolean = commandBinder?.let { it.radioPre(); true } ?: false

    fun radioSearchNext(): Boolean = commandBinder?.let { it.radioSearchNext(); true } ?: false

    fun radioSearchPre(): Boolean = commandBinder?.let { it.radioSearchPre(); true } ?: false

    fun radioSetChannel(khz: Int): Boolean = commandBinder?.let { it.radioSetChannel(khz); true } ?: false

    fun radioBand(band: Int): Boolean = commandBinder?.let { it.radioBand(band); true } ?: false

    fun radioBandChange(): Boolean = commandBinder?.let { it.radioBandChange(); true } ?: false

    fun requestExtended(): Boolean = commandBinder?.let { it.extendedInterface(android.os.Bundle()); true } ?: false

    fun stop() {
        if (!started) return
        started = false
        try {
            reverseCallback?.let { commandBinder?.unregister(it) }
            btCallback?.let { commandBinder?.unregister(it) }
            vehicleCallback?.let { commandBinder?.unregister(it) }
        } catch (e: Exception) {
            Log.w("TwCommandClient", "unregister: ${e.message}")
        }
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(carReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
        if (bound) {
            try {
                context.unbindService(connection)
            } catch (e: Exception) {
                Log.w("TwCommandClient", "unbind: ${e.message}")
            }
        }
        bound = false
        commandBinder = null
        reverseCallback = null
        btCallback = null
        vehicleCallback = null
    }
}
