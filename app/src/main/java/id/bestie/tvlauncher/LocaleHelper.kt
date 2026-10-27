package id.bestie.tvlauncher

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {
    fun localeFor(ctx: Context): Locale =
        if (Prefs.isEnglish(ctx)) Locale.ENGLISH else Locale("in", "ID")

    /** Returns a context whose resources use the language chosen in Settings. */
    fun wrap(context: Context): Context {
        val locale = localeFor(context)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLocales(android.os.LocaleList(locale))
        return context.createConfigurationContext(config)
    }
}

abstract class LocalizedActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}
