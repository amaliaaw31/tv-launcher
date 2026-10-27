package id.bestie.tvlauncher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Mirrors notifications from the InaEEWS app (and the BMKG app) into the
 * launcher's own full-screen-capable popup, because InaEEWS does not expose
 * a public push API to third-party apps.
 */
class NotifListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName?.lowercase() ?: return
        if (!pkg.contains("inaeews") && !pkg.contains("bmkg")) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val appLabel = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (e: Exception) {
            sbn.packageName
        }
        OverlayPopup.showMessage(this, appLabel, title, text)
    }
}
