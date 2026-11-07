package com.chronosbvrx.ibizadash.car

import android.os.Binder
import android.os.Parcel

/**
 * Callback del MCU para el estado del teléfono/llamadas.
 * Transacciones del AIDL:
 *  - onBtPhoneStatus = 3
 *  - onBtCallStatus  = 4
 *  - onBtConnectedStatus = 5
 */
class BtCallback(
    private val onPhoneStatus: (Int) -> Unit,
    private val onCallStatus: (Int, String?, String?) -> Unit,
    private val onConnectedStatus: (Int) -> Unit
) : Binder() {

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        return when (code) {
            TRANSACTION_PHONE_STATUS -> {
                data.enforceInterface(DESCRIPTOR)
                onPhoneStatus(data.readInt())
                reply?.writeNoException()
                true
            }
            TRANSACTION_CALL_STATUS -> {
                data.enforceInterface(DESCRIPTOR)
                val status = data.readInt()
                val number = data.readString()
                val name = data.readString()
                onCallStatus(status, number, name)
                reply?.writeNoException()
                true
            }
            TRANSACTION_CONNECTED_STATUS -> {
                data.enforceInterface(DESCRIPTOR)
                onConnectedStatus(data.readInt())
                reply?.writeNoException()
                true
            }
            in 1..8 -> {
                data.enforceInterface(DESCRIPTOR)
                reply?.writeNoException()
                true
            }
            else -> super.onTransact(code, data, reply, flags)
        }
    }

    companion object {
        const val DESCRIPTOR = "com.tw.service.xt.aidl.ITWCommandCallbackAidl"
        private const val TRANSACTION_PHONE_STATUS = 3
        private const val TRANSACTION_CALL_STATUS = 4
        private const val TRANSACTION_CONNECTED_STATUS = 5
    }
}
