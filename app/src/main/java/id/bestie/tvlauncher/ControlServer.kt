package id.bestie.tvlauncher

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URLDecoder

/**
 * Minimal embedded HTTP server (ServerSocket, no libraries) that serves one
 * control page for a phone on the same Wi-Fi: change location / language /
 * popup threshold / sound, and fire the test earthquake popup on the TV.
 */
object ControlServer {
    const val PORT = 8765

    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null
    private var appContext: Context? = null

    fun isRunning(): Boolean = running

    fun start(context: Context): Boolean {
        if (running) return true
        appContext = context.applicationContext
        return try {
            serverSocket = ServerSocket(PORT)
            running = true
            Thread { acceptLoop() }.apply { isDaemon = true }.start()
            true
        } catch (e: Exception) {
            running = false
            false
        }
    }

    fun localIp(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
        }
        return null
    }

    fun url(): String? = localIp()?.let { "http://$it:$PORT/" }

    private fun acceptLoop() {
        while (running) {
            try {
                val socket = serverSocket?.accept() ?: break
                Thread { handle(socket) }.apply { isDaemon = true }.start()
            } catch (e: Exception) {
                if (running) continue else break
            }
        }
    }

    private fun handle(socket: java.net.Socket) {
        socket.use { s ->
            s.soTimeout = 10_000
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.lowercase().startsWith("content-length:")) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }
            var body = ""
            if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = reader.read(buf, read, contentLength - read)
                    if (n < 0) break
                    read += n
                }
                body = String(buf, 0, read)
            }
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0) ?: "GET"
            val path = parts.getOrNull(1) ?: "/"
            val out = s.getOutputStream()
            val ctx = appContext
            when {
                method == "GET" && (path == "/" || path.startsWith("/?")) -> {
                    respond(out, 200, controlPage(ctx))
                }
                method == "POST" && path == "/set" -> {
                    if (ctx != null) applySettings(ctx, parseForm(body))
                    respond(out, 200, savedPage())
                }
                method == "POST" && path == "/test" -> {
                    if (ctx != null) {
                        val wctx = LocaleHelper.wrap(ctx)
                        val test = Quake(
                            magnitude = 5.4,
                            wilayah = wctx.getString(R.string.test_quake_wilayah),
                            kedalaman = "10 km",
                            jam = "",
                            tanggal = "",
                            dateTime = "test",
                            potensi = "",
                            dirasakan = ""
                        )
                        if (OverlayPopup.canShow(ctx)) {
                            OverlayPopup.showQuake(ctx, test)
                        } else {
                            QuakeAlerter.launchAlertActivity(ctx, test)
                        }
                    }
                    respond(out, 200, savedPage())
                }
                else -> respond(out, 404, "<html><body>404</body></html>")
            }
        }
    }

    private fun parseForm(body: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (pair in body.split("&")) {
            if (pair.isBlank()) continue
            val kv = pair.split("=", limit = 2)
            val key = URLDecoder.decode(kv[0], "UTF-8")
            val value = if (kv.size > 1) URLDecoder.decode(kv[1], "UTF-8") else ""
            map[key] = value
        }
        return map
    }

    private fun applySettings(ctx: Context, params: Map<String, String>) {
        params["city"]?.toIntOrNull()?.let { Prefs.setCityIndex(ctx, it) }
        params["lang"]?.let { if (it == "en" || it == "in") Prefs.setLanguage(ctx, it) }
        params["threshold"]?.toDoubleOrNull()?.let { Prefs.setThreshold(ctx, it) }
        // Checkbox: present with value "on" when ticked, absent when unticked.
        Prefs.setSound(ctx, params["sound"] == "on")
    }

    private fun controlPage(ctx: Context?): String {
        val cityOptions = StringBuilder()
        val currentCity = if (ctx != null) Prefs.cityIndex(ctx) else 0
        Cities.list.forEachIndexed { i, city ->
            val sel = if (i == currentCity) " selected" else ""
            cityOptions.append("<option value=\"$i\"$sel>${city.name}</option>")
        }
        val currentLang = if (ctx != null) Prefs.language(ctx) else "in"
        val currentThreshold = if (ctx != null) Prefs.threshold(ctx) else 5.0
        val thresholdOptions = listOf(4.5, 5.0, 5.5, 6.0).joinToString("") { v ->
            val sel = if (v == currentThreshold) " selected" else ""
            "<option value=\"$v\"$sel>M ≥ $v</option>"
        }
        val soundChecked = if (ctx != null && Prefs.sound(ctx)) " checked" else ""
        return """
        <!DOCTYPE html><html lang="id"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Kontrol TV Launcher</title>
        <style>body{font-family:sans-serif;background:#10181F;color:#fff;margin:24px}
        label{display:block;margin-top:18px;font-size:15px;color:#B0BEC5}
        select{width:100%;padding:12px;font-size:17px;margin-top:6px;border-radius:8px}
        button{width:100%;padding:14px;font-size:17px;margin-top:26px;border:0;border-radius:10px;background:#FFD54F;color:#000;font-weight:bold}
        .test{background:#FF5252;color:#fff}</style></head><body>
        <h2>Kontrol TV Launcher</h2>
        <form method="POST" action="/set">
          <label>Lokasi<select name="city">$cityOptions</select></label>
          <label>Bahasa / Language<select name="lang">
            <option value="in"${if (currentLang == "in") " selected" else ""}>Indonesia</option>
            <option value="en"${if (currentLang == "en") " selected" else ""}>English</option>
          </select></label>
          <label>Ambang Pop-up Gempa<select name="threshold">$thresholdOptions</select></label>
          <label style="color:#fff"><input type="checkbox" name="sound" value="on"$soundChecked> Suara Peringatan</label>
          <button type="submit">Simpan</button>
        </form>
        <form method="POST" action="/test">
          <button type="submit" class="test">Uji Pop-up Gempa di TV</button>
        </form>
        <p style="color:#90A4AE;font-size:13px">Perubahan lokasi/bahasa terlihat di TV saat layar utama tampil lagi.</p>
        </body></html>
        """.trimIndent()
    }

    private fun savedPage(): String =
        "<!DOCTYPE html><html><head><meta charset=\"utf-8\"></head><body style=\"font-family:sans-serif;background:#10181F;color:#fff;margin:24px\"><h3>Tersimpan ✓</h3><p><a style=\"color:#FFD54F\" href=\"/\">Kembali ke kontrol</a></p></body></html>"

    private fun respond(out: OutputStream, code: Int, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val status = if (code == 200) "200 OK" else "404 Not Found"
        val header = "HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }
}
