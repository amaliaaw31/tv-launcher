package id.bestie.tvlauncher

import android.content.Context
import android.graphics.PixelFormat
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

object SoundPlayer {
    private var ringtone: Ringtone? = null

    fun playAlarm(context: Context) {
        stop()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ringtone?.isLooping = true
            ringtone?.play()
        } catch (e: Exception) {
            // Sound is best-effort; the visual alert still shows.
        }
    }

    fun stop() {
        try {
            ringtone?.stop()
        } catch (e: Exception) {
        }
        ringtone = null
    }
}

/**
 * Earthquake popup drawn as a system overlay so it appears on top of any app
 * (YouTube, Netflix, ...) while watching. Requires "display over other apps".
 */
object OverlayPopup {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var currentView: View? = null
    private var dismissRunnable: Runnable? = null

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun showQuake(context: Context, quake: Quake) {
        val ctx = LocaleHelper.wrap(context)
        val title = ctx.getString(R.string.quake_warning_title)
        val main = ctx.getString(R.string.quake_line, formatMag(quake.magnitude), quake.wilayah)
        val sub = ctx.getString(R.string.quake_depth, quake.kedalaman, quake.jam) +
            if (quake.potensi.isNotBlank()) "\n" + quake.potensi else ""
        show(context, title, main, sub, big = false)
    }

    /** Free-form popup, used to mirror InaEEWS/BMKG app notifications. */
    fun showMessage(context: Context, title: String, main: String, sub: String) {
        show(context, title, main, sub, big = false)
    }

    fun show(context: Context, title: String, main: String, sub: String, big: Boolean) {
        val appCtx = context.applicationContext
        mainHandler.post {
            if (!canShow(appCtx)) return@post
            dismissInternal()
            val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val inflater = LayoutInflater.from(LocaleHelper.wrap(appCtx))
            val view: View
            if (big) {
                view = inflater.inflate(R.layout.activity_alert, null)
                view.findViewById<TextView>(R.id.alertTitle).text =
                    appCtx.getString(R.string.quake_big_title)
                view.findViewById<TextView>(R.id.alertMag).text = main.substringBefore("•").trim()
                view.findViewById<TextView>(R.id.alertMain).text = main
                view.findViewById<TextView>(R.id.alertSub).text = sub
            } else {
                view = inflater.inflate(R.layout.popup_quake, null)
                view.findViewById<TextView>(R.id.popupTitle).text = title
                view.findViewById<TextView>(R.id.popupMain).text = main
                view.findViewById<TextView>(R.id.popupSub).text = sub
            }
            val params = WindowManager.LayoutParams(
                if (big) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = if (big) Gravity.CENTER else Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = if (big) 0 else 48
            }
            view.isFocusableInTouchMode = true
            view.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_BACK ||
                        keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == KeyEvent.KEYCODE_ENTER ||
                        keyCode == KeyEvent.KEYCODE_BUTTON_A)
                ) {
                    dismiss()
                    true
                } else {
                    false
                }
            }
            try {
                wm.addView(view, params)
                windowManager = wm
                currentView = view
                view.requestFocus()
                if (big && Prefs.sound(appCtx)) SoundPlayer.playAlarm(appCtx)
                if (!big) {
                    val r = Runnable { dismiss() }
                    dismissRunnable = r
                    mainHandler.postDelayed(r, 30_000)
                }
            } catch (e: Exception) {
                // Overlay permission revoked between check and add — stay silent.
            }
        }
    }

    /** Full-screen red alert for big quakes (M>=6.5 or tsunami potential). */
    fun showBig(context: Context, quake: Quake) {
        val ctx = LocaleHelper.wrap(context)
        val main = ctx.getString(R.string.quake_line, formatMag(quake.magnitude), quake.wilayah)
        val sub = ctx.getString(R.string.quake_depth, quake.kedalaman, quake.jam) +
            if (quake.potensi.isNotBlank()) "\n" + quake.potensi else ""
        show(context, ctx.getString(R.string.quake_big_title), main, sub, big = true)
    }

    fun dismiss() {
        mainHandler.post { dismissInternal() }
    }

    private fun dismissInternal() {
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = null
        SoundPlayer.stop()
        val view = currentView
        if (view != null) {
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
            }
        }
        currentView = null
        windowManager = null
    }

    fun formatMag(mag: Double): String =
        if (mag % 1.0 == 0.0) mag.toInt().toString() else String.format(java.util.Locale.US, "%.1f", mag)
}
