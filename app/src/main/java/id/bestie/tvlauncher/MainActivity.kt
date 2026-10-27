package id.bestie.tvlauncher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : LocalizedActivity() {

    private val uiHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var greetingText: TextView
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var weatherMain: TextView
    private lateinit var weatherCity: TextView
    private lateinit var weatherAqi: TextView
    private lateinit var prayerNext: TextView
    private lateinit var prayerAll: TextView
    private lateinit var quakeMain: TextView
    private lateinit var quakeSub: TextView
    private lateinit var quakeFelt: TextView
    private lateinit var volcanoText: TextView
    private lateinit var dockRow: LinearLayout
    private lateinit var wallpaperView: ImageView
    private lateinit var tintView: View
    private lateinit var forecastRow: LinearLayout

    private var lastFetchMs = 0L

    // Last weather state, kept so the clock tick can re-apply the wallpaper
    // variant on the hour boundary without waiting for the next data refresh.
    private var lastWeatherCode = -1
    private var lastIsDay = 1
    private var lastTintMinute = -1
    private var lastSceneRes = 0
    private var lastTintApplied = Int.MIN_VALUE

    companion object {
        // The stock Google TV home app: never a dock entry (we ARE the
        // launcher). Its label is exactly "Google TV"; see isDockWorthy().
        private const val STOCK_LAUNCHER_PACKAGE = "com.google.android.apps.tv.launcherx"

        // Apps the user explicitly hid from the dock (v1.7), matched
        // case-insensitively: label fragments (substring of the trimmed
        // label) and package fragments (substring of the lowercase
        // package name). See isDockWorthy().
        private val HIDDEN_LABEL_PARTS = listOf(
            "castplay", "multimedia player", "tv wizard",
            "wi-fi display", "wifi display", "display sink"
        )
        private val HIDDEN_PACKAGE_PARTS = listOf(
            "castplay", "tvwizard", "wifidisplay", "displaysink", "miracast"
        )

        // Settings city name -> bundled wallpaper set key (drawable prefix).
        // A city whose set is not bundled falls back to "jakarta" in
        // sceneRes(). Resource names use underscores (no hyphens allowed).
        private val CITY_SCENE_KEYS = mapOf(
            "Jakarta" to "jakarta",
            "Bandung" to "bandung",
            "Surabaya" to "surabaya",
            "Yogyakarta" to "yogyakarta",
            "Semarang" to "semarang",
            "Medan" to "medan",
            "Makassar" to "makassar",
            "Denpasar" to "denpasar",
            "Balikpapan" to "balikpapan",
            "Tanah Grogot" to "tanah_grogot"
        )

        // Open-Meteo codes that map to the rain scene (drizzle, rain, snow,
        // showers, thunderstorms).
        private val RAIN_CODES = setOf(
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67,
            71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99
        )
    }

    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            uiHandler.postDelayed(this, 1000)
        }
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshData()
            uiHandler.postDelayed(this, 15 * 60 * 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        greetingText = findViewById(R.id.greetingText)
        clockText = findViewById(R.id.clockText)
        dateText = findViewById(R.id.dateText)
        weatherMain = findViewById(R.id.weatherMain)
        weatherCity = findViewById(R.id.weatherCity)
        weatherAqi = findViewById(R.id.weatherAqi)
        prayerNext = findViewById(R.id.prayerNext)
        prayerAll = findViewById(R.id.prayerAll)
        quakeMain = findViewById(R.id.quakeMain)
        quakeSub = findViewById(R.id.quakeSub)
        quakeFelt = findViewById(R.id.quakeFelt)
        volcanoText = findViewById(R.id.volcanoText)
        dockRow = findViewById(R.id.dockRow)
        wallpaperView = findViewById(R.id.wallpaper)
        tintView = findViewById(R.id.tintView)
        forecastRow = findViewById(R.id.forecastRow)

        val inputBtn = findViewById<Button>(R.id.inputBtn)
        val gearBtn = findViewById<ImageButton>(R.id.gearBtn)
        inputBtn.setOnClickListener { openTvInput() }
        gearBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        // Pill background turns white on focus, so foreground colors must invert.
        inputBtn.setOnFocusChangeListener { _, hasFocus ->
            inputBtn.setTextColor(if (hasFocus) Color.BLACK else Color.WHITE)
        }
        gearBtn.setOnFocusChangeListener { _, hasFocus ->
            gearBtn.setColorFilter(if (hasFocus) Color.BLACK else Color.WHITE)
        }
        gearBtn.setColorFilter(Color.WHITE)

        buildDock()
        QuakeService.start(this)
    }

    override fun onResume() {
        super.onResume()
        uiHandler.removeCallbacks(clockRunnable)
        uiHandler.post(clockRunnable)
        if (System.currentTimeMillis() - lastFetchMs > 60_000) {
            uiHandler.removeCallbacks(refreshRunnable)
            uiHandler.post(refreshRunnable)
        }
        buildDock()
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(clockRunnable)
    }

    // ---------------------------------------------------------------- clock

    private fun updateClock() {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        greetingText.text = getString(
            when (hour) {
                in 4..10 -> R.string.greet_morning
                in 11..14 -> R.string.greet_noon
                in 15..18 -> R.string.greet_afternoon
                else -> R.string.greet_night
            }
        )
        clockText.text = SimpleDateFormat("HH:mm", Locale.US).format(now.time)
        val dateFmt = SimpleDateFormat("EEEE, dd MMMM yyyy", LocaleHelper.localeFor(this))
        dateText.text = dateFmt.format(now.time)
        // Re-apply the wallpaper variant once per minute so the night
        // scene kicks in on time (e.g. at 18:30) between weather refreshes.
        val minuteOfDay = hour * 60 + now.get(Calendar.MINUTE)
        if (minuteOfDay != lastTintMinute) {
            lastTintMinute = minuteOfDay
            applyWallpaperVariant(lastWeatherCode, lastIsDay)
        }
    }

    // ---------------------------------------------------------------- input

    /** Best-effort: open the system TV input/source picker; not every TV exposes it the same way. */
    private fun openTvInput() {
        val attempts = listOf(
            Intent("android.settings.TV_INPUT_SETTINGS"),
            Intent(Intent.ACTION_MAIN).setClassName(
                "com.google.android.tv", "com.google.android.tv.MainActivity"
            ),
            Intent(Intent.ACTION_MAIN).setClassName(
                "com.android.tv", "com.android.tv.MainActivity"
            )
        )
        for (intent in attempts) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                // try the next one
            } catch (e: Exception) {
                // try the next one
            }
        }
        // Fall back to the TV (live channels) app if it is installed.
        val tvIntent = packageManager.getLeanbackLaunchIntentForPackage("com.google.android.tv")
            ?: packageManager.getLaunchIntentForPackage("com.google.android.tv")
        if (tvIntent != null) {
            try {
                startActivity(tvIntent)
                return
            } catch (e: Exception) {
            }
        }
        Toast.makeText(this, getString(R.string.input_unavailable), Toast.LENGTH_LONG).show()
    }

    // ---------------------------------------------------------------- dock

    private fun buildDock() {
        dockRow.removeAllViews()
        val pm = packageManager
        val byPackage = LinkedHashMap<String, ResolveInfo>()
        val leanback = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val normal = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        for (ri in pm.queryIntentActivities(leanback, 0)) {
            byPackage.putIfAbsent(ri.activityInfo.packageName, ri)
        }
        for (ri in pm.queryIntentActivities(normal, 0)) {
            byPackage.putIfAbsent(ri.activityInfo.packageName, ri)
        }
        val apps = byPackage.values
            .mapNotNull { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || isSystemSettings(pkg)) return@mapNotNull null
                val label = try {
                    ri.loadLabel(pm)?.toString()
                } catch (e: Exception) {
                    null
                }
                if (!isDockWorthy(pkg, label)) return@mapNotNull null
                ri to label!!
            }
            .sortedBy { it.second.lowercase() }

        val inflater = LayoutInflater.from(this)
        for ((ri, name) in apps) {
            val pkg = ri.activityInfo.packageName
            val item = inflater.inflate(R.layout.item_dock_app, dockRow, false)
            val icon = item.findViewById<ImageView>(R.id.appIcon)
            val label = item.findViewById<TextView>(R.id.appLabel)
            label.text = name
            try {
                icon.setImageDrawable(ri.loadIcon(pm))
            } catch (e: Exception) {
            }
            item.setOnClickListener {
                val launch = pm.getLeanbackLaunchIntentForPackage(pkg)
                    ?: pm.getLaunchIntentForPackage(pkg)
                if (launch != null) {
                    try {
                        startActivity(launch)
                    } catch (e: Exception) {
                        Toast.makeText(this, name, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            item.setOnFocusChangeListener { _, hasFocus ->
                // Label lives only inside the tile; no duplicate floating title.
                label.setTextColor(if (hasFocus) Color.BLACK else Color.WHITE)
            }
            dockRow.addView(item)
        }
        if (dockRow.childCount > 0 && currentFocus == null) {
            dockRow.post { dockRow.getChildAt(0)?.requestFocus() }
        }
    }

    /**
     * Dock filter (v1.5, user-reversed rule: "dock diisi dengan aplikasi
     * yang sudah ada di TV"). The v1.4 strict filter left only Play Store
     * on her TV because everything else is a preinstalled system app — so
     * now EVERY app with a launch intent is welcome again, system apps
     * included (Multimedia Player, CastPlayTV, Wi-Fi Display Sink,
     * TV Wizard return on purpose). The only checks left:
     *  1. Human display label required — blank, equal to / starting with
     *     the package name, or containing a dot (package-style text)
     *     fails. This keeps the broken "com.walton.startupbroadcast..."
     *     tile out: its label is its package name.
     *  2. The stock Google TV launcher never appears (its package, or
     *     any package labeled exactly "Google TV").
     * Our own package and system Settings apps are excluded by the
     * caller (buildDock / isSystemSettings) — Settings stays reachable
     * only via the top-right gear icon.
     *
     * v1.7 addition — explicit hidden apps (user request after seeing
     * v1.6): CastPlayTV, Multimedia Player, TV Wizard and Wi-Fi Display
     * Sink never appear. Matched robustly two ways (both
     * case-insensitive): the label containing "castplay", "multimedia
     * player", "tv wizard", "wi-fi display", "wifi display" or
     * "display sink"; or the package name containing "castplay",
     * "tvwizard", "wifidisplay", "displaysink" or "miracast".
     */
    private fun isDockWorthy(pkg: String, label: String?): Boolean {
        if (label.isNullOrBlank()) return false
        val name = label.trim()
        if (name.equals(pkg, ignoreCase = true) || name.startsWith(pkg, ignoreCase = true)) return false
        if (name.contains('.')) return false // package-style text is not a human label
        if (pkg.equals(STOCK_LAUNCHER_PACKAGE, ignoreCase = true)) return false
        if (name.equals("Google TV", ignoreCase = true)) return false // stock home app
        val lowerName = name.lowercase()
        if (HIDDEN_LABEL_PARTS.any { lowerName.contains(it) }) return false
        val lowerPkg = pkg.lowercase()
        if (HIDDEN_PACKAGE_PARTS.any { lowerPkg.contains(it) }) return false
        return true
    }

    private fun isSystemSettings(pkg: String): Boolean {
        val p = pkg.lowercase()
        if (!p.contains("settings")) return false
        return p.startsWith("com.android.") || p.startsWith("com.google.android.") ||
            p.startsWith("com.skyworth") || p.startsWith("com.sony") ||
            p.startsWith("com.tcl") || p.contains("tv.settings")
    }

    // ---------------------------------------------------------------- data

    private fun refreshData() {
        lastFetchMs = System.currentTimeMillis()
        val city = Prefs.city(this)
        executor.execute {
            fetchWeather(city)
            fetchPrayer(city)
            fetchQuake()
            fetchVolcano()
        }
    }

    private data class ForecastDay(val date: String, val code: Int, val max: Double, val min: Double)

    private fun fetchWeather(city: City) {
        val forecastJson = Net.get(
            "https://api.open-meteo.com/v1/forecast?latitude=${city.lat}&longitude=${city.lon}" +
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=4&timezone=auto"
        )
        val airJson = Net.get(
            "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${city.lat}&longitude=${city.lon}" +
                "&current=us_aqi,pm2_5&timezone=auto"
        )
        var temp: Double? = null
        var code = -1
        var isDay = 1
        val forecastDays = mutableListOf<ForecastDay>()
        try {
            if (forecastJson != null) {
                val root = JSONObject(forecastJson)
                val current = root.getJSONObject("current")
                temp = current.optDouble("temperature_2m")
                code = current.optInt("weather_code", -1)
                isDay = current.optInt("is_day", 1)
                val daily = root.optJSONObject("daily")
                if (daily != null) {
                    val times = daily.optJSONArray("time")
                    val codes = daily.optJSONArray("weather_code")
                    val maxs = daily.optJSONArray("temperature_2m_max")
                    val mins = daily.optJSONArray("temperature_2m_min")
                    if (times != null && codes != null && maxs != null && mins != null) {
                        val n = minOf(4, times.length(), codes.length(), maxs.length(), mins.length())
                        for (i in 0 until n) {
                            forecastDays.add(
                                ForecastDay(
                                    date = times.optString(i),
                                    code = codes.optInt(i, -1),
                                    max = maxs.optDouble(i),
                                    min = mins.optDouble(i)
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
        }
        var aqi: Int? = null
        var pm25: Double? = null
        try {
            if (airJson != null) {
                val current = JSONObject(airJson).getJSONObject("current")
                if (!current.isNull("us_aqi")) aqi = current.optDouble("us_aqi").toInt()
                if (!current.isNull("pm2_5")) pm25 = current.optDouble("pm2_5")
            }
        } catch (e: Exception) {
        }
        val tempFinal = temp
        val codeFinal = code
        val isDayFinal = isDay
        val aqiFinal = aqi
        val pmFinal = pm25
        uiHandler.post {
            if (tempFinal != null && !tempFinal.isNaN()) {
                weatherMain.text = "${tempFinal.toInt()}°  ${weatherText(codeFinal)}"
            } else {
                weatherMain.text = getString(R.string.no_data)
            }
            weatherCity.text = city.name
            lastWeatherCode = codeFinal
            lastIsDay = isDayFinal
            weatherAqi.text = if (aqiFinal != null) {
                getString(
                    R.string.air_quality, aqiFinal,
                    pmFinal?.let { String.format(Locale.US, "%.0f", it) } ?: "-"
                )
            } else ""
            applyWallpaperVariant(codeFinal, isDayFinal)
            buildForecast(forecastDays)
        }
    }

    /** Fills the forecast strip under the date: today + next 3 days. */
    private fun buildForecast(days: List<ForecastDay>) {
        forecastRow.removeAllViews()
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()
        val dateParser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayFmt = SimpleDateFormat("EEE", LocaleHelper.localeFor(this))
        days.forEachIndexed { index, day ->
            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setBackgroundResource(R.drawable.forecast_chip)
                setPadding(dp(9), dp(7), dp(9), dp(7))
                // v1.8: equal-weight chips (0dp width, weight 1) so the
                // four chips always share the strip's full width in one
                // horizontal row — the old fixed 74dp width inside a
                // wrap_content strip let them bunch up beside the date.
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ).apply { if (index < days.size - 1) marginEnd = dp(8) }
            }
            val dayLabel = if (index == 0) {
                getString(R.string.forecast_today)
            } else {
                try {
                    dateParser.parse(day.date)?.let { dayFmt.format(it) } ?: ""
                } catch (e: Exception) {
                    ""
                }
            }
            chip.addView(TextView(this).apply {
                text = dayLabel
                textSize = 11f
                setTextColor(Color.parseColor("#B0BEC5"))
                gravity = android.view.Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            chip.addView(ImageView(this).apply {
                setImageResource(forecastIconFor(day.code))
                contentDescription = null
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
                    .apply { topMargin = dp(3) }
            })
            chip.addView(TextView(this).apply {
                text = String.format(
                    Locale.US, "%d°/%d°",
                    Math.round(day.max), Math.round(day.min)
                )
                textSize = 12f
                setTextColor(Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(3) }
            })
            forecastRow.addView(chip)
        }
    }

    /** Maps WMO weather codes to the bundled forecast icons (same grouping as the wallpaper). */
    private fun forecastIconFor(code: Int): Int {
        return when (code) {
            0, 1 -> R.drawable.ic_wx_clear
            2 -> R.drawable.ic_wx_partly
            3 -> R.drawable.ic_wx_cloudy
            45, 48 -> R.drawable.ic_wx_fog
            51, 53, 55, 56, 57 -> R.drawable.ic_wx_drizzle
            61, 63, 66, 80, 81 -> R.drawable.ic_wx_rain
            65, 67, 82 -> R.drawable.ic_wx_heavy_rain
            71, 73, 75, 77, 85, 86 -> R.drawable.ic_wx_snow
            95, 96, 99 -> R.drawable.ic_wx_thunder
            else -> R.drawable.ic_wx_cloudy
        }
    }

    /**
     * Wallpaper = one of the bundled scene photos, picked in two steps:
     * first the set for the selected city (see [sceneRes]), then the
     * variant inside that set by the Open-Meteo weather code and
     * day/night. The API's is_day flag alone proved unreliable on the
     * TV, so the local clock window 18:30-05:30 also counts as night.
     */
    private fun applyWallpaperVariant(code: Int, isDay: Int) {
        val isRain = code in RAIN_CODES
        val isNight = isDay == 0 || isNightByClock()
        var tint = 0x00000000
        val variant = when {
            isRain -> {
                // Rain scene already shows rain; at night just darken it.
                if (isNight) tint = 0xB30A1430.toInt()
                "hujan"
            }
            isNight -> "malam"
            code == 2 -> "berawan"
            code == 3 -> {
                // No dedicated overcast photo: cloudy scene + gray tint.
                tint = 0x4D6E7B8B
                "berawan"
            }
            code == 45 || code == 48 -> {
                tint = 0x66B0BEC5
                "berawan"
            }
            else -> "cerah" // 0, 1 and unknown/no-data
        }
        val scene = sceneRes(variant)
        if (scene != lastSceneRes) {
            lastSceneRes = scene
            wallpaperView.setImageResource(scene)
            // Hand the picked scene to the Settings screen (v1.8), which
            // uses the same photo as its background.
            Prefs.setLastSceneName(this, resources.getResourceEntryName(scene))
        }
        if (tint != lastTintApplied) {
            lastTintApplied = tint
            tintView.setBackgroundColor(tint)
        }
    }

    /**
     * Scene photos are bundled per city as `<city>_<variant>` drawables
     * (see [CITY_SCENE_KEYS] for the city-name -> set-key mapping). A city
     * whose set was not bundled falls back to the Jakarta set.
     */
    private fun sceneRes(variant: String): Int {
        val cityKey = CITY_SCENE_KEYS[Prefs.city(this).name] ?: "jakarta"
        val resId = resources.getIdentifier(cityKey + "_" + variant, "drawable", packageName)
        if (resId != 0) return resId
        return when (variant) {
            "hujan" -> R.drawable.jakarta_hujan
            "malam" -> R.drawable.jakarta_malam
            "berawan" -> R.drawable.jakarta_berawan
            else -> R.drawable.jakarta_cerah
        }
    }

    private fun isNightByClock(): Boolean {
        val now = Calendar.getInstance()
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return minutes >= 18 * 60 + 30 || minutes < 5 * 60 + 30
    }

    private fun weatherText(code: Int): String {
        val en = Prefs.isEnglish(this)
        return when (code) {
            0 -> if (en) "Clear" else "Cerah"
            1 -> if (en) "Mostly Clear" else "Cerah Berawan"
            2 -> if (en) "Partly Cloudy" else "Berawan"
            3 -> if (en) "Overcast" else "Mendung"
            45, 48 -> if (en) "Fog" else "Berkabut"
            51, 53, 55, 56, 57 -> if (en) "Drizzle" else "Gerimis"
            61, 80 -> if (en) "Light Rain" else "Hujan Ringan"
            63, 81 -> if (en) "Rain" else "Hujan"
            65, 82 -> if (en) "Heavy Rain" else "Hujan Lebat"
            66, 67 -> if (en) "Rain" else "Hujan"
            71, 73, 75, 77, 85, 86 -> if (en) "Snow" else "Salju"
            95 -> if (en) "Thunderstorm" else "Badai Petir"
            96, 99 -> if (en) "Thunderstorm, Hail" else "Badai Petir + Es"
            else -> ""
        }
    }

    private fun fetchPrayer(city: City) {
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Calendar.getInstance().time)
        val json = Net.get(
            "https://api.aladhan.com/v1/timings/$today?latitude=${city.lat}&longitude=${city.lon}&method=20"
        ) ?: return
        try {
            val timings = JSONObject(json).getJSONObject("data").getJSONObject("timings")
            fun clean(key: String): String {
                val raw = timings.optString(key)
                val m = Regex("""(\d{2}:\d{2})""").find(raw)
                return m?.value ?: raw
            }
            val en = Prefs.isEnglish(this)
            val entries = listOf(
                (if (en) "Fajr" else "Subuh") to clean("Fajr"),
                (if (en) "Dhuhr" else "Dzuhur") to clean("Dhuhr"),
                (if (en) "Asr" else "Ashar") to clean("Asr"),
                (if (en) "Maghrib" else "Maghrib") to clean("Maghrib"),
                (if (en) "Isha" else "Isya") to clean("Isha")
            ).filter { it.second.isNotBlank() }
            if (entries.isEmpty()) return
            val nowMin = Calendar.getInstance().let {
                it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
            }
            fun toMin(hhmm: String): Int {
                val p = hhmm.split(":")
                return (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
            }
            var nextName = entries[0].first
            var nextTime = entries[0].second
            var diff = toMin(entries[0].second) + 24 * 60 - nowMin
            for ((name, time) in entries) {
                val d = toMin(time) - nowMin
                if (d >= 0 && d < diff) {
                    diff = d
                    nextName = name
                    nextTime = time
                }
            }
            if (diff > 24 * 60) diff -= 24 * 60
            val remaining = if (en) "${diff / 60} h ${diff % 60} m" else "${diff / 60} j ${diff % 60} m"
            val all = entries.joinToString("  •  ") { "${it.first} ${it.second}" }
            uiHandler.post {
                prayerNext.text = getString(R.string.next_prayer, nextName, nextTime, remaining)
                prayerAll.text = all
            }
        } catch (e: Exception) {
            uiHandler.post { prayerNext.text = getString(R.string.no_data) }
        }
    }

    private fun fetchQuake() {
        val json = Net.get(QuakeService.AUTOGEMPA_URL)
        val quake = json?.let { QuakeParser.parseLatest(it) }
        uiHandler.post {
            if (quake != null) {
                quakeMain.text = getString(
                    R.string.quake_line,
                    OverlayPopup.formatMag(quake.magnitude), quake.wilayah
                )
                quakeSub.text = getString(R.string.quake_depth, quake.kedalaman, quake.jam)
                val felt = quake.dirasakan.ifBlank { quake.potensi }
                quakeFelt.text = felt
                quakeFelt.visibility = if (felt.isBlank()) View.GONE else View.VISIBLE
            } else {
                quakeMain.text = getString(R.string.no_data)
                quakeSub.text = ""
                quakeFelt.text = ""
                quakeFelt.visibility = View.GONE
            }
        }
    }

    /** Best-effort scrape of the public MAGMA VONA listing; any failure just shows "unavailable". */
    private fun fetchVolcano() {
        val html = Net.get("https://magma.esdm.go.id/v1/vona", 20000)
        val text = html?.let { parseVona(it) }
        uiHandler.post {
            volcanoText.text = text ?: getString(R.string.volcano_unavailable)
        }
    }

    private fun parseVona(html: String): String? {
        return try {
            val entry = Regex("""([A-Z][A-Za-z .]{2,30}?)\s*-\s*(\d{8})/(\d{4})Z""").find(html) ?: return null
            val name = entry.groupValues[1].trim()
            val window = html.substring(entry.range.first, minOf(html.length, entry.range.first + 2500))
            val color = Regex("""(?i)\b(Red|Orange|Yellow|Green)\b""").find(window)?.value ?: ""
            val ash = Regex("""(?i)ash cloud moving to ([a-z]+)""").find(window)?.groupValues?.get(1) ?: ""
            val en = Prefs.isEnglish(this)
            buildString {
                append(name)
                if (color.isNotBlank()) append(" • ").append(color.uppercase())
                if (ash.isNotBlank()) {
                    append(if (en) " • ash to " else " • abu ke ").append(ash)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
