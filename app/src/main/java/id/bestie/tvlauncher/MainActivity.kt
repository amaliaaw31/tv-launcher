package id.bestie.tvlauncher

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.TransitionDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

class MainActivity : LocalizedActivity() {

    private val uiHandler = Handler(Looper.getMainLooper())

    // v1.9 (point 10): multi-thread pool — each data source is submitted
    // as its own task, so a slow source never blocks the others (the old
    // single-thread chain ran weather -> prayer -> quake -> volcano
    // strictly in sequence).
    private val executor = Executors.newFixedThreadPool(4)

    private lateinit var greetingText: TextView
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var weatherMain: TextView
    private lateinit var weatherCity: TextView
    private lateinit var weatherAqi: TextView
    private lateinit var weatherStatus: TextView
    private lateinit var prayerNext: TextView
    private lateinit var prayerAll: TextView
    private lateinit var prayerStatus: TextView
    private lateinit var quakeMain: TextView
    private lateinit var quakeSub: TextView
    private lateinit var quakeStatus: TextView
    private lateinit var volcanoText: TextView
    private lateinit var volcanoStatus: TextView
    private lateinit var weatherCard: LinearLayout
    private lateinit var prayerCard: LinearLayout
    private lateinit var quakeCard: LinearLayout
    private lateinit var volcanoCard: LinearLayout
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

    // ------------------------------------------------------------ v1.9 state

    /** Per-source load state (point 4): tracked independently per source. */
    private enum class LoadState { LOADING, OK, ERROR }

    private var weatherState = LoadState.LOADING
    private var airState = LoadState.LOADING
    private var prayerState = LoadState.LOADING
    private var quakeState = LoadState.LOADING
    private var volcanoState = LoadState.LOADING
    private var weatherOkMs = 0L
    private var airOkMs = 0L
    private var prayerOkMs = 0L
    private var quakeOkMs = 0L
    private var volcanoOkMs = 0L

    /** Today's prayer entries (name -> "HH:mm"), for the countdown tick (point 6). */
    private var prayerEntriesMem: List<Pair<String, String>> = emptyList()

    // Dock: signature of the resolved package list (point 8) and tiles by
    // package, for focus restore.
    private var dockSignature: String? = null
    private val dockTiles = HashMap<String, View>()
    private var lastFocusedPkg: String? = null

