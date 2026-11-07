package com.chronosbvrx.ibizadash.bluetooth

data class BluetoothStatus(
    val name: String,
    val address: String,
    val connected: Boolean,
    val battery: Int,
    val a2dp: Boolean,
    val hfp: Boolean
)
