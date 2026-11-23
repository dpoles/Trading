package app.papertrader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/** Foglio "Balance" su Google Drive (l'ID non è un segreto: serve comunque il login). */
const val SHEET_ID = "1w_O9NKcD1cZ6LzdovwwgNsJU7MyUN2OO7V0hUKC6w1U"
const val SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets.readonly"

private val TABS = listOf("Azioni", "Monetari")

typealias SheetTab = List<List<Any?>>

object SheetParser {
    private fun num(x: Any?): Double? = (x as? Number)?.toDouble()
    private fun text(x: Any?): String = (x as? String)?.trim().orEmpty()

    /** Le date del foglio arrivano come numeri seriali (giorni dal 30/12/1899). */
    private fun serialDate(n: Double): LocalDate = LocalDate.of(1899, 12, 30).plusDays(n.toLong())

    private fun dateText(x: Any?): String = when (x) {
        is Number -> serialDate(x.toDouble()).let { "%02d/%02d/%04d".format(it.dayOfMonth, it.monthValue, it.year) }
        else -> text(x)
    }

    /** Pura (nessun I/O): input = valori delle celle per tab. */
    fun parse(tabs: Map<String, SheetTab>, now: Long = System.currentTimeMillis()): SheetSnapshot {
        val lots = ArrayList<Lot>()
        val summaries = ArrayList<Summary>()
        var cash: Double? = null
        var stamp: Double? = null

        for (name in TABS) {
            val rows = tabs[name] ?: error("Tab \"$name\" non trovato nel foglio")
            val h = rows.indexOfFirst { text(it.getOrNull(0)) == "Nome Investimento" }
            if (h < 0) error("Intestazione \"Nome Investimento\" non trovata in \"$name\"")
            val hdr = rows[h].map { text(it) }
            fun col(n: String) = hdr.indexOf(n)
            val iIsin = col("ISIN"); val iQty = col("Qtà"); val iVal = col("Val Acq")
            if (minOf(iIsin, iQty, iVal) < 0) error("Colonne ISIN/Qtà/Val Acq non trovate in \"$name\"")
            fun cell(r: List<Any?>, n: String): Double? = col(n).takeIf { it >= 0 }?.let { num(r.getOrNull(it)) }

            var inLots = true
            for (r in rows.drop(h + 1)) {
                val first = text(r.getOrNull(0))
                if (first.uppercase().startsWith("TOTALE")) {
                    inLots = false
                    summaries += Summary(
                        label = "$name – $first",
                        qty = cell(r, "Qtà"), buyValue = cell(r, "Val Acq"), value = cell(r, "Val Act"),
                        diff = cell(r, "Diff"), ret = cell(r, "% Int"), net = cell(r, "Net"),
                    )
                    continue
                }
                if (first.lowercase().startsWith("imposte bollo") && name == "Azioni") {
                    stamp = r.drop(1).firstNotNullOfOrNull { num(it) }
                }
                if (first.lowercase().startsWith("saldo disponibile") && name == "Azioni") {
                    cash = r.drop(1).firstNotNullOfOrNull { num(it) }
                }
                if (!inLots) continue
                val isin = text(r.getOrNull(iIsin))
                val q = num(r.getOrNull(iQty))
                val v = num(r.getOrNull(iVal))
                if (isin.isNotEmpty() && q != null && q > 0 && v != null) {
                    lots += Lot(
                        tab = name, name = first, isin = isin,
                        date = dateText(r.getOrNull(col("Data Inv").takeIf { it >= 0 } ?: Int.MAX_VALUE)),
                        buyPrice = cell(r, "Prz Acq"), qty = q, commission = cell(r, "Comm"), buyValue = v,
                        price = cell(r, "Prz Act"), value = cell(r, "Val Act"), diff = cell(r, "Diff"),
                        ret = cell(r, "% Int"), net = cell(r, "Net"),
                    )
                }
            }
        }

        val az = tabs.getValue("Azioni")
        return SheetSnapshot(
            lots = lots,
            summaries = summaries,
            stampDuty = stamp,
            cash = cash ?: error("\"Saldo Disponibile\" non trovato in \"Azioni\""),
            months = parseMonths(az),
            deposits = parseDeposits(az),
            importedAt = now,
        )
    }

    private fun parseMonths(rows: SheetTab): List<MonthRow> {
        val mi = rows.indexOfFirst { r -> r.any { text(it) == "Mese" } }
        if (mi < 0) return emptyList()
        val head = rows[mi]
        val c0 = head.indexOfFirst { text(it) == "Mese" }
        val dateCols = (c0 + 1 until head.size).filter { (num(head[it]) ?: 0.0) > 40_000 }
        fun rowOf(prefix: String) = rows.firstOrNull { text(it.getOrNull(0)).lowercase().startsWith(prefix) }
        val net = rowOf("totale conto") ?: return emptyList()
        val variation = rowOf("variazione su valore netto")
        val growth = rowOf("crescita mensile")
        return dateCols.mapNotNull { c ->
            val d = serialDate(num(head[c])!!)
            val nv = num(net.getOrNull(c)) ?: return@mapNotNull null
            MonthRow(
                month = "%04d-%02d".format(d.year, d.monthValue),
                netValue = nv,
                variation = variation?.let { num(it.getOrNull(c)) },
                growth = growth?.let { num(it.getOrNull(c)) },
            )
        }
    }

    private fun parseDeposits(rows: SheetTab): List<Deposit> {
        val vi = rows.indexOfFirst { text(it.getOrNull(0)).lowercase() == "versamenti" }
        if (vi < 0) return emptyList()
        val out = ArrayList<Deposit>()
        for (r in rows.drop(vi + 1)) {
            val amount = num(r.getOrNull(0)) ?: break
            out += Deposit(
                amount = amount,
                cumulative = num(r.getOrNull(1)),
                date = dateText(r.getOrNull(3)),
                source = text(r.getOrNull(4)),
                note = text(r.getOrNull(5)),
            )
        }
        return out
    }
}

object SheetsClient {
    /** Converte la risposta di values:batchGet in valori per tab. */
    fun parseBody(body: String): Map<String, SheetTab> {
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
        return result
    }

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
            parseBody(body)
        } finally {
            conn.disconnect()
        }
    }
}
