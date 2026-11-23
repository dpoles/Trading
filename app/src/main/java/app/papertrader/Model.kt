package app.papertrader

import kotlinx.serialization.Serializable
import java.util.Locale

data class Candle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
)

data class Stock(val symbol: String, val name: String, val basePrice: Double, val vol: Double = 1.0)

@Serializable
data class PriceAlert(
    val id: Long,
    val symbol: String,
    val above: Boolean,
    val target: Double,
    val triggered: Boolean = false,
)

/** Un acquisto (riga dei tab Azioni/Monetari del foglio). */
@Serializable
data class Lot(
    val tab: String,
    val name: String,
    val isin: String,
    val date: String,
    val buyPrice: Double?,
    val qty: Double,
    val commission: Double?,
    val buyValue: Double,
    val price: Double?,
    val value: Double?,
    val diff: Double?,
    val ret: Double?,
    val net: Double?,
)

/** Riga di riepilogo (TOTALE INVESTITO, TOTALE CONTO...). */
@Serializable
data class Summary(
    val label: String,
    val qty: Double?,
    val buyValue: Double?,
    val value: Double?,
    val diff: Double?,
    val ret: Double?,
    val net: Double?,
)

@Serializable
data class MonthRow(
    val month: String, // yyyy-MM
    val netValue: Double?,
    val variation: Double?,
    val growth: Double?,
)

@Serializable
data class Deposit(
    val amount: Double,
    val cumulative: Double?,
    val date: String,
    val source: String,
    val note: String,
)

/** Fotografia dei dati letti dal foglio Balance. */
@Serializable
data class SheetSnapshot(
    val lots: List<Lot>,
    val summaries: List<Summary>,
    val stampDuty: Double?,
    val cash: Double,
    val months: List<MonthRow>,
    val deposits: List<Deposit>,
    val importedAt: Long,
)

@Serializable
data class PortfolioState(
    val snapshot: SheetSnapshot? = null,
    val alerts: List<PriceAlert> = emptyList(),
    val watchlist: List<String> = DEFAULT_WATCHLIST,
)

/** Titoli del simulatore di mercato (watchlist e alert). */
val UNIVERSE = listOf(
    Stock("VWCE", "Vanguard FTSE All-World (Acc)", 170.73),
    Stock("XEON", "Xtrackers EUR Overnight Rate", 150.84, vol = 0.02),
    Stock("AAPL", "Apple", 225.0),
    Stock("MSFT", "Microsoft", 420.0),
    Stock("NVDA", "NVIDIA", 135.0),
    Stock("TSLA", "Tesla", 250.0),
    Stock("AMZN", "Amazon", 185.0),
    Stock("GOOGL", "Alphabet", 170.0),
    Stock("SPY", "S&P 500 ETF", 570.0),
    Stock("QQQ", "Nasdaq 100 ETF", 480.0),
    Stock("VTI", "Vanguard Total Market ETF", 280.0),
)

val DEFAULT_WATCHLIST = listOf("VWCE", "XEON", "AAPL", "NVDA", "SPY")

fun money(v: Double): String = String.format(Locale.ITALY, "%,.2f €", v)
fun pct(v: Double): String = String.format(Locale.US, "%+.2f%%", v)
fun qtyText(v: Double): String =
    if (v == Math.floor(v)) v.toLong().toString() else String.format(Locale.ITALY, "%.4f", v)
