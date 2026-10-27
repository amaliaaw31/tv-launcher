package id.bestie.tvlauncher

import org.json.JSONObject

data class Quake(
    val magnitude: Double,
    val wilayah: String,
    val kedalaman: String,
    val jam: String,
    val tanggal: String,
    val dateTime: String,
    val potensi: String,
    val dirasakan: String,
    /** BMKG "Coordinates" ("lat,lon"), falling back to Lintang/Bujur text. Used by the detail page (v1.9). */
    val koordinat: String = ""
) {
    /** BMKG phrasing is e.g. "Berpotensi TSUNAMI" vs "tidak berpotensi TSUNAMI" — check the negation first. */
    val isTsunami: Boolean
        get() {
            val p = potensi.lowercase()
            return p.contains("berpotensi tsunami") && !p.contains("tidak berpotensi")
        }

    val isBig: Boolean get() = magnitude >= 6.5 || isTsunami

    companion object {
        const val EXTRA_MAG = "extra_mag"
        const val EXTRA_WILAYAH = "extra_wilayah"
        const val EXTRA_KEDALAMAN = "extra_kedalaman"
        const val EXTRA_JAM = "extra_jam"
        const val EXTRA_POTENSI = "extra_potensi"
    }
}

object QuakeParser {
    /** Parses BMKG autogempa.json (single object) and tolerates the gempaterkini array shape too. */
    fun parseLatest(jsonText: String): Quake? {
        return try {
            val info = JSONObject(jsonText).getJSONObject("Infogempa")
            val node = info.opt("gempa") ?: return null
            val obj: JSONObject = when (node) {
                is JSONObject -> node
                is org.json.JSONArray -> if (node.length() > 0) node.getJSONObject(0) else return null
                else -> return null
            }
            Quake(
                magnitude = obj.optString("Magnitude").toDoubleOrNull() ?: return null,
                wilayah = obj.optString("Wilayah"),
                kedalaman = obj.optString("Kedalaman"),
                jam = obj.optString("Jam"),
                tanggal = obj.optString("Tanggal"),
                dateTime = obj.optString("DateTime"),
                potensi = obj.optString("Potensi"),
                dirasakan = obj.optString("Dirasakan"),
                koordinat = obj.optString("Coordinates").ifBlank {
                    val lintang = obj.optString("Lintang")
                    val bujur = obj.optString("Bujur")
                    if (lintang.isBlank() && bujur.isBlank()) "" else "$lintang, $bujur"
                }
            )
        } catch (e: Exception) {
            null
        }
    }
}
