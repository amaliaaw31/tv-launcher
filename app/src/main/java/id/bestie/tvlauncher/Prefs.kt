package id.bestie.tvlauncher

import android.content.Context
import android.content.SharedPreferences

data class City(val name: String, val lat: Double, val lon: Double)

object Cities {
    val list = listOf(
        City("Jakarta", -6.2088, 106.8456),
        City("Bandung", -6.9175, 107.6191),
        City("Surabaya", -7.2575, 112.7521),
        City("Yogyakarta", -7.7956, 110.3695),
        City("Semarang", -6.9667, 110.4167),
        City("Medan", 3.5952, 98.6722),
        City("Makassar", -5.1477, 119.4327),
        City("Denpasar", -8.6705, 115.2126),
        City("Balikpapan", -1.2379, 116.8529),
        City("Tanah Grogot", -1.9083, 116.1808)
    )
}

object Prefs {
    private const val FILE = "launcher_prefs"
    private const val KEY_CITY = "city_index"
    private const val KEY_LANG = "language"
    private const val KEY_THRESHOLD = "threshold"
    private const val KEY_SOUND = "sound"
    private const val KEY_LAST_QUAKE = "last_quake_datetime"
    private const val KEY_LAST_SCENE = "last_scene_name"
    private const val KEY_CITY_TZ_PREFIX = "city_tz_"
    private const val KEY_PRAYER_ENTRIES = "prayer_entries"
    private const val KEY_PRAYER_DATE = "prayer_entries_date"
    private const val KEY_LAUNCH_COUNT_PREFIX = "launch_count_"
    private const val KEY_LAUNCH_LAST_PREFIX = "launch_last_"
    private const val KEY_LAST_LAUNCHED_PKG = "last_launched_pkg"
    private const val KEY_Q_MAG = "quake_detail_mag"
    private const val KEY_Q_REGION = "quake_detail_region"
    private const val KEY_Q_TANGGAL = "quake_detail_tanggal"
    private const val KEY_Q_JAM = "quake_detail_jam"
    private const val KEY_Q_DATETIME = "quake_detail_datetime"
    private const val KEY_Q_DEPTH = "quake_detail_depth"
    private const val KEY_Q_COORDS = "quake_detail_coords"
    private const val KEY_Q_FELT = "quake_detail_felt"
    private const val KEY_Q_POTENSI = "quake_detail_potensi"
    private const val KEY_Q_UPDATED = "quake_detail_updated_at"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun cityIndex(ctx: Context): Int = sp(ctx).getInt(KEY_CITY, 0).coerceIn(0, Cities.list.size - 1)
    fun setCityIndex(ctx: Context, value: Int) = sp(ctx).edit().putInt(KEY_CITY, value).apply()
    fun city(ctx: Context): City = Cities.list[cityIndex(ctx)]

    /** "in" (Indonesia) or "en" (English). */
    fun language(ctx: Context): String = sp(ctx).getString(KEY_LANG, "in") ?: "in"
    fun setLanguage(ctx: Context, value: String) = sp(ctx).edit().putString(KEY_LANG, value).apply()
    fun isEnglish(ctx: Context): Boolean = language(ctx) == "en"

    fun threshold(ctx: Context): Double = sp(ctx).getString(KEY_THRESHOLD, "5.0")?.toDoubleOrNull() ?: 5.0
    fun setThreshold(ctx: Context, value: Double) =
        sp(ctx).edit().putString(KEY_THRESHOLD, value.toString()).apply()

    fun sound(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_SOUND, true)
    fun setSound(ctx: Context, value: Boolean) = sp(ctx).edit().putBoolean(KEY_SOUND, value).apply()

    fun lastQuakeDateTime(ctx: Context): String = sp(ctx).getString(KEY_LAST_QUAKE, "") ?: ""
    fun setLastQuakeDateTime(ctx: Context, value: String) =
        sp(ctx).edit().putString(KEY_LAST_QUAKE, value).apply()

    /**
     * Drawable entry name of the wallpaper scene the home screen last
     * picked (e.g. "bandung_hujan"). The home screen's selection logic
     * (city set + weather/night variant) writes it; the Settings screen
     * reads it so its background is the very same photo (v1.8).
     */
    fun lastSceneName(ctx: Context): String = sp(ctx).getString(KEY_LAST_SCENE, "") ?: ""
    fun setLastSceneName(ctx: Context, value: String) =
        sp(ctx).edit().putString(KEY_LAST_SCENE, value).apply()

    // ------------------------------------------------------------ v1.9

    /**
     * IANA timezone of a city (e.g. "Asia/Makassar"), learned from the
     * Open-Meteo forecast response (review point 13). Prayer date and
     * countdown math must run in the SELECTED city's timezone, not the
     * device's. Empty when not learned yet -> callers fall back to the
     * device timezone.
     */
    fun cityTimezone(ctx: Context, index: Int = cityIndex(ctx)): String =
        sp(ctx).getString(KEY_CITY_TZ_PREFIX + index, "") ?: ""

