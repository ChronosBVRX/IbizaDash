package com.chronosbvrx.ibizadash.car

import android.os.Binder
import android.os.Bundle
import android.os.Parcel
import android.util.Log

/**
 * ITWCommandCallbackAidl implementation for the MCU extended channel.
 *
 * The service pushes raw vehicle frames through onExtendedInterface (tx 8):
 *   Bundle { type: Int, data: ByteArray? }
 * Types 2560..2563 carry the CAN frames; 513 a two-byte status.
 * We log every frame so the byte-to-field mapping can be validated against
 * physical actions (doors, lights, RPM) on the real car.
 */
class VehicleCallback(
    private val onExtended: (Bundle) -> Unit
) : Binder() {

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        try {
            when (code) {
                1, 2, 3, 5, 6, 7 -> {
                    data.enforceInterface(DESCRIPTOR)
                    val value = if (data.dataAvail() >= 4) data.readInt() else 0
                    Log.d(TAG, "callback code=$code value=$value")
                    reply?.writeNoException()
                    return true
                }
                4 -> {
                    data.enforceInterface(DESCRIPTOR)
                    val value = data.readInt()
                    val number = data.readString()
                    val name = data.readString()
                    Log.d(TAG, "callback code=4 value=$value number=$number name=$name")
                    reply?.writeNoException()
                    return true
                }
                8 -> {
                    data.enforceInterface(DESCRIPTOR)
                    val present = data.readInt()
                    val bundle = if (present != 0) {
                        Bundle.CREATOR.createFromParcel(data)
                    } else {
                        null
                    }
                    if (bundle != null) {
                        val type = bundle.getInt("type", -1)
                        val bytes = bundle.getByteArray("data")
                        val keys = bundle.keySet().joinToString(" | ") { key ->
                            "$key=${bundle.get(key)}"
                        }
                        Log.i(
                            TAG,
                            "extended type=$type len=${bytes?.size ?: 0} " +
                                "data=${bytes?.joinToString("") { "%02X".format(it) } ?: "-"} " +
                                "keys=[$keys]"
                        )
                        onExtended(bundle)
                    }
                    reply?.writeNoException()
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "onTransact $code: ${e.message}")
        }
        return super.onTransact(code, data, reply, flags)
    }

    companion object {
        const val DESCRIPTOR = "com.tw.service.xt.aidl.ITWCommandCallbackAidl"
        private const val TAG = "VehicleData"
    }
}
