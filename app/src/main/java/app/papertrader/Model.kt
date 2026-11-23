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

data class Stock(val symbol: String, val name: String, val basePrice: Double)

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
    val cash: Double = START_CASH,
    val positions: List<Position> = emptyList(),
    val trades: List<TradeRecord> = emptyList(),
    val alerts: List<PriceAlert> = emptyList(),
    val watchlist: List<String> = DEFAULT_WATCHLIST,
)

const val START_CASH = 100_000.0

val UNIVERSE = listOf(
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

val DEFAULT_WATCHLIST = listOf("AAPL", "NVDA", "TSLA", "SPY", "QQQ")

fun money(v: Double): String = String.format(Locale.US, "$%,.2f", v)
fun pct(v: Double): String = String.format(Locale.US, "%+.2f%%", v)
