package id.bestie.tvlauncher

import java.net.HttpURLConnection
import java.net.URL

/** Minimal HTTP GET helper (HttpURLConnection, no external libraries). Call from a background thread. */
object Net {
    fun get(url: String, timeoutMs: Int = 15000): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("User-Agent", "TvLauncher/1.0 (Android TV)")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
