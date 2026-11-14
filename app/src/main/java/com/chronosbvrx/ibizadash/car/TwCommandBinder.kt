package com.chronosbvrx.ibizadash.car

import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * Cliente mínimo del servicio MCU `com.tw.service.xt`.
 *
 * Descriptor y transacciones extraídas del APK original
 * (`decompiled/service_xt/.../ITWCommandAidl.java`).
 */
class TwCommandBinder(private val binder: IBinder) {

    fun register(callback: IBinder) = transact(TRANSACTION_REGISTER) { it.writeStrongBinder(callback) }

    fun unregister(callback: IBinder) = transact(TRANSACTION_UNREGISTER) { it.writeStrongBinder(callback) }

    fun getReverseStatus() = transact(TRANSACTION_GET_REVERSE)

    fun getBtPhoneStatus() = transact(TRANSACTION_BT_PHONE_STATUS)

    fun btOpenMusic() = transact(TRANSACTION_BT_OPEN_MUSIC)

    fun btPlayMusic() = transact(TRANSACTION_BT_PLAY_MUSIC)

    fun btPrev() = transact(TRANSACTION_BT_PRE)

    fun btNext() = transact(TRANSACTION_BT_NEXT)

    fun btAccept() = transact(TRANSACTION_BT_ACCEPT)

    fun btHangUP() = transact(TRANSACTION_BT_HANGUP)

    fun musicNext() = transact(TRANSACTION_MUSIC_NEXT)

    fun musicPre() = transact(TRANSACTION_MUSIC_PRE)

    fun musicPlay() = transact(TRANSACTION_MUSIC_PLAY)

    fun musicPause() = transact(TRANSACTION_MUSIC_PAUSE)

    fun radioNext() = transact(TRANSACTION_RADIO_NEXT)

    fun radioPre() = transact(TRANSACTION_RADIO_PRE)

    fun radioSearchNext() = transact(TRANSACTION_RADIO_SEARCH_NEXT)

    fun radioSearchPre() = transact(TRANSACTION_RADIO_SEARCH_PRE)

    fun radioSetChannel(khz: Int) = transact(TRANSACTION_RADIO_SET_CHANNEL) { it.writeInt(khz) }

    fun radioOpenChannel(name: String) = transact(TRANSACTION_RADIO_OPEN_CHANNEL) { it.writeString(name) }

    fun radioBand(band: Int) = transact(TRANSACTION_RADIO_BAND) { it.writeInt(band) }

    fun radioBandChange() = transact(TRANSACTION_RADIO_BAND_CHANGE)

    fun volumeUp() = transact(TRANSACTION_VOLUME_UP)

    fun volumeDown() = transact(TRANSACTION_VOLUME_DOWN)

    fun volumeMute() = transact(TRANSACTION_VOLUME_MUTE)

    fun goHome() = transact(TRANSACTION_GO_HOME)

    fun back() = transact(TRANSACTION_BACK)

    fun extendedInterface(bundle: android.os.Bundle) = transact(TRANSACTION_EXTENDED_INTERFACE) {
        it.writeInt(1)
        bundle.writeToParcel(it, 0)
    }

    /** Pide al MCU la fuente actual; responde por `Source_recieve`. */
    fun requestSource() = extendedInterface(android.os.Bundle().apply {
        putString("project", "system")
        putString("action", "source_request")
    })

    private fun transact(code: Int, writer: (android.os.Parcel) -> Unit = {}) {
        val data = android.os.Parcel.obtain()
        val reply = android.os.Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            writer(data)
            binder.transact(code, data, reply, 0)
            reply.readException()
        } catch (e: Exception) {
            Log.w("TwCommandBinder", "transact $code: ${e.message}")
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    companion object {
        const val DESCRIPTOR = "com.tw.service.xt.aidl.ITWCommandAidl"
        const val TRANSACTION_REGISTER = 1
        const val TRANSACTION_UNREGISTER = 2
        const val TRANSACTION_BT_ACCEPT = 20
        const val TRANSACTION_BT_HANGUP = 21
        const val TRANSACTION_BT_OPEN_MUSIC = 22
        const val TRANSACTION_BT_PLAY_MUSIC = 23
        const val TRANSACTION_BT_PRE = 24
        const val TRANSACTION_BT_NEXT = 25
        const val TRANSACTION_BT_PHONE_STATUS = 26
        const val TRANSACTION_BT_SET_PHONE_STATUS = 27
        const val TRANSACTION_BT_GET_CONNECTED_STATUS = 28
        const val TRANSACTION_BT_SET_CONNECTED_STATUS = 29
        const val TRANSACTION_BT_SET_CALL_STATUS = 30
        const val TRANSACTION_MUSIC_NEXT = 31
        const val TRANSACTION_MUSIC_PRE = 32
        const val TRANSACTION_MUSIC_PLAY = 33
        const val TRANSACTION_MUSIC_PAUSE = 34
        const val TRANSACTION_MUSIC_MODE = 35
        const val TRANSACTION_RADIO_NEXT = 11
        const val TRANSACTION_RADIO_PRE = 12
        const val TRANSACTION_RADIO_SEARCH_NEXT = 13
        const val TRANSACTION_RADIO_SEARCH_PRE = 14
        const val TRANSACTION_RADIO_SET_CHANNEL = 15
        const val TRANSACTION_RADIO_OPEN_CHANNEL = 16
        const val TRANSACTION_RADIO_BAND = 17
        const val TRANSACTION_RADIO_BAND_CHANGE = 18
        const val TRANSACTION_VOLUME_UP = 40
        const val TRANSACTION_VOLUME_DOWN = 41
        const val TRANSACTION_VOLUME_MUTE = 42
        const val TRANSACTION_VOLUME_MAX = 43
        const val TRANSACTION_VOLUME_MIN = 44
        const val TRANSACTION_VOLUME_VALUE = 45
        const val TRANSACTION_GET_VOLUME = 46
        const val TRANSACTION_GET_VOLUME_STATUS = 47
        const val TRANSACTION_GO_HOME = 48
        const val TRANSACTION_BACK = 49
        const val TRANSACTION_GET_REVERSE = 52
        const val TRANSACTION_EXTENDED_INTERFACE = 67
    }
}
