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
data class Position(val symbol: String, val qty: Int, val avgPrice: Double)

@Serializable
data class TradeRecord(
    val symbol: String,
    val side: String,
    val qty: Int,
    val price: Double,
    val time: Long,
)

@Serializable
data class PriceAlert(
    val id: Long,
    val symbol: String,
    val above: Boolean,
    val target: Double,
    val triggered: Boolean = false,
)

@Serializable
data class PortfolioState(
    val cash: Double = SEED_CASH,
    val positions: List<Position> = SEED_POSITIONS,
    val trades: List<TradeRecord> = emptyList(),
    val alerts: List<PriceAlert> = emptyList(),
    val watchlist: List<String> = DEFAULT_WATCHLIST,
)

// Import una tantum dal foglio "Balance" (tab Azioni e Monetari), valori in EUR.
const val SEED_CASH = 12_235.79
val SEED_POSITIONS = listOf(
    Position("VWCE", 663, 154.92),   // costo totale 102.713,17 EUR
    Position("XEON", 89, 149.98),    // costo totale 13.348,18 EUR
)

/** Base per il P&L totale: costo delle posizioni + liquidità iniziale. */
val START_CASH: Double = SEED_CASH + SEED_POSITIONS.sumOf { it.qty * it.avgPrice }

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
