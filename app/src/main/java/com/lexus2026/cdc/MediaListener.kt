package com.lexus2026.cdc

import android.app.Notification
import android.app.PendingIntent
import android.graphics.drawable.Drawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Da acceso a la sesión de medios activa y a las notificaciones que muestra el panel. */
class MediaListener : NotificationListenerService() {

    class Act(val title: String, val intent: PendingIntent?)

    class Note(
        val key: String, val pkg: String, val app: String, val title: String, val text: String,
        val color: Int, val icon: Drawable?, val appIcon: Drawable?, val intent: PendingIntent?,
        val clearable: Boolean, val autoCancel: Boolean, val time: Long,
        val actions: List<Act>
    )

    companion object {
        @Volatile var instance: MediaListener? = null
        @Volatile var onChange: (() -> Unit)? = null
    }

    override fun onListenerConnected() { instance = this; onChange?.invoke() }
    override fun onListenerDisconnected() { if (instance === this) instance = null }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { onChange?.invoke() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { onChange?.invoke() }

    fun notes(): List<Note> = try {
        val pm = packageManager
        (activeNotifications ?: emptyArray())
            .filter { it.packageName != packageName && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            .sortedByDescending { it.postTime }
            .map { sbn ->
                val n = sbn.notification
                val ex = n.extras
                val app = try { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() }
                          catch (e: Exception) { sbn.packageName }
                val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
                val ic = try { n.smallIcon?.loadDrawable(this) } catch (e: Exception) { null }
                val acts = (n.actions ?: emptyArray()).filter { it.remoteInputs == null }
                    .map { Act(it.title?.toString() ?: "", it.actionIntent) }
                val appIc = try { pm.getApplicationIcon(sbn.packageName) } catch (e: Exception) { null }
                Note(sbn.key, sbn.packageName, app, title, text, n.color, ic, appIc, n.contentIntent,
                    sbn.isClearable, (n.flags and Notification.FLAG_AUTO_CANCEL) != 0, sbn.postTime, acts)
            }
            .filter { it.title.isNotEmpty() || it.text.isNotEmpty() }
    } catch (e: Throwable) { emptyList() }
}