    companion object {
        private val STATUS_GRAY: Int = Color.parseColor("#B0BEC5")
        private val STATUS_AMBER: Int = Color.parseColor("#FFC107")

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

    /**
     * v1.9 (point 6): countdown tick, fully decoupled from the network.
     * Recomputes the next prayer + remaining time from the stored entries
     * every 30 s (the display granularity is minutes, so the shown value
     * is never more than a minute stale — unlike before, when it was
     * computed once per 15-minute fetch).
     */
    private val prayerTickRunnable = object : Runnable {
        override fun run() {
            updatePrayerCountdown()
            uiHandler.postDelayed(this, 30_000)
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
        weatherStatus = findViewById(R.id.weatherStatus)
        prayerNext = findViewById(R.id.prayerNext)
        prayerAll = findViewById(R.id.prayerAll)
        prayerStatus = findViewById(R.id.prayerStatus)
        quakeMain = findViewById(R.id.quakeMain)
        quakeSub = findViewById(R.id.quakeSub)
        quakeStatus = findViewById(R.id.quakeStatus)
        volcanoText = findViewById(R.id.volcanoText)
        volcanoStatus = findViewById(R.id.volcanoStatus)
        weatherCard = findViewById(R.id.weatherCard)
        prayerCard = findViewById(R.id.prayerCard)
        quakeCard = findViewById(R.id.quakeCard)
        volcanoCard = findViewById(R.id.volcanoCard)
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

        // Card actions (point 4): OK refetches ONLY that card's source;
        // OK on the quake card opens the native detail dialog (point 2).
        // Focus swaps the card background to the teal-bordered variant.
        weatherCard.setOnClickListener {
            val city = Prefs.city(this)
            executor.execute { fetchWeather(city) }
            executor.execute { fetchAir(city) }
        }
        prayerCard.setOnClickListener {
            val city = Prefs.city(this)
            executor.execute { fetchPrayer(city) }
        }
        quakeCard.setOnClickListener {
            showQuakeDetailDialog()
        }
        volcanoCard.setOnClickListener {
            executor.execute { fetchVolcano() }
        }
        for (card in listOf(weatherCard, prayerCard, quakeCard, volcanoCard)) {
            card.setOnFocusChangeListener { v, hasFocus ->
                v.setBackgroundResource(
                    if (hasFocus) R.drawable.glass_card_focused else R.drawable.glass_card
                )
            }
        }

        // Show the persisted last quake immediately (if any) so the card
        // starts from last-good data instead of a spinner.
        if (Prefs.quakeUpdatedAt(this) > 0) {
            quakeOkMs = Prefs.quakeUpdatedAt(this)
            quakeState = LoadState.OK
            renderQuakeCard()
        }
        renderAllStatuses()

        buildDock()
        QuakeService.start(this)
    }

    override fun onResume() {
        super.onResume()
        uiHandler.removeCallbacks(clockRunnable)
        uiHandler.post(clockRunnable)
        uiHandler.removeCallbacks(prayerTickRunnable)
        uiHandler.post(prayerTickRunnable)
        if (System.currentTimeMillis() - lastFetchMs > 60_000) {
            uiHandler.removeCallbacks(refreshRunnable)
            uiHandler.post(refreshRunnable)
        }
        // If a newer quake record was persisted while we were away,
        // adopt it as the card's last-good data.
        val persistedQuakeMs = Prefs.quakeUpdatedAt(this)
        if (persistedQuakeMs > quakeOkMs) {
            quakeOkMs = persistedQuakeMs
            quakeState = LoadState.OK
            renderQuakeCard()
            renderQuakeStatus()
        }
        buildDock()
        restoreDockFocus()
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(clockRunnable)
        uiHandler.removeCallbacks(prayerTickRunnable)
    }

    // ---------------------------------------------------------------- clock

    private fun updateClock() {
        // The big clock + greeting stay on DEVICE time (point 13 changes
        // only the prayer math, which uses the selected city's timezone).
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

    /**
     * v1.9 changes (points 8 + 9):
     *  - The resolved package list is signed; when the signature is
     *    unchanged the dock is NOT rebuilt on resume, so D-pad focus is
     *    no longer destroyed every time the user comes back.
     *  - Tiles are ordered by use: launch count desc, then last-launch
     *    desc, then label A-Z. Never-launched apps (count 0) sort after
     *    used ones, alphabetically.
     *  - Clicking a tile records the launch in Prefs; focus restore
     *    targets the last launched app's tile (see [restoreDockFocus]).
     */
    private fun buildDock() {
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

        val signature = apps.map { it.first.activityInfo.packageName }.sorted().joinToString(",")
        if (signature == dockSignature && dockRow.childCount > 0) {
            return // unchanged app set: keep views (and focus) as they are
        }
        dockSignature = signature

        val ordered = apps.sortedWith(
            compareByDescending<Pair<ResolveInfo, String>> {
                Prefs.launchCount(this, it.first.activityInfo.packageName)
            }.thenByDescending {
                Prefs.launchLast(this, it.first.activityInfo.packageName)
            }.thenBy { it.second.lowercase() }
        )

        dockRow.removeAllViews()
        dockTiles.clear()
        val inflater = LayoutInflater.from(this)
        for ((ri, name) in ordered) {
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
                // Record the launch BEFORE leaving (points 8 + 9).
                Prefs.recordLaunch(this, pkg)
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
                if (hasFocus) lastFocusedPkg = pkg
            }
            dockTiles[pkg] = item
            dockRow.addView(item)
        }
    }

    /**
     * Point 8: after returning to the launcher, if nothing holds focus,
     * put it back on the tile of the last launched app (falling back to
     * the last focused tile, then the first tile).
     */
    private fun restoreDockFocus() {
        dockRow.post {
            if (currentFocus != null) return@post
            val target = dockTiles[Prefs.lastLaunchedPackage(this)]
                ?: lastFocusedPkg?.let { dockTiles[it] }
                ?: dockRow.getChildAt(0)
            target?.requestFocus()
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

    /**
     * v1.9 (point 10): every source is an INDEPENDENT task on the pool —
     * weather, air quality, prayer, quake and volcano load in parallel,
     * so one slow source can no longer delay the others.
     */
    private fun refreshData() {
        lastFetchMs = System.currentTimeMillis()
        val city = Prefs.city(this)
        executor.execute { fetchWeather(city) }
        executor.execute { fetchAir(city) }
        executor.execute { fetchPrayer(city) }
        executor.execute { fetchQuake() }
        executor.execute { fetchVolcano() }
    }

    // ------------------------------------------------------- status (point 4)

    private fun hhmm(ms: Long, tz: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = tz }.format(Date(ms))

    private fun renderAllStatuses() {
        renderWeatherStatus()
        renderPrayerStatus()
        renderQuakeStatus()
        renderVolcanoStatus()
    }

    /** Weather card status combines the weather + air states (one card). */
    private fun renderWeatherStatus() {
        val failed = weatherState == LoadState.ERROR || airState == LoadState.ERROR
        when {
            failed -> {
                weatherStatus.setTextColor(STATUS_AMBER)
                val lastOk = maxOf(
                    if (weatherState == LoadState.ERROR) weatherOkMs else 0L,
                    if (airState == LoadState.ERROR) airOkMs else 0L
                )
                weatherStatus.text = if (lastOk > 0) {
                    getString(R.string.status_failed_last, hhmm(lastOk))
                } else {
                    getString(R.string.status_failed)
                }
            }
            weatherState == LoadState.OK && airState == LoadState.OK -> {
                weatherStatus.setTextColor(STATUS_GRAY)
                weatherStatus.text =
                    getString(R.string.status_updated, hhmm(maxOf(weatherOkMs, airOkMs)))
            }
            else -> {
                weatherStatus.setTextColor(STATUS_GRAY)
                weatherStatus.text = getString(R.string.loading)
            }
        }
    }

    private fun renderPrayerStatus() {
        when (prayerState) {
            LoadState.OK -> {
                prayerStatus.setTextColor(STATUS_GRAY)
                prayerStatus.text =
                    getString(R.string.status_updated, hhmm(prayerOkMs, cityTimeZone()))
            }
            LoadState.ERROR -> {
                prayerStatus.setTextColor(STATUS_AMBER)
                prayerStatus.text = if (prayerOkMs > 0) {
                    getString(R.string.status_failed_last, hhmm(prayerOkMs, cityTimeZone()))
                } else {
                    getString(R.string.status_failed)
                }
            }
            LoadState.LOADING -> {
                prayerStatus.setTextColor(STATUS_GRAY)
                prayerStatus.text = getString(R.string.loading)
            }
        }
    }

    private fun renderQuakeStatus() {
        when (quakeState) {
            LoadState.OK -> {
                quakeStatus.setTextColor(STATUS_GRAY)
                quakeStatus.text = getString(R.string.status_updated, hhmm(quakeOkMs))
            }
            LoadState.ERROR -> {
                quakeStatus.setTextColor(STATUS_AMBER)
                quakeStatus.text = if (quakeOkMs > 0) {
                    getString(R.string.status_failed_last, hhmm(quakeOkMs))
                } else {
                    getString(R.string.status_failed)
                }
            }
            LoadState.LOADING -> {
                quakeStatus.setTextColor(STATUS_GRAY)
                quakeStatus.text = getString(R.string.loading)
            }
        }
    }

    private fun renderVolcanoStatus() {
        when (volcanoState) {
            LoadState.OK -> {
                volcanoStatus.setTextColor(STATUS_GRAY)
                volcanoStatus.text = getString(R.string.status_updated, hhmm(volcanoOkMs))
            }
            LoadState.ERROR -> {
                volcanoStatus.setTextColor(STATUS_AMBER)
                volcanoStatus.text = if (volcanoOkMs > 0) {
                    getString(R.string.status_failed_last, hhmm(volcanoOkMs))
                } else {
                    getString(R.string.status_failed)
                }
            }
            LoadState.LOADING -> {
                volcanoStatus.setTextColor(STATUS_GRAY)
                volcanoStatus.text = getString(R.string.loading)
            }
        }
    }

    // ------------------------------------------------------------ timezone

    /**
     * Point 13: the SELECTED city's timezone, learned from the Open-Meteo
     * response (see fetchWeather). Falls back to the device timezone when
     * unknown or unparseable.
     */
    private fun cityTimeZone(): TimeZone {
        val name = Prefs.cityTimezone(this)
        if (name.isBlank()) return TimeZone.getDefault()
        val tz = TimeZone.getTimeZone(name)
        // getTimeZone() silently returns GMT for unknown IDs; treat that
        // as "unknown" unless the API really said GMT.
        return if (tz.id == "GMT" && name != "GMT" && !name.startsWith("GMT")) {
            TimeZone.getDefault()
        } else {
            tz
        }
    }

    // -------------------------------------------------------------- weather

    private data class ForecastDay(val date: String, val code: Int, val max: Double, val min: Double)

    /** Forecast + current conditions only; air quality is fetchAir (point 10). */
    private fun fetchWeather(city: City) {
        val forecastJson = Net.get(
            "https://api.open-meteo.com/v1/forecast?latitude=${city.lat}&longitude=${city.lon}" +
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=4&timezone=auto"
        )
        var temp: Double? = null
        var code = -1
        var isDay = 1
        var tzName = ""
        val forecastDays = mutableListOf<ForecastDay>()
        try {
            if (forecastJson != null) {
                val root = JSONObject(forecastJson)
                // Point 13: the city's IANA timezone comes with the response.
                tzName = root.optString("timezone")
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
        val tempFinal = temp
        val codeFinal = code
        val isDayFinal = isDay
        if (tzName.isNotBlank()) {
            Prefs.setCityTimezone(this, Cities.list.indexOf(city), tzName)
        }
        if (tempFinal != null && !tempFinal.isNaN() && forecastJson != null) {
            weatherState = LoadState.OK
            weatherOkMs = System.currentTimeMillis()
            uiHandler.post {
                weatherMain.text = "${tempFinal.toInt()}°  ${weatherText(codeFinal)}"
                weatherCity.text = city.name
                lastWeatherCode = codeFinal
                lastIsDay = isDayFinal
                applyWallpaperVariant(codeFinal, isDayFinal)
                buildForecast(forecastDays)
                renderWeatherStatus()
            }
        } else {
            // Point 4: no silent failure — keep last good data on screen,
            // flip the state so the status line explains what happened.
            weatherState = LoadState.ERROR
            uiHandler.post {
                if (weatherOkMs == 0L) weatherMain.text = getString(R.string.data_failed)
                renderWeatherStatus()
            }
        }
    }

    /** Air quality (AQI + PM2.5) as its own source/task (points 4 + 10). */
    private fun fetchAir(city: City) {
        val airJson = Net.get(
            "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${city.lat}&longitude=${city.lon}" +
                "&current=us_aqi,pm2_5&timezone=auto"
        )
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
        val aqiFinal = aqi
        val pmFinal = pm25
        if (aqiFinal != null) {
            airState = LoadState.OK
            airOkMs = System.currentTimeMillis()
            uiHandler.post {
                weatherAqi.text = getString(
                    R.string.air_quality, aqiFinal,
                    pmFinal?.let { String.format(Locale.US, "%.0f", it) } ?: "-"
                )
                renderWeatherStatus()
            }
        } else {
            airState = LoadState.ERROR
            uiHandler.post { renderWeatherStatus() }
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
            // Point 12: the condition name for this chip's weather code,
            // reused from the same mapping as the weather text. The icon
            // gets it as its contentDescription and the whole chip reads
            // "{day}, {condition}, {max}° / {min}°" to screen readers —
            // previously the icon description was null, so only the
            // temperature text was announced.
            val condition = weatherText(day.code)
            chip.contentDescription = String.format(
                Locale.US, "%s, %s, %d° / %d°",
                dayLabel, condition, Math.round(day.max), Math.round(day.min)
            )
            chip.addView(TextView(this).apply {
                text = dayLabel
                textSize = 16f
                setTextColor(Color.parseColor("#B0BEC5"))
                gravity = android.view.Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            chip.addView(ImageView(this).apply {
                setImageResource(forecastIconFor(day.code))
                contentDescription = condition
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
                    .apply { topMargin = dp(3) }
            })
            chip.addView(TextView(this).apply {
                text = String.format(
                    Locale.US, "%d°/%d°",
                    Math.round(day.max), Math.round(day.min)
                )
                textSize = 16f
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
     *
     * v1.9 (point 7): when the variant CHANGES, the new scene crossfades
     * over the old one in ~1.5 s via a TransitionDrawable; an unchanged
     * variant sets nothing (no animation, no flicker).
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
            val firstPick = lastSceneRes == 0
            lastSceneRes = scene
            // Hand the picked scene to the Settings screen (v1.8), which
            // uses the same photo as its background.
            Prefs.setLastSceneName(this, resources.getResourceEntryName(scene))
            if (firstPick) {
                wallpaperView.setImageResource(scene)
            } else {
                val old = wallpaperView.drawable
                val newDrawable = resources.getDrawable(scene, theme)
                if (old == null) {
                    wallpaperView.setImageDrawable(newDrawable)
                } else {
                    // Flatten a previous transition to its final layer so
                    // transitions never nest deeper than two layers.
                    val base = if (old is TransitionDrawable && old.numberOfLayers > 0) {
                        old.getDrawable(old.numberOfLayers - 1)
                    } else {
                        old
                    }
                    val transition = TransitionDrawable(arrayOf(base, newDrawable))
                    transition.isCrossFadeEnabled = true
                    wallpaperView.setImageDrawable(transition)
                    transition.startTransition(1500)
                }
            }
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

    // --------------------------------------------------------------- prayer

    /**
     * v1.9 (points 4, 6, 13): the Aladhan request date is formatted in
     * the CITY's timezone, and a successful fetch only stores the day's
     * entries — the next/countdown computation happens in
     * [updatePrayerCountdown] (per-minute tick, same city tz). A network
     * failure sets the ERROR state instead of silently returning.
     */
    private fun fetchPrayer(city: City) {
        val tz = cityTimeZone()
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.US)
            .apply { timeZone = tz }
            .format(Calendar.getInstance(tz).time)
        val json = Net.get(
            "https://api.aladhan.com/v1/timings/$today?latitude=${city.lat}&longitude=${city.lon}&method=20"
        )
        if (json == null) {
            onPrayerFailed()
            return
        }
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
            if (entries.isEmpty()) {
                onPrayerFailed()
                return
            }
            val cityDateIso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .apply { timeZone = tz }
                .format(Calendar.getInstance(tz).time)
            prayerEntriesMem = entries
            Prefs.savePrayerEntries(this, entries, cityDateIso)
            prayerState = LoadState.OK
            prayerOkMs = System.currentTimeMillis()
            uiHandler.post {
                updatePrayerCountdown()
                renderPrayerStatus()
            }
        } catch (e: Exception) {
            onPrayerFailed()
        }
    }

    private fun onPrayerFailed() {
        prayerState = LoadState.ERROR
        uiHandler.post {
            if (prayerEntriesMem.isEmpty() && Prefs.prayerEntries(this).isEmpty()) {
                prayerNext.text = getString(R.string.data_failed)
            }
            renderPrayerStatus()
        }
    }

    /**
     * Recomputes "next prayer + remaining" from the stored entries using
     * the city timezone (points 6 + 13). UI thread only. When every time
     * today has passed, the first entry is next (tomorrow) — the diff
     * wraps by +24 h as before.
     */
    private fun updatePrayerCountdown() {
        if (prayerEntriesMem.isEmpty()) {
            prayerEntriesMem = Prefs.prayerEntries(this)
        }
        val entries = prayerEntriesMem
        if (entries.isEmpty()) return
        val tz = cityTimeZone()
        val nowCal = Calendar.getInstance(tz)
        val nowMin = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
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
        val en = Prefs.isEnglish(this)
        val remaining = if (en) "${diff / 60} h ${diff % 60} m" else "${diff / 60} j ${diff % 60} m"
        // v2.0 (point 2): short next-prayer format ("Dzuhur 11:36 •
        // 5 j 14 m") so the bold line renders on ONE line — the v1.9
        // "Berikutnya: … • … lagi" wrapped and stranded "lagi".
        prayerNext.text = getString(R.string.next_prayer, nextName, nextTime, remaining)
        // All-times on TWO clear lines (Subuh • Dzuhur / Ashar •
        // Maghrib • Isya) — no ellipsis truncation of any time.
        prayerAll.text = if (entries.size >= 5) {
            entries.take(2).joinToString(" • ") { "${it.first} ${it.second}" } +
                "\n" + entries.drop(2).joinToString(" • ") { "${it.first} ${it.second}" }
        } else {
            entries.joinToString(" • ") { "${it.first} ${it.second}" }
        }
    }

    // ---------------------------------------------------------------- quake

    private fun fetchQuake() {
        val json = Net.get(QuakeService.AUTOGEMPA_URL)
        val quake = json?.let { QuakeParser.parseLatest(it) }
        if (quake != null) {
            val now = System.currentTimeMillis()
            // Persist the full record for the card + detail dialog.
            Prefs.saveQuake(
                this, quake.magnitude, quake.wilayah, quake.tanggal, quake.jam,
                quake.dateTime, quake.kedalaman, quake.koordinat, quake.dirasakan,
                quake.potensi, now
            )
            quakeState = LoadState.OK
            quakeOkMs = now
            uiHandler.post {
                renderQuakeCard()
                renderQuakeStatus()
            }
        } else {
            // Point 4: keep the last good data (Prefs + card untouched);
            // only the state flips so the status line turns amber.
            quakeState = LoadState.ERROR
            uiHandler.post {
                if (Prefs.quakeUpdatedAt(this) == 0L) {
                    quakeMain.text = getString(R.string.data_failed)
                    quakeSub.text = ""
                }
                renderQuakeStatus()
            }
        }
    }

    /** Renders the home quake summary from the persisted record. */
    private fun renderQuakeCard() {
        if (Prefs.quakeUpdatedAt(this) == 0L) return
        quakeMain.text = getString(
            R.string.quake_summary,
            OverlayPopup.formatMag(Prefs.quakeMag(this)), shortWilayah(Prefs.quakeRegion(this))
        )
        quakeSub.text = getString(
            R.string.quake_summary_sub,
            Prefs.quakeTanggal(this), Prefs.quakeJam(this), Prefs.quakeDepth(this)
        )
    }

    /**
     * v2.0 (point 2): short location for the home card. BMKG Wilayah
     * strings look like "Pusat gempa berada di darat 7 km barat daya
     * Gunungkidul, DIY" — the card keeps only the place segment after
     * the distance/direction boilerplate. Deterministic: strips the
     * standard prefixes, then drops a leading "<n> km <direction>"
     * part if present. Anything unrecognized falls back to the full
     * Wilayah, trimmed and capitalized.
     */
    private fun shortWilayah(wilayah: String): String {
        var s = wilayah.trim()
        if (s.isEmpty()) return s
        for (prefix in listOf(
            "Pusat gempa berada di darat", "Pusat gempa berada di laut",
            "Pusat gempa terletak di darat", "Pusat gempa terletak di laut"
        )) {
            if (s.startsWith(prefix, ignoreCase = true)) {
                s = s.substring(prefix.length).trim()
                break
            }
        }
        // "7 km barat daya X" / "12 km tenggara X" -> "X"
        val m = Regex("""^\d+(?:[.,]\d+)?\s*km\s+\S+(?:\s+\S+)?\s+""", RegexOption.IGNORE_CASE).find(s)
        if (m != null) {
            val rest = s.substring(m.range.last + 1).trim()
            if (rest.isNotEmpty()) s = rest
        }
        return s.replaceFirstChar { it.uppercase() }
    }

    /**
     * v2.0 (point 2): complete quake detail in a native AlertDialog
     * (replaces the v1.9 QuakeDetailActivity). Body = ScrollView +
     * TextView with the COMPLETE unabridged record; D-pad scrolls it;
     * Back or Tutup dismisses and focus returns to the quake card.
     */
    private fun showQuakeDetailDialog() {
        if (Prefs.quakeUpdatedAt(this) == 0L) return
        fun field(labelRes: Int, value: String): String =
            getString(labelRes) + ": " + value.ifBlank { "—" }
        val tanggal = Prefs.quakeTanggal(this)
        val jam = Prefs.quakeJam(this)
        val timeText = listOf(tanggal, jam).filter { it.isNotBlank() }
            .joinToString(" • ").ifBlank { Prefs.quakeDateTime(this) }
        val body = listOf(
            field(R.string.quake_mag_label, "M " + OverlayPopup.formatMag(Prefs.quakeMag(this))),
            field(R.string.quake_region_label, Prefs.quakeRegion(this)),
            field(R.string.quake_time_label, timeText),
            field(R.string.quake_depth_label, Prefs.quakeDepth(this)),
            field(R.string.quake_coords_label, Prefs.quakeCoords(this)),
            field(R.string.quake_felt_label, Prefs.quakeFelt(this)),
            field(R.string.quake_tsunami_label, Prefs.quakePotensi(this)),
            getString(R.string.quake_source, hhmm(Prefs.quakeUpdatedAt(this)))
        ).joinToString("\n")
        val pad = (16 * resources.displayMetrics.density).toInt()
        val textView = TextView(this).apply {
            text = body
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(pad, pad / 2, pad, pad / 2)
            isFocusable = true
        }
        val scroll = ScrollView(this).apply {
            addView(textView)
            isFocusable = true
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.quake_detail_title)
            .setView(scroll)
            .setPositiveButton(R.string.close, null)
            .create()
        dialog.setOnDismissListener { quakeCard.requestFocus() }
        dialog.setOnShowListener { scroll.requestFocus() }
        dialog.show()
    }

    // -------------------------------------------------------------- volcano

    /** Best-effort scrape of the public MAGMA VONA listing. */
    private fun fetchVolcano() {
        val html = Net.get("https://magma.esdm.go.id/v1/vona", 20000)
        val text = html?.let { parseVona(it) }
        if (text != null) {
            volcanoState = LoadState.OK
            volcanoOkMs = System.currentTimeMillis()
            uiHandler.post {
                volcanoText.text = text
                renderVolcanoStatus()
            }
        } else {
            volcanoState = LoadState.ERROR
            uiHandler.post {
                if (volcanoOkMs == 0L) volcanoText.text = getString(R.string.data_failed)
                renderVolcanoStatus()
            }
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
