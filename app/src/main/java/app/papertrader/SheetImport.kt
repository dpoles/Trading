package app.papertrader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Foglio "Balance" su Google Drive (l'ID non è un segreto: serve comunque il login). */
const val SHEET_ID = "1w_O9NKcD1cZ6LzdovwwgNsJU7MyUN2OO7V0hUKC6w1U"
const val SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets.readonly"

private val TABS = listOf("Azioni", "Monetari")

/** ISIN presenti nel foglio -> simbolo usato nell'app. */
private val ISIN_TO_SYMBOL = mapOf(
    "IE00BK5BQT80" to "VWCE",
    "LU0290358497" to "XEON",
)

data class ImportPreview(
    val cash: Double,
    val positions: List<Position>,
    val skipped: List<String>,
)

typealias SheetTab = List<List<Any?>>

object SheetParser {
    private fun num(x: Any?): Double? = (x as? Number)?.toDouble()
    private fun text(x: Any?): String = (x as? String)?.trim().orEmpty()

    /** Pura (nessun I/O): input = valori delle celle per tab. */
    fun parse(tabs: Map<String, SheetTab>): ImportPreview {
        val qty = LinkedHashMap<String, Double>()
        val cost = LinkedHashMap<String, Double>()
        var cash: Double? = null

        for (name in TABS) {
            val rows = tabs[name] ?: error("Tab \"$name\" non trovato nel foglio")
            val h = rows.indexOfFirst { text(it.getOrNull(0)) == "Nome Investimento" }
            if (h < 0) error("Intestazione \"Nome Investimento\" non trovata in \"$name\"")
            val hdr = rows[h].map { text(it) }
            val iIsin = hdr.indexOf("ISIN")
            val iQty = hdr.indexOf("Qtà")
            val iVal = hdr.indexOf("Val Acq")
            if (minOf(iIsin, iQty, iVal) < 0) error("Colonne ISIN/Qtà/Val Acq non trovate in \"$name\"")

            for (r in rows.drop(h + 1)) {
                if (text(r.getOrNull(0)).uppercase().startsWith("TOTALE")) break
                val isin = text(r.getOrNull(iIsin))
                val q = num(r.getOrNull(iQty))
                val v = num(r.getOrNull(iVal))
                if (isin.isNotEmpty() && q != null && q > 0 && v != null) {
                    qty[isin] = (qty[isin] ?: 0.0) + q
                    cost[isin] = (cost[isin] ?: 0.0) + v
                }
            }
            if (name == "Azioni") {
                val row = rows.firstOrNull { text(it.getOrNull(0)).lowercase().startsWith("saldo disponibile") }
                cash = row?.drop(1)?.firstNotNullOfOrNull { num(it) }
            }
        }

        val skipped = ArrayList<String>()
        val positions = ArrayList<Position>()
        for ((isin, q) in qty) {
            val symbol = ISIN_TO_SYMBOL[isin]
            if (symbol == null) {
                skipped += "$isin (${q.toInt()} quote): ISIN sconosciuto all'app"
            } else {
                positions += Position(symbol, q.toInt(), cost.getValue(isin) / q)
            }
        }
        return ImportPreview(
            cash = cash ?: error("\"Saldo Disponibile\" non trovato in \"Azioni\""),
            positions = positions,
            skipped = skipped,
        )
    }
}

object SheetsClient {
    suspend fun fetch(accessToken: String): Map<String, SheetTab> = withContext(Dispatchers.IO) {
        val ranges = TABS.joinToString("&") {
            "ranges=" + URLEncoder.encode("$it!A1:AZ120", "UTF-8")
        }
        val url = URL(
            "https://sheets.googleapis.com/v4/spreadsheets/$SHEET_ID/values:batchGet" +
                "?$ranges&valueRenderOption=UNFORMATTED_VALUE"
        )
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Google Sheets ha risposto $code")
            val result = LinkedHashMap<String, SheetTab>()
            val arr = JSONObject(body).getJSONArray("valueRanges")
            for (i in 0 until arr.length()) {
                val vr = arr.getJSONObject(i)
                val name = vr.getString("range").substringBefore('!').trim('\'')
                val values = vr.optJSONArray("values")
                val rows = ArrayList<List<Any?>>()
                if (values != null) for (r in 0 until values.length()) {
                    val row = values.getJSONArray(r)
                    rows += List(row.length()) { c -> row.get(c).takeIf { it != JSONObject.NULL } }
                }
                result[name] = rows
            }
            result
        } finally {
            conn.disconnect()
        }
    }
}
