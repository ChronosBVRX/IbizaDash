package com.chronosbvrx.ibizadash.nav

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.CopyOnWriteArraySet

data class TurnInstruction(
    val title: String,
    val text: String,
    val sub: String?
)

object NavRepository {
    private val listeners = CopyOnWriteArraySet<(TurnInstruction?) -> Unit>()
    var current: TurnInstruction? = null
        private set

    fun addListener(listener: (TurnInstruction?) -> Unit) {
        listeners.add(listener)
        listener(current)
    }

    fun removeListener(listener: (TurnInstruction?) -> Unit) {
        listeners.remove(listener)
    }

    fun publish(title: String, text: String, sub: String?) {
        val turn = TurnInstruction(title.trim(), text.trim(), sub?.trim()?.takeIf { it.isNotBlank() })
        current = turn
        listeners.forEach { it(turn) }
    }

    fun clear() {
        current = null
        listeners.forEach { it(null) }
    }
}

class NavNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn?.notification ?: return
        val pkg = sbn.packageName
        val isNav = n.category == Notification.CATEGORY_NAVIGATION ||
            pkg == "com.google.android.apps.maps" ||
            pkg == "com.waze" ||
            pkg == "com.here.app.maps" ||
            pkg == "com.sygic.aura"
        if (!isNav) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        if (title.isNullOrBlank() && text.isNullOrBlank()) return

        NavRepository.publish(title ?: "", text ?: "", sub)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val n = sbn?.notification ?: return
        val pkg = sbn.packageName
        val isNav = n.category == Notification.CATEGORY_NAVIGATION ||
            pkg == "com.google.android.apps.maps" ||
            pkg == "com.waze" ||
            pkg == "com.here.app.maps" ||
            pkg == "com.sygic.aura"
        if (isNav) NavRepository.clear()
    }
}
