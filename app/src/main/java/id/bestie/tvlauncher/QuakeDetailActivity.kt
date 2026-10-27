package id.bestie.tvlauncher

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * v1.9 (point 3): full earthquake detail page, opened with OK on the
 * home quake card. Reads the persisted last-quake record (written by
 * MainActivity.fetchQuake and by this page's own retry); the Coba Lagi
 * button refetches BMKG autogempa, updates the record, re-renders this
 * page — and the home card picks the same record up on resume.
 */
class QuakeDetailActivity : LocalizedActivity() {

    private val uiHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var detailMag: TextView
    private lateinit var detailRegion: TextView
    private lateinit var detailTime: TextView
    private lateinit var detailDepth: TextView
    private lateinit var detailCoords: TextView
    private lateinit var detailFelt: TextView
    private lateinit var detailTsunami: TextView
    private lateinit var detailSource: TextView
    private lateinit var detailStatus: TextView
    private lateinit var detailRetry: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quake_detail)

        detailMag = findViewById(R.id.detailMag)
        detailRegion = findViewById(R.id.detailRegion)
        detailTime = findViewById(R.id.detailTime)
        detailDepth = findViewById(R.id.detailDepth)
        detailCoords = findViewById(R.id.detailCoords)
        detailFelt = findViewById(R.id.detailFelt)
        detailTsunami = findViewById(R.id.detailTsunami)
        detailSource = findViewById(R.id.detailSource)
        detailStatus = findViewById(R.id.detailStatus)
        detailRetry = findViewById(R.id.detailRetry)

        applyBackground()
        render()
        detailRetry.setOnClickListener { refetch() }
        detailRetry.post { detailRetry.requestFocus() }
    }

    /** Same wallpaper photo the home screen last picked (persisted scene name). */
    private fun applyBackground() {
        val wallpaper = findViewById<ImageView>(R.id.detailWallpaper)
        val name = Prefs.lastSceneName(this)
        if (name.isNotBlank()) {
            val resId = resources.getIdentifier(name, "drawable", packageName)
            if (resId != 0) wallpaper.setImageResource(resId)
        }
    }

    private fun render() {
        val updatedAt = Prefs.quakeUpdatedAt(this)
        if (updatedAt == 0L) {
            detailMag.text = getString(R.string.data_failed)
            detailRegion.text = "—"
            detailTime.text = "—"
            detailDepth.text = "—"
            detailCoords.text = "—"
            detailFelt.text = "—"
            detailTsunami.text = "—"
            detailSource.text = ""
            return
        }
        detailMag.text = "M " + OverlayPopup.formatMag(Prefs.quakeMag(this))
        detailRegion.text = Prefs.quakeRegion(this).ifBlank { "—" }
        val tanggal = Prefs.quakeTanggal(this)
        val jam = Prefs.quakeJam(this)
        detailTime.text = listOf(tanggal, jam).filter { it.isNotBlank() }
            .joinToString(" • ").ifBlank { Prefs.quakeDateTime(this) }
        detailDepth.text = Prefs.quakeDepth(this).ifBlank { "—" }
        detailCoords.text = Prefs.quakeCoords(this).ifBlank { "—" }
        detailFelt.text = Prefs.quakeFelt(this).ifBlank { "—" }
        detailTsunami.text = Prefs.quakePotensi(this).ifBlank { "—" }
        val hhmm = SimpleDateFormat("HH:mm", Locale.US).format(Date(updatedAt))
        detailSource.text = getString(R.string.quake_source, hhmm)
    }

    /** Refetches the latest BMKG quake; on success both this page and (via Prefs) the home card refresh. */
    private fun refetch() {
        detailStatus.visibility = View.GONE
        detailRetry.isEnabled = false
        detailRetry.text = getString(R.string.loading)
        executor.execute {
            val json = Net.get(QuakeService.AUTOGEMPA_URL)
            val quake = json?.let { QuakeParser.parseLatest(it) }
            uiHandler.post {
                detailRetry.isEnabled = true
                detailRetry.text = getString(R.string.retry)
                if (quake != null) {
                    Prefs.saveQuake(
                        this, quake.magnitude, quake.wilayah, quake.tanggal, quake.jam,
                        quake.dateTime, quake.kedalaman, quake.koordinat, quake.dirasakan,
                        quake.potensi, System.currentTimeMillis()
                    )
                    render()
                } else {
                    detailStatus.text = getString(R.string.status_failed)
                    detailStatus.visibility = View.VISIBLE
                }
            }
        }
    }
}
