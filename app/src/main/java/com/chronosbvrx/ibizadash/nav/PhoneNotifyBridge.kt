package com.chronosbvrx.ibizadash.nav

import org.json.JSONObject

/** Notificaciones reenviadas por el teléfono (WhatsApp, llamadas, etc.). */
object PhoneNotifyBridge {
    @Volatile
    private var listener: ((String) -> Unit)? = null

    fun attach(listener: (String) -> Unit) {
        this.listener = listener
    }

    fun detach() {
        listener = null
    }

    fun onNotify(obj: JSONObject) {
        val app = obj.optString("app", "")
        val title = obj.optString("title", "")
        val text = obj.optString("text", "")
        val message = listOf(app, title, text)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .take(160)
        if (message.isBlank()) return
        listener?.invoke(message)
    }
}
