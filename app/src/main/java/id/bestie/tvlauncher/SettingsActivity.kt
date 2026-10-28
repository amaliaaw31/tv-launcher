package id.bestie.tvlauncher

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.util.Calendar

/**
 * Launcher settings, two-pane layout modeled on the user's mockup:
 * categories on the left, the selected category's rows on the right.
 * Reached ONLY from the gear icon on the home screen — this activity has
 * no launcher entry of its own, per the locked design.
 */
class SettingsActivity : LocalizedActivity() {

    private enum class Cat(val labelRes: Int) {
        GENERAL(R.string.cat_general),
        APPEARANCE(R.string.cat_appearance),
        NOTIFICATIONS(R.string.cat_notifications),
        QUAKE(R.string.cat_quake),
        ACCOUNT(R.string.cat_account),
        ABOUT(R.string.cat_about)
    }

    private lateinit var categoryList: LinearLayout
    private lateinit var rowsContainer: LinearLayout
    private lateinit var categoryTitle: TextView
    private lateinit var settingsWallpaper: ImageView
    private val catViews = LinkedHashMap<Cat, TextView>()
    private var selected = Cat.GENERAL
    private var phonePanel: View? = null

    private val thresholds = listOf(4.5, 5.0, 5.5, 6.0)

    private val teal = Color.parseColor("#67E8F9")
    private val accent = Color.parseColor("#22D3EE")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        settingsWallpaper = findViewById(R.id.settingsWallpaper)
        applySettingsBackground()
        categoryList = findViewById(R.id.categoryList)
        rowsContainer = findViewById(R.id.rowsContainer)
        categoryTitle = findViewById(R.id.categoryTitle)
        buildCategories()
        selectCategory(Cat.GENERAL)
        categoryList.post { catViews[Cat.GENERAL]?.requestFocus() }
    }

    override fun onResume() {
        super.onResume()
        // Refresh permission statuses etc. after returning from system screens.
        buildRows()
    }

    // ------------------------------------------------------------ background

    /**
     * v1.8: the Settings background is the actual current wallpaper
     * photo. The home screen's selection logic (city set, then weather /
     * night variant) persists its pick in Prefs.lastSceneName; here we
     * re-resolve that same variant against the currently set city, so
     * changing the city in the picker below also swaps the photo.
     * Fallback before the home screen has ever run: day/night by clock.
     */
    private fun applySettingsBackground() {
        val res = settingsSceneRes(currentSceneVariant())
        settingsWallpaper.setImageResource(res)
        Prefs.setLastSceneName(this, resources.getResourceEntryName(res))
    }

    private fun currentSceneVariant(): String {
        val saved = Prefs.lastSceneName(this)
        if (saved.isNotBlank()) return saved.substringAfterLast('_')
        return if (isNightByClock()) "malam" else "cerah"
    }

    /** Same resolution as MainActivity.sceneRes: `<cityKey>_<variant>`, Jakarta set as fallback. */
    private fun settingsSceneRes(variant: String): Int {
        // City name -> set key is a plain lowercase/underscore transform
        // for every bundled city (matches MainActivity.CITY_SCENE_KEYS).
        val cityKey = Prefs.city(this).name.lowercase().replace(' ', '_')
        val resId = resources.getIdentifier(cityKey + "_" + variant, "drawable", packageName)
        if (resId != 0) return resId
        return when (variant) {
            "hujan" -> R.drawable.jakarta_hujan
            "malam" -> R.drawable.jakarta_malam
            "berawan" -> R.drawable.jakarta_berawan
            else -> R.drawable.jakarta_cerah
        }
    }

    /** Same night window as the home screen: 18:30-05:30 local. */
    private fun isNightByClock(): Boolean {
        val now = Calendar.getInstance()
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return minutes >= 18 * 60 + 30 || minutes < 5 * 60 + 30
    }

    // ------------------------------------------------------------ categories

    private fun buildCategories() {
        categoryList.removeAllViews()
        catViews.clear()
        val textColors = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_selected, android.R.attr.state_focused),
                intArrayOf(android.R.attr.state_focused),
                intArrayOf(android.R.attr.state_selected),
                intArrayOf()
            ),
            intArrayOf(accent, accent, teal, Color.WHITE)
        )
        for (cat in Cat.values()) {
            val item = TextView(this).apply {
                text = getString(cat.labelRes)
                textSize = 15f
                setPadding(dp(16), dp(11), dp(16), dp(11))
                setBackgroundResource(R.drawable.cat_item_bg)
                setTextColor(textColors)
                isFocusable = true
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6) }
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus && selected != cat) selectCategory(cat)
                }
                setOnClickListener { focusFirstRow() }
                setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN &&
                        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    ) {
                        focusFirstRow()
                        true
                    } else false
                }
            }
            catViews[cat] = item
            categoryList.addView(item)
        }
    }

    private fun selectCategory(cat: Cat) {
        selected = cat
        phonePanel = null
        for ((c, view) in catViews) view.isSelected = c == cat
        categoryTitle.text = getString(cat.labelRes)
        buildRows()
    }

    private fun focusFirstRow() {
        for (i in 0 until rowsContainer.childCount) {
            val child = rowsContainer.getChildAt(i)
            if (child.isFocusable) {
                child.requestFocus()
                return
            }
        }
    }

    private fun backToCategories() {
        catViews[selected]?.requestFocus()
    }

    // ------------------------------------------------------------ rows

    private fun buildRows() {
        rowsContainer.removeAllViews()
        phonePanel = null
        when (selected) {
            Cat.GENERAL -> buildGeneralRows()
            Cat.APPEARANCE -> buildAppearanceRows()
            Cat.NOTIFICATIONS -> buildNotificationRows()
            Cat.QUAKE -> buildQuakeRows()
            Cat.ACCOUNT -> buildAccountRows()
            Cat.ABOUT -> buildAboutRows()
        }
    }

    private fun buildGeneralRows() {
        addValueRow(
            getString(R.string.set_language),
            if (Prefs.isEnglish(this)) getString(R.string.lang_english)
            else getString(R.string.lang_indonesia)
        ) { showLanguagePicker() }
        addValueRow(getString(R.string.set_location), Prefs.city(this).name) { showCityPicker() }
    }

    private fun buildAppearanceRows() {
        addInfoRow(
            getString(R.string.appearance_wallpaper_label),
            getString(R.string.appearance_wallpaper_value)
        )
    }

    private fun buildNotificationRows() {
        addInfoTextRow(getString(R.string.notif_popup_info))
        addInfoTextRow(getString(R.string.notif_full_info))
        addInfoTextRow(getString(R.string.notif_source_info))
    }

    private fun buildQuakeRows() {
        addValueRow(
            getString(R.string.set_threshold),
            getString(R.string.set_threshold_value, OverlayPopup.formatMag(Prefs.threshold(this)))
        ) { showThresholdPicker() }
        addValueRow(
            getString(R.string.set_overlay_perm),
            if (Settings.canDrawOverlays(this)) getString(R.string.status_active)
            else getString(R.string.status_inactive)
        ) { openOverlayPermission() }
        addValueRow(
            getString(R.string.set_notif_perm),
            if (notifListenerEnabled()) getString(R.string.status_active)
            else getString(R.string.status_inactive)
        ) { openNotifListenerSettings() }
        addToggleRow(getString(R.string.set_sound), Prefs.sound(this)) { on ->
            Prefs.setSound(this, on)
        }
        addBigButton(getString(R.string.set_test_popup)) { fireTestPopup() }
    }

    private fun buildAccountRows() {
        addValueRow(
            getString(R.string.set_connect_phone),
            if (ControlServer.isRunning()) ControlServer.url() ?: getString(R.string.connect_phone_hint)
            else getString(R.string.connect_phone_hint)
        ) { togglePhonePanel() }
    }

    private fun buildAboutRows() {
        addInfoRow(getString(R.string.about_app_label), getString(R.string.app_name))
        addInfoRow(getString(R.string.about_version_label), "2.3")
        addInfoRow(getString(R.string.about_package_label), packageName)
        addInfoTextRow(getString(R.string.about_credit))
    }

    /** Label left, value (teal) right, whole row focusable/clickable. */
    private fun addValueRow(label: String, value: String, action: () -> Unit) {
        val row = baseRow()
        row.addView(rowLabel(label))
        val valueView = TextView(this).apply {
            text = "$value ›"
            textSize = 14f
            setTextColor(teal)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        row.addView(valueView)
        row.setOnClickListener { action() }
        rowsContainer.addView(row)
    }

    /** Read-only label/value row (not focusable). */
    private fun addInfoRow(label: String, value: String) {
        val row = baseRow(focusable = false)
        row.addView(rowLabel(label))
        val valueView = TextView(this).apply {
            text = value
            textSize = 13f
            setTextColor(Color.parseColor("#B0BEC5"))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        row.addView(valueView)
        rowsContainer.addView(row)
    }

    /** Read-only full-width text row (not focusable). */
    private fun addInfoTextRow(text: String) {
        val row = baseRow(focusable = false)
        val tv = TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.parseColor("#CFD8DC"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        row.addView(tv)
        rowsContainer.addView(row)
    }

    /** Label left, teal pill toggle right; updates in place (keeps focus). */
    private fun addToggleRow(label: String, initial: Boolean, onChange: (Boolean) -> Unit) {
        val row = baseRow()
        row.addView(rowLabel(label))
        val knob = View(this).apply {
            setBackgroundResource(R.drawable.toggle_knob)
            layoutParams = FrameLayout.LayoutParams(dp(18), dp(18)).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                marginStart = dp(3)
            }
        }
        val toggle = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(24))
            addView(knob)
        }
        var state = initial
        fun render() {
            toggle.setBackgroundResource(if (state) R.drawable.toggle_on else R.drawable.toggle_off)
            (knob.layoutParams as FrameLayout.LayoutParams).gravity =
                Gravity.CENTER_VERTICAL or (if (state) Gravity.END else Gravity.START)
            (knob.layoutParams as FrameLayout.LayoutParams).marginStart = if (state) 0 else dp(3)
            (knob.layoutParams as FrameLayout.LayoutParams).marginEnd = if (state) dp(3) else 0
            knob.requestLayout()
        }
        render()
        row.addView(toggle)
        row.setOnClickListener {
            state = !state
            render()
            onChange(state)
        }
        rowsContainer.addView(row)
    }

    /** Big teal pill button, like "Uji Pop-up Gempa" in the mockup. */
    private fun addBigButton(label: String, action: () -> Unit) {
        val btn = TextView(this).apply {
            text = label
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#06222A"))
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.big_button_bg)
            isFocusable = true
            isClickable = true
            setPadding(dp(16), dp(13), dp(16), dp(13))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(8)
            }
            setOnClickListener { action() }
            addBackKey(this)
        }
        rowsContainer.addView(btn)
    }

    private fun baseRow(focusable: Boolean = true): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.set_row_bg)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isFocusable = focusable
            isClickable = focusable
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            if (focusable) addBackKey(this)
        }
    }

    private fun rowLabel(label: String): TextView {
        return TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
    }

    /** DPAD_LEFT inside the right pane returns focus to the category list. */
    private fun addBackKey(view: View) {
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                backToCategories()
                true
            } else false
        }
    }

    // ------------------------------------------------------------ pickers

    private fun showPicker(titleRes: Int, items: List<String>, current: Int, onPick: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setSingleChoiceItems(items.toTypedArray(), current) { dialog, which ->
                onPick(which)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCityPicker() {
        showPicker(
            R.string.set_location,
            Cities.list.map { it.name },
            Prefs.cityIndex(this)
        ) { which ->
            Prefs.setCityIndex(this, which)
            applySettingsBackground()
            buildRows()
        }
    }

    private fun showLanguagePicker() {
        showPicker(
            R.string.set_language,
            listOf(getString(R.string.lang_indonesia), getString(R.string.lang_english)),
            if (Prefs.isEnglish(this)) 1 else 0
        ) { which ->
            Prefs.setLanguage(this, if (which == 1) "en" else "in")
            recreate()
        }
    }

    private fun showThresholdPicker() {
        val current = thresholds.indexOf(Prefs.threshold(this)).let { if (it < 0) 1 else it }
        showPicker(
            R.string.set_threshold,
            thresholds.map { getString(R.string.set_threshold_value, OverlayPopup.formatMag(it)) },
            current
        ) { which ->
            Prefs.setThreshold(this, thresholds[which])
            buildRows()
        }
    }

    // ------------------------------------------------------------ permissions

    private fun notifListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ) ?: return false
        val mine = ComponentName(this, NotifListener::class.java)
        return flat.split(":").any { ComponentName.unflattenFromString(it) == mine }
    }

    private fun openOverlayPermission() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            } catch (e2: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    private fun openNotifListenerSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    // ------------------------------------------------------------ actions

    private fun fireTestPopup() {
        val test = Quake(
            magnitude = 5.4,
            wilayah = getString(R.string.test_quake_wilayah),
            kedalaman = "10 km",
            jam = "",
            tanggal = "",
            dateTime = "test",
            potensi = "",
            dirasakan = ""
        )
        if (OverlayPopup.canShow(this)) {
            OverlayPopup.showQuake(this, test)
        } else {
            Toast.makeText(this, getString(R.string.set_overlay_perm), Toast.LENGTH_LONG).show()
            QuakeAlerter.launchAlertActivity(this, test)
        }
    }

    /** QR + URL block appended under the Akun rows; tapping the row again hides it. */
    private fun togglePhonePanel() {
        phonePanel?.let {
            rowsContainer.removeView(it)
            phonePanel = null
            return
        }
        if (!ControlServer.isRunning() && !ControlServer.start(this)) {
            Toast.makeText(this, getString(R.string.phone_server_failed), Toast.LENGTH_LONG).show()
            return
        }
        val url = ControlServer.url()
        if (url == null) {
            Toast.makeText(this, getString(R.string.no_data), Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, getString(R.string.phone_server_started), Toast.LENGTH_SHORT).show()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundResource(R.drawable.set_card)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        val qr = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(256), dp(256))
            setBackgroundColor(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            contentDescription = null
        }
        makeQr(url)?.let { qr.setImageBitmap(it) }
        panel.addView(qr)
        panel.addView(TextView(this).apply {
            text = getString(R.string.phone_connect_info, url)
            textSize = 13f
            setTextColor(Color.parseColor("#ECEFF1"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        })
        rowsContainer.addView(panel)
        phonePanel = panel
    }

    private fun makeQr(text: String): Bitmap? {
        return try {
            val size = 340
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