    fun setCityTimezone(ctx: Context, index: Int, tz: String) =
        sp(ctx).edit().putString(KEY_CITY_TZ_PREFIX + index, tz).apply()

    /**
     * Today's prayer entries ("Name|HH:mm" pairs joined by ";") plus the
     * city-local ISO date (yyyy-MM-dd) they belong to, so the per-minute
     * countdown tick can recompute without the network (point 6).
     */
    fun savePrayerEntries(ctx: Context, entries: List<Pair<String, String>>, cityDateIso: String) {
        val raw = entries.joinToString(";") { "${it.first}|${it.second}" }
        sp(ctx).edit()
            .putString(KEY_PRAYER_ENTRIES, raw)
            .putString(KEY_PRAYER_DATE, cityDateIso)
            .apply()
    }

    fun prayerEntries(ctx: Context): List<Pair<String, String>> {
        val raw = sp(ctx).getString(KEY_PRAYER_ENTRIES, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { part ->
            val i = part.indexOf('|')
            if (i <= 0) null else part.substring(0, i) to part.substring(i + 1)
        }
    }

    fun prayerEntriesDate(ctx: Context): String =
        sp(ctx).getString(KEY_PRAYER_DATE, "") ?: ""

    /** Dock usage stats (point 9) + last launched package (point 8). */
    fun recordLaunch(ctx: Context, pkg: String) {
        val e = sp(ctx).edit()
        e.putInt(KEY_LAUNCH_COUNT_PREFIX + pkg, launchCount(ctx, pkg) + 1)
        e.putLong(KEY_LAUNCH_LAST_PREFIX + pkg, System.currentTimeMillis())
        e.putString(KEY_LAST_LAUNCHED_PKG, pkg)
        e.apply()
    }

    fun launchCount(ctx: Context, pkg: String): Int =
        sp(ctx).getInt(KEY_LAUNCH_COUNT_PREFIX + pkg, 0)

    fun launchLast(ctx: Context, pkg: String): Long =
        sp(ctx).getLong(KEY_LAUNCH_LAST_PREFIX + pkg, 0L)

    fun lastLaunchedPackage(ctx: Context): String =
        sp(ctx).getString(KEY_LAST_LAUNCHED_PKG, "") ?: ""

    /**
     * Full record of the last successfully fetched BMKG quake (point 3),
     * read by QuakeDetailActivity and used to keep the home card's last
     * good data on screen when a later fetch fails (point 4).
     */
    fun saveQuake(
        ctx: Context, mag: Double, region: String, tanggal: String, jam: String,
        dateTime: String, depth: String, coords: String, felt: String,
        potensi: String, updatedAtMs: Long
    ) {
        sp(ctx).edit()
            .putFloat(KEY_Q_MAG, mag.toFloat())
            .putString(KEY_Q_REGION, region)
            .putString(KEY_Q_TANGGAL, tanggal)
            .putString(KEY_Q_JAM, jam)
            .putString(KEY_Q_DATETIME, dateTime)
            .putString(KEY_Q_DEPTH, depth)
            .putString(KEY_Q_COORDS, coords)
            .putString(KEY_Q_FELT, felt)
            .putString(KEY_Q_POTENSI, potensi)
            .putLong(KEY_Q_UPDATED, updatedAtMs)
            .apply()
    }

    fun quakeMag(ctx: Context): Double = sp(ctx).getFloat(KEY_Q_MAG, 0f).toDouble()
    fun quakeRegion(ctx: Context): String = sp(ctx).getString(KEY_Q_REGION, "") ?: ""
    fun quakeTanggal(ctx: Context): String = sp(ctx).getString(KEY_Q_TANGGAL, "") ?: ""
    fun quakeJam(ctx: Context): String = sp(ctx).getString(KEY_Q_JAM, "") ?: ""
    fun quakeDateTime(ctx: Context): String = sp(ctx).getString(KEY_Q_DATETIME, "") ?: ""
    fun quakeDepth(ctx: Context): String = sp(ctx).getString(KEY_Q_DEPTH, "") ?: ""
    fun quakeCoords(ctx: Context): String = sp(ctx).getString(KEY_Q_COORDS, "") ?: ""
    fun quakeFelt(ctx: Context): String = sp(ctx).getString(KEY_Q_FELT, "") ?: ""
    fun quakePotensi(ctx: Context): String = sp(ctx).getString(KEY_Q_POTENSI, "") ?: ""
    fun quakeUpdatedAt(ctx: Context): Long = sp(ctx).getLong(KEY_Q_UPDATED, 0L)
}
