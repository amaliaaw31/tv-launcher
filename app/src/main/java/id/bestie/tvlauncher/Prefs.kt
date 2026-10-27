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
}
