package id.bestie.tvlauncher

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.TextView

/** Full-screen red alert for major earthquakes (fallback surface when overlay permission is off). */
class AlertActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_alert)

        val mag = intent.getStringExtra(Quake.EXTRA_MAG).orEmpty()
        val wilayah = intent.getStringExtra(Quake.EXTRA_WILAYAH).orEmpty()
        val kedalaman = intent.getStringExtra(Quake.EXTRA_KEDALAMAN).orEmpty()
        val jam = intent.getStringExtra(Quake.EXTRA_JAM).orEmpty()
        val potensi = intent.getStringExtra(Quake.EXTRA_POTENSI).orEmpty()

        findViewById<TextView>(R.id.alertMag).text = "M $mag"
        findViewById<TextView>(R.id.alertMain).text = wilayah
        findViewById<TextView>(R.id.alertSub).text =
            getString(R.string.quake_depth, kedalaman, jam) +
                if (potensi.isNotBlank()) "\n" + potensi else ""

        if (Prefs.sound(this)) SoundPlayer.playAlarm(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_BUTTON_A
        ) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        SoundPlayer.stop()
        super.onDestroy()
    }
}
