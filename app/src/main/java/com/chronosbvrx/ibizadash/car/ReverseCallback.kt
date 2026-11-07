package com.chronosbvrx.ibizadash.car

import android.os.Binder
import android.os.Parcel

/**
 * Callback que el servicio MCU usa para avisar del estado de reversa.
 * Solo procesamos onReverseStatus (transacción 6); el resto se ignora.
 */
class ReverseCallback(
    private val onReverse: (Boolean) -> Unit
) : Binder() {

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        return when (code) {
            TRANSACTION_ON_REVERSE -> {
                data.enforceInterface(DESCRIPTOR)
                onReverse(data.readInt() == 1)
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
        private const val TRANSACTION_ON_REVERSE = 6
    }
}
